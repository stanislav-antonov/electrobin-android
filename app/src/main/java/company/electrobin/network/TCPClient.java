package company.electrobin.network;

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

public class TCPClient implements AsyncConnectorListener {

    private TCPClientListener mTCPClientListener;

    private volatile SSLSocket mSocket;
    private volatile boolean mIsConnected;

    private AsyncWriter mAsyncWriter;
    private Thread mAsyncWriterThread;

    private AsyncReader mAsyncReader;
    private Thread mAsyncReaderThread;

    private AsyncConnector mAsyncConnector;

    private final static String LOG_TAG = TCPClient.class.getSimpleName();
    private final static String TCP_HOST = Constants.SOCKET_API_HOST;
    private final static int TCP_PORT = Constants.SOCKET_API_PORT;


    private class AsyncConnector implements Runnable {

        private final AsyncConnectorListener mAsyncConnectorListener;

        private BufferedReader mIn;
        private PrintWriter mOut;

        private static final String HANDSHAKE_PROMPT = "HELLO!";
        private static final String HANDSHAKE_RESULT_OK = "200 AUTH_OK";

        private final String LOG_TAG = AsyncConnector.class.getName();

        /**
         *
         * @param listener
         */
        public AsyncConnector(AsyncConnectorListener listener) {
            if (listener == null) throw new IllegalArgumentException();
            mAsyncConnectorListener = listener;
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
                try {
                    mAsyncConnectorListener.onConnectResult(AsyncConnectorListener.CONNECT_RESULT_CREATE_SOCKET_ERROR);
                }
                catch (Exception ex) {
                    Log.e(LOG_TAG, ex.getMessage());
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
                try {
                    mAsyncConnectorListener.onConnectResult(AsyncConnectorListener.CONNECT_RESULT_AUTH_ERROR);
                }
                catch (Exception ex) {
                    Log.e(LOG_TAG, ex.getMessage());
                }

                return;
            }

            // This means that we have established logical connection:
            // connected and authenticated by token
            mIsConnected = true;

            try {
                mAsyncConnectorListener.onConnectResult(AsyncConnectorListener.CONNECT_RESULT_OK);
            }
            catch (Exception ex) {
                Log.e(LOG_TAG, ex.getMessage());
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

            try {
                mAsyncConnectorListener.onConnectionClosed(AsyncConnectorListener.CONNECTION_CLOSED_NORMALLY);
            }
            catch (Exception ex) {
                Log.e(LOG_TAG, ex.getMessage());
            }
        }
    }


    private class AsyncWriter implements Runnable {

        private Handler mHandler;
        private PrintWriter mOut;
        private volatile boolean mIsRunning;

        private final String LOG_TAG = AsyncWriter.class.getName();
        private static final String MESSAGE_KEY = "key";

        @Override
        public void run() {
            if (mIsRunning) throw new IllegalStateException("AsyncWriter already running");
            if (!mIsConnected) throw new IllegalStateException("Not connected");

            try {
                mOut = new PrintWriter(new BufferedWriter(new OutputStreamWriter(mSocket
                        .getOutputStream())), true);

                Looper.prepare();

                // Construct fore the current thread
                mHandler = new Handler() {
                    @Override
                    public void handleMessage(Message msg) {
                        mOut.println(msg.getData().getString(MESSAGE_KEY));
                        mOut.flush();
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

            if (mOut != null)
                mOut.close();

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

        private PrintWriter mOut;
        private BufferedReader mIn;
        private volatile boolean mIsRunning;

        private final String LOG_TAG = AsyncReader.class.getName();

        /**
         *
         */
        @Override
        public void run() {
            if (mIsRunning) throw new IllegalStateException("AsyncReader already running");
            if (!mIsConnected) throw new IllegalStateException("Not connected");

            try {
                mIn = new BufferedReader(new InputStreamReader(mSocket.getInputStream()));
                mOut = new PrintWriter(new BufferedWriter(new OutputStreamWriter(mSocket
                        .getOutputStream())), true);

                mIsRunning = true;

                while (mIsRunning) {
                    // Assume the server always respond us the data trailing with the \n
                    String data = mIn.readLine();

                    // And we need to answer to each message from server
                    mOut.println("OK");
                    mOut.flush();

                    if (data != null) {
                        Log.d(LOG_TAG, "Got data from server: " + data);
                    }
                }
            }
            catch (Exception e) {
                Log.e(LOG_TAG, "Error: " + e.getMessage());
            }
            finally {
                // No more running
                mIsRunning = false;
            }
        }

        /**
         *
         */
        public void shutdown() {
            mIsRunning = false;
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

        mAsyncConnector = new AsyncConnector(this);

        Thread connectorThread = new Thread(mAsyncConnector);
        connectorThread.setName("TCPClient async connector");
        connectorThread.start();
    }

    /**
     *
     */
    public void start(final TCPClientListener listener) {
        if (listener == null) throw new IllegalArgumentException();
        mTCPClientListener = listener;

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
            mAsyncWriter = new AsyncWriter();
            mAsyncWriterThread = new Thread(mAsyncWriter);
            mAsyncWriterThread.setName("TCPClient async writer");
        }
        else if (mAsyncWriterThread.isAlive()) {
            throw new IllegalStateException("AsyncWriter thread is alive!");
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
            mAsyncReader = new AsyncReader();
            mAsyncReaderThread = new Thread(mAsyncReader);
            mAsyncReaderThread.setName("TCPClient async reader");
        }
        else if (mAsyncReaderThread.isAlive()) {
            throw new IllegalStateException("AsyncReader thread is alive!");
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
