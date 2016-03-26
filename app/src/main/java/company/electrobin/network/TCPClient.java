package company.electrobin.network;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;

import android.os.Looper;
import android.os.Message;
import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;

import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
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

public class TCPClient implements AsyncConnectorListener {

    private TCPClientListener mTCPClientListener;

    private Context mContext;

    private volatile SSLSocket mSocket;
    private volatile boolean mIsConnected;

    private AsyncWriter mAsyncWriter;
    private Thread mAsyncWriterThread;

    private AsyncReader mAsyncReader;
    private Thread mAsyncReaderThread;

    private AsyncConnector mAsyncConnector;
    private Thread mAsyncConnectorThread;

    private AsyncConnectionWatcher mAsyncConnectionWatcher;

    private BufferedReader mIn;
    private PrintWriter mOut;

    private final static String LOG_TAG = TCPClient.class.getSimpleName();
    private final static String TCP_HOST = Constants.SOCKET_API_HOST;
    private final static int TCP_PORT = Constants.SOCKET_API_PORT;

    private class AsyncConnector implements Runnable {

        private ArrayList<AsyncConnectorListener> mListenerList;

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
        @Override
        public void run() {
            if (mIsConnected) throw new IllegalStateException("AsyncConnector already connected");

            try {
                TLSSocketFactory tlsFact = new TLSSocketFactory();

                mSocket = (SSLSocket)tlsFact.createSocket(TCP_HOST, TCP_PORT);
                mSocket.setKeepAlive(true);

                mIn = new BufferedReader(new InputStreamReader(mSocket.getInputStream()));
                mOut = new PrintWriter(new BufferedWriter(new OutputStreamWriter(mSocket
                        .getOutputStream())), true);

            }
            catch(Exception e) {
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
                if (data == null || !data.equals(HANDSHAKE_PROMPT)) {
                    throw new IllegalStateException("Bad handshake prompt: " + data);
                }

                String token = String.format("Token:%1$s", mTCPClientListener.onAuthToken());
                mOut.println(token);
                mOut.flush();

                data = mIn.readLine();
                if (data == null || !data.equals(HANDSHAKE_RESULT_OK)) {
                    throw new IllegalStateException("Bad handshake result: " + data);
                }
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

            for (AsyncConnectorListener listener : mListenerList) {
                try {
                    listener.onConnectResult(AsyncConnectorListener.CONNECT_RESULT_OK);
                } catch (Exception ex) {
                    Log.e(LOG_TAG, ex.getMessage());
                }
            }
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
                }
                catch(Exception e) {
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

    private class AsyncConnectionWatcher implements AsyncConnectorListener, AsyncReaderErrorListener, AsyncWriterErrorListener {

        private Handler mHandler = new Handler();
        private boolean mIsReconnecting;

        private final int RECONNECT_DELAY = 5000;

        /**
         *
         * @return
         */
        private boolean isNetworkAvailable() {
            ConnectivityManager connectivityManager
                    = (ConnectivityManager)mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
            return activeNetworkInfo != null && activeNetworkInfo.isConnected();
        }

        /**
         *
         * @return
         */
        private boolean canReconnect() {
            return !mIsReconnecting && !mIsConnected && isNetworkAvailable();
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
                    if (!canReconnect())
                        return;
                    connect();
                    mHandler.postDelayed(this, RECONNECT_DELAY);
                }
            }, RECONNECT_DELAY);
        }

        @Override
        public void onWriteError(Exception e) {
            mIsConnected = false;
            reconnect();
        }

        @Override
        public void onReadError(Exception e) {
            mIsConnected = false;
            reconnect();
        }

        @Override
        public void onConnectResult(int status) {
            // Let's see what's next?
            mIsReconnecting = false;
            if (status == AsyncConnectorListener.CONNECT_RESULT_AUTH_ERROR
                    || status == AsyncConnectorListener.CONNECT_RESULT_CREATE_SOCKET_ERROR) {

                reconnect();
            }
        }

        @Override
        public void onConnectionClosed(int status) {
            // If the connection was normally closed, we do not need the reconnection tryings
            mIsReconnecting = false;
        }
    }

    private class AsyncWriter implements Runnable {

        private Handler mHandler;
        private volatile boolean mIsRunning;

        private final AsyncWriterErrorListener mListener;

        private final String LOG_TAG = AsyncWriter.class.getName();
        private static final String MESSAGE_KEY = "key";

        /**
         *
         * @param listener
         */
        public AsyncWriter(AsyncWriterErrorListener listener) {
            mListener = listener;
        }

        /**
         *
         */
        @Override
        public void run() {
            if (mIsRunning) throw new IllegalStateException("AsyncWriter already running");
            if (!mIsConnected) throw new IllegalStateException("Not connected");

            try {
                Looper.prepare();

                // Construct fore the current thread
                mHandler = new Handler() {
                    @Override
                    public void handleMessage(Message msg) {
                        try {
                            mOut.println(msg.getData().getString(MESSAGE_KEY));
                            mOut.flush();
                        }
                        catch (Exception e1) {
                            shutdown();
                            try {
                                mListener.onWriteError(e1);
                            } catch (Exception e2) {
                                Log.e(LOG_TAG, e2.toString());
                            }
                        }
                    }
                };

                mIsRunning = true;

                // Run the message queue. The loop() call blocks!
                Looper.loop();
            }
            catch (Exception e) {
                Log.e(LOG_TAG, "AsyncWriter error: " + e.getMessage());
            }

            // We can come here after normal the looper quit or during some error
            mIsRunning = false;
        }

        /**
         *
         * @param data
         */
        public void sendData(String data) {
            if (!mIsConnected) throw new IllegalStateException("Not connected");

            Message msg = new Message();
            Bundle bundle = new Bundle();
            bundle.putString(MESSAGE_KEY, data);
            msg.setData(bundle);

            mHandler.sendMessage(msg);
        }

        /**
         *
         */
        public void shutdown() {
            Looper.myLooper().quit();

            if (mOut != null) mOut.close();

            mHandler = null;
            mIsRunning = false; // Paranoid feelings
        }

        /**
         *
         * @return
         */
        public boolean isRunning() {
            return mIsRunning;
        }
    }

    private class AsyncReader implements Runnable {

        private volatile boolean mIsRunning;
        private boolean mIsShutdown;

        private AsyncReaderErrorListener mListener;

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
        @Override
        public void run() {
            if (mIsRunning) throw new IllegalStateException("AsyncReader already running");
            if (!mIsConnected) throw new IllegalStateException("Not connected");

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
                    // Not IO error - "correct" shutdown by the mIn.close();
                    if (!mIsShutdown) {
                        shutdown();
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

                Log.d(LOG_TAG, "Got data from server: " + data);
            }

            mIsRunning = false;
        }

        /**
         *
         */
        public void shutdown() {
            mIsRunning = false;
            mIsShutdown = true;

            if (mIn != null) {
                try {
                    mIn.close();
                }
                catch (Throwable e) {
                    // Unhandled exception
                }

                mIn = null;
            }
        }

        /**
         *
         * @return
         */
        public boolean isRunning() {
            return mIsRunning;
        }
    }

    /**
     *
     * @param context
     */
    public TCPClient(Context context) {
        mContext = context;
    }

    /**
     *
     * @param data
     */
    public void sendData(final String data) {
        if (data == null || data.isEmpty())
            throw new IllegalArgumentException();

        try {
            mAsyncWriter.sendData(data);
        }
        catch(Exception e) {
            Log.e(LOG_TAG, e.getMessage());
        }
    }

    /**
     *
     */
    private void connect() {
        if (mAsyncConnector != null && mIsConnected) {
            Log.e(LOG_TAG, "Already connected");
            return;
        }

        if (mAsyncConnectorThread == null) {
            mAsyncConnectorThread = new Thread(mAsyncConnector);
            mAsyncConnectorThread.setName("TCPClient async connector");
        }
        else if (mAsyncConnectorThread.isAlive()) {
            Log.i(LOG_TAG, "AsyncConnector thread is alive!");
            return;
        }

        mAsyncConnectorThread.start();
    }

    /**
     *
     */
    public void start(final TCPClientListener listener) {
        if (listener == null) throw new IllegalArgumentException();
        mTCPClientListener = listener;

        mAsyncConnector = new AsyncConnector();
        mAsyncConnectionWatcher = new AsyncConnectionWatcher();

        mAsyncConnector.addListener(this);
        mAsyncConnector.addListener(mAsyncConnectionWatcher);

        connect();
    }

    /**
     *
     */
    public void shutdown() {
        if (mAsyncWriter != null) mAsyncWriter.shutdown();
        if (mAsyncReader != null) mAsyncReader.shutdown();
        if (mAsyncConnector != null) mAsyncConnector.disconnect();
    }

    /**
     *
     */
    private void startAsyncWriter() {
        if (mAsyncWriter != null && mAsyncWriter.isRunning()) {
            Log.i(LOG_TAG, "AsyncWriter already running");
            return;
        }

        if (mAsyncWriterThread == null) {
            mAsyncWriter = new AsyncWriter(mAsyncConnectionWatcher);
            mAsyncWriterThread = new Thread(mAsyncWriter);
            mAsyncWriterThread.setName("TCPClient async writer");
        }
        else if (mAsyncWriterThread.isAlive()) {
            Log.i(LOG_TAG, "AsyncWriter thread is alive!");
            return;
        }

        mAsyncWriterThread.start();
    }

    /**
     *
     */
    private void startAsyncReader() {
        if (mAsyncReader != null && mAsyncReader.isRunning()) {
            Log.i(LOG_TAG, "AsyncReader already running");
            return;
        }

        if (mAsyncReaderThread == null) {
            mAsyncReader = new AsyncReader(mAsyncConnectionWatcher);
            mAsyncReaderThread = new Thread(mAsyncReader);
            mAsyncReaderThread.setName("TCPClient async reader");
        }
        else if (mAsyncReaderThread.isAlive()) {
            Log.i(LOG_TAG, "AsyncReader thread is alive!");
            return;
        }

        mAsyncReaderThread.start();
    }

    @Override
    public void onConnectResult(int status) {
        if (status == AsyncConnectorListener.CONNECT_RESULT_OK) {
            mTCPClientListener.onConnectResult(TCPClientListener.CONNECT_RESULT_OK);

            startAsyncWriter();
            startAsyncReader();
        }
        else {
            mTCPClientListener.onConnectResult(TCPClientListener.CONNECT_RESULT_ERROR);
        }
    }

    @Override
    public void onConnectionClosed(int status) {

    }

}
