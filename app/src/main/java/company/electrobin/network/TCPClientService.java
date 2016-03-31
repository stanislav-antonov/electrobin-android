package company.electrobin.network;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;

import android.os.IBinder;
import android.os.Message;
import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;

import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.ref.WeakReference;
import java.util.ArrayList;

import javax.net.ssl.SSLSocket;

import company.electrobin.common.Constants;

interface AsyncConnectorListener {

    public static final int CONNECT_RESULT_OK = 1;
    public static final int CONNECT_RESULT_CREATE_SOCKET_ERROR = 2;
    public static final int CONNECT_RESULT_AUTH_ERROR = 3;

    public static final int CONNECTION_CLOSED_NORMALLY = 4;

    abstract void onConnectResult(int status);
    abstract void onConnectionClosed(int status);

}

interface AsyncWriterErrorListener {
    abstract void onWriteError(Exception e);
}

interface AsyncReaderErrorListener {
    abstract void onReadError(Exception e);
}

public class TCPClientService extends Service implements AsyncConnectorListener {

    private TCPClientListener mTCPClientListener;

    private volatile SSLSocket mSocket;
    private volatile boolean mIsConnected;

    private volatile BufferedReader mIn;
    private volatile PrintWriter mOut;

    private boolean mIsRunning;

    private Writer mWriter;
    private AsyncReader mAsyncReader;
    private AsyncConnector mAsyncConnector;
    private ConnectionChecker mConnectionChecker;

    private Handler mHandler = new Handler();
    private Handler mOnDataReceivedHandler;

    private final IBinder mBinder = new TCPClientServiceBinder();

    private final static String LOG_TAG = TCPClientService.class.getSimpleName();
    private final static String TCP_HOST = Constants.SOCKET_API_HOST;
    private final static int TCP_PORT = Constants.SOCKET_API_PORT;

    private final static String MESSAGE_BUNDLE_KEY_CONNECT_RESULT = "connect_result";
    private final static String MESSAGE_BUNDLE_KEY_RECEIVED_DATA = "received_data";

    private final static int MESSAGE_TYPE_CONNECT_RESULT = 1;
    private final static int MESSAGE_TYPE_DATA_RECEIVED = 2;

    private class AsyncConnector {

        private ArrayList<AsyncConnectorListener> mListenerList;

        private Thread mAsyncConnectorThread;

        private static final String HANDSHAKE_PROMPT = "HELLO!";
        private static final String HANDSHAKE_RESULT_OK = "200 AUTH_OK";

        private final String LOG_TAG = AsyncConnector.class.getName();

        /**
         *
         */
        public AsyncConnector() {
            mListenerList = new ArrayList<>();
        }

        /**
         *
         * @param listener
         */
        public void addListener(AsyncConnectorListener listener) {
            mListenerList.add(listener);
        }

        /**
         *
         * @param listener
         */
        public void removeListener(AsyncConnectorListener listener) {
            mListenerList.remove(listener);
        }

        /**
         *
         */
        public void connect() {
            if (mIsConnected) {
                Log.i(LOG_TAG, "TCPClient is already connected");
                return;
            }

            mAsyncConnectorThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Log.i(LOG_TAG, "Try to connect...");

                        TLSSocketFactory tlsFact = new TLSSocketFactory();
                        mSocket = (SSLSocket)tlsFact.createSocket(TCP_HOST, TCP_PORT);
                        mSocket.setKeepAlive(true);

                        mIn = new BufferedReader(new InputStreamReader(mSocket.getInputStream()));
                        mOut = new PrintWriter(new BufferedWriter(new OutputStreamWriter(mSocket
                                .getOutputStream())), true);
                    }
                    catch (Exception e) {
                        Log.e(LOG_TAG, "Failed to connect: " + e.getMessage());
                        for (AsyncConnectorListener listener : mListenerList) {
                            try {
                                listener.onConnectResult(AsyncConnectorListener.CONNECT_RESULT_CREATE_SOCKET_ERROR);
                            } catch (Exception ex) {
                                Log.e(LOG_TAG, ex.getMessage());
                            }
                        }

                        return;
                    }

                    try {
                        String data = mIn.readLine();
                        if (data == null || !data.equals(HANDSHAKE_PROMPT))
                            throw new IllegalStateException("Bad handshake prompt: " + data);

                        String token = String.format("Token:%1$s", mTCPClientListener.onAuthToken());
                        mOut.println(token);
                        mOut.flush();

                        data = mIn.readLine();
                        if (data == null || !data.equals(HANDSHAKE_RESULT_OK))
                            throw new IllegalStateException("Bad handshake result: " + data);
                    }
                    catch (Exception e) {
                        for (AsyncConnectorListener listener : mListenerList) {
                            try {
                                listener.onConnectResult(AsyncConnectorListener.CONNECT_RESULT_AUTH_ERROR);
                            } catch (Exception ex) {
                                Log.e(LOG_TAG, ex.getMessage());
                            }
                        }

                        return;
                    }

                    // This means that we have established logical connection:
                    // connected and authenticated by token
                    mIsConnected = true;
                    Log.i(LOG_TAG, "Connected OK");

                    for (AsyncConnectorListener listener : mListenerList) {
                        try {
                            listener.onConnectResult(AsyncConnectorListener.CONNECT_RESULT_OK);
                        } catch (Exception ex) {
                            Log.e(LOG_TAG, ex.getMessage());
                        }
                    }

                }
            });

            mAsyncConnectorThread.setName("TCPClient async connector");
            mAsyncConnectorThread.start();
        }

        /**
         *
         */
        public synchronized void disconnect() {
            // First of all
            mIsConnected = false;

            if (mSocket != null) {
                try {
                    mSocket.close();
                } catch (Exception e) {
                    // Ignore exception
                }

                mSocket = null;
            }

            for (AsyncConnectorListener listener : mListenerList) {
                try {
                    listener.onConnectionClosed(AsyncConnectorListener.CONNECTION_CLOSED_NORMALLY);
                } catch (Exception ex) {
                    Log.e(LOG_TAG, ex.getMessage());
                }
            }
        }
    }

    private class ConnectionChecker implements AsyncConnectorListener, AsyncReaderErrorListener, AsyncWriterErrorListener {

        private Handler mHandler = new Handler();
        private boolean mIsReconnecting;

        private final int RECONNECT_DELAY = 1000;

        /**
         *
         * @return
         */
        private boolean isNetworkAvailable() {
            ConnectivityManager connectivityManager
                    = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
            return activeNetworkInfo != null && activeNetworkInfo.isConnected();
        }

        /**
         *
         * @return
         */
        private boolean canReconnect() {
            return !mIsReconnecting && !mIsConnected;
        }

        /**
         *
         */
        public void reconnect() {
            if (!canReconnect()) return;

            mIsReconnecting = true;

            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mAsyncConnector.connect();
                    if (!canReconnect()) return;
                    mHandler.postDelayed(this, RECONNECT_DELAY);
                }
            }, RECONNECT_DELAY);
        }

        /**
         *
         * @param e
         */
        @Override
        public void onWriteError(Exception e) {
            shutdown();
            reconnect();
        }

        /**
         *
         * @param e
         */
        @Override
        public void onReadError(Exception e) {
            shutdown();
            reconnect();
        }

        /**
         *
         * @param status
         */
        @Override
        public void onConnectResult(int status) {
            // Let's see what's next?
            mIsReconnecting = false;
            if (status == AsyncConnectorListener.CONNECT_RESULT_AUTH_ERROR
                    || status == AsyncConnectorListener.CONNECT_RESULT_CREATE_SOCKET_ERROR) {

                shutdown();
                reconnect();
            }
        }

        /**
         *
         * @param status
         */
        @Override
        public void onConnectionClosed(int status) {
            // If the connection was normally closed, we do not need the reconnection tryings
            mIsReconnecting = false;
        }
    }

    private class Writer {

        private final AsyncWriterErrorListener mListener;
        private final String LOG_TAG = Writer.class.getName();

        /**
         *
         * @param listener
         */
        public Writer(AsyncWriterErrorListener listener) {
            mListener = listener;
        }

        /**
         *
         * @param data
         */
        public void sendData(String data) {
            if (!mIsConnected) throw new IllegalStateException("Not connected");

            try {
                mOut.println(data);
                mOut.flush();
            }
            catch (Exception e1) {
                try {
                    mListener.onWriteError(e1);
                } catch (Exception e2) {
                    Log.e(LOG_TAG, e2.toString());
                }
            }
        }
    }

    private class AsyncReader {

        private volatile boolean mIsRunning;
        private boolean mIsShutdown;

        private AsyncReaderErrorListener mListener;

        private Thread mAsyncReaderThread;

        private final String LOG_TAG = AsyncReader.class.getName();

        /**
         *
         * @param listener
         */
        public AsyncReader(AsyncReaderErrorListener listener) {
            mListener = listener;
        }

        /**
         *
         */
        public void start() {
            if (mAsyncReader.isRunning()) {
                Log.i(LOG_TAG, "AsyncReader already running");
                return;
            }

            if (!mIsConnected) {
                Log.e(LOG_TAG, "Can't start AsyncReader - no network connection");
                return;
            }

            mAsyncReaderThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    mIsRunning = true;
                    mIsShutdown = false;

                    while (mIsRunning) {
                        String data;
                        try {
                            // Assume the server always respond us the data trailing with the \n
                            data = mIn.readLine();
                            if (data == null)
                                // If we fall her with null, the connection was lost during the network problems
                                throw new Exception();
                        }
                        catch (Exception e1) {
                            // Not IO error - "correct" shutdownAll by the mIn.close();
                            if (!mIsShutdown) {
                                try {
                                    mListener.onReadError(e1);
                                } catch (Exception e2) {
                                    Log.e(LOG_TAG, e2.toString());
                                }
                            }

                            break;
                        }

                        try {
                            // And we need to answer to each message from server
                            mOut.println("OK");
                            mOut.flush();
                        } catch(Exception e) {
                            Log.e(LOG_TAG, e.getMessage());
                            continue;
                        }

                        try {
                            Message msg = Message.obtain(mOnDataReceivedHandler, MESSAGE_TYPE_DATA_RECEIVED);
                            Bundle bundle = new Bundle();
                            bundle.putString(MESSAGE_BUNDLE_KEY_RECEIVED_DATA, data);
                            msg.setData(bundle);

                            mOnDataReceivedHandler.sendMessage(msg);
                        }
                        catch (Exception e) {
                            Log.d(LOG_TAG, e.getMessage());
                        }
                    }

                    mIsRunning = false;
                }
            });

            mAsyncReaderThread.setName("TCPClient async reader");
            mAsyncReaderThread.start();
        }

        /**
         *
         */
        public void shutdown() {
            mIsRunning = false;
            mIsShutdown = true;

            mAsyncConnector.disconnect();
        }

        /**
         *
         * @return
         */
        public boolean isRunning() {
            return mIsRunning;
        }
    }

    private static class OnDataReceivedHandler extends Handler {
        private final WeakReference<TCPClientListener> mListener;

        OnDataReceivedHandler(TCPClientListener service) {
            mListener = new WeakReference<TCPClientListener>(service);
        }

        @Override
        public void handleMessage(Message msg)
        {
            TCPClientListener listener = mListener.get();
            if (listener == null) return;

            switch (msg.what) {
                case MESSAGE_TYPE_CONNECT_RESULT:
                    listener.onConnectResult(msg.getData().getInt(MESSAGE_BUNDLE_KEY_CONNECT_RESULT));
                    break;
                case MESSAGE_TYPE_DATA_RECEIVED:
                    listener.onDataReceived(msg.getData().getString(MESSAGE_BUNDLE_KEY_RECEIVED_DATA));
                    break;
            }
        }
    }

    public class TCPClientServiceBinder extends Binder {
        public TCPClientService getService() {
            return TCPClientService.this;
        }
    }

    /**
     *
     * @param data
     */
    public void sendData(final String data) {
        if (data == null || data.isEmpty()) return;

        try {
            mWriter.sendData(data);
        } catch(Exception e) {
            Log.e(LOG_TAG, e.getMessage());
        }
    }

    /**
     *
     */
    public void start(final TCPClientListener listener) {
        if (mIsRunning) {
            Log.i(LOG_TAG, "TCPClient is already running");
            return;
        }

        if (listener == null) throw new IllegalArgumentException();
        mTCPClientListener = listener;

        mConnectionChecker = new ConnectionChecker();
        mWriter = new Writer(mConnectionChecker);
        mAsyncReader = new AsyncReader(mConnectionChecker);

        mAsyncConnector = new AsyncConnector();
        mAsyncConnector.addListener(this);
        mAsyncConnector.addListener(mConnectionChecker);

        mOnDataReceivedHandler = new OnDataReceivedHandler(mTCPClientListener);

        mAsyncConnector.connect();

        mIsRunning = true;
    }

    /**
     *
     */
    public void stop() {
        shutdown();
        mIsRunning = false;
    }

    /**
     *
     * @return
     */
    public boolean isConnected() {
        return mIsConnected;
    }

    /**
     *
     */
    private void shutdown() {
        if (mAsyncReader != null) mAsyncReader.shutdown();
    }

    /**
     *
     * @param status
     */
    @Override
    public void onConnectResult(int status) {
        if (status == AsyncConnectorListener.CONNECT_RESULT_OK) {
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mAsyncReader.start();
                }
            }, 100);
        }

        try {
            Message msg = Message.obtain(mOnDataReceivedHandler, MESSAGE_TYPE_CONNECT_RESULT);
            Bundle bundle = new Bundle();
            bundle.putInt(MESSAGE_BUNDLE_KEY_CONNECT_RESULT, status);
            msg.setData(bundle);

            mOnDataReceivedHandler.sendMessage(msg);
        }
        catch (Exception e) {
            Log.d(LOG_TAG, e.getMessage());
        }
    }

    /**
     *
     * @param status
     */
    @Override
    public void onConnectionClosed(int status) {

    }

    /**
     *
     */
    @Override
    public void onCreate() {
        // Start up the thread running the service.  Note that we create a
        // separate thread because the service normally runs in the process's
        // main thread, which we don't want to block.  We also make it
        // background priority so CPU-intensive work will not disrupt our UI.

        // HandlerThread thread = new HandlerThread("ServiceStartArguments",
        //        Process.THREAD_PRIORITY_BACKGROUND);
        // thread.start();

        // Get the HandlerThread's Looper and use it for our Handler
        // mServiceLooper = thread.getLooper();
        // mServiceHandler = new ServiceHandler(mServiceLooper);
    }

    /**
     *
     * @param intent
     * @param flags
     * @param startId
     * @return
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // If we get killed, after returning from here, restart
        return START_STICKY;
    }

    /**
     *
     * @param intent
     * @return
     */
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    /**
     *
     */
    @Override
    public void onDestroy() {
        stop();
    }
}
