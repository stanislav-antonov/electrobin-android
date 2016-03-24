package company.electrobin.network;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;

import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.util.Pair;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.CharArrayWriter;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import javax.net.SocketFactory;
import javax.net.ssl.SSLSocket;

import company.electrobin.common.Constants;


public class TCPClient {

    private TCPClientListener mTCPClientListener;
    private Context mContext;

    private AsyncWriter mAsyncWriter;
    private Thread mAsyncWriterThread;

    private AsyncReader mAsyncReader;
    private Thread mAsyncReaderThread;

    private BufferedReader mIn;
    private PrintWriter mOut;

    private final static String LOG_TAG = TCPClient.class.getSimpleName();
    private final static String TCP_HOST = Constants.SOCKET_API_HOST;
    private final static int TCP_PORT = Constants.SOCKET_API_PORT;

    private interface AsyncConnectorListener {

        public static final int CONNECT_RESULT_OK = 1;
        public static final int CONNECT_RESULT_ERROR = 2;
        public static final int AUTH_RESULT_ERROR = 3;

        void onConnectResult(int result);
    }

    private class AsyncConnector implements Runnable {

        private final AsyncConnectorListener mAsyncConnectorListener;

        private static final String HANDSHAKE_PROMPT = "HELLO!";
        private static final String HANDSHAKE_RESULT_OK = "200 AUTH_OK";

        public AsyncConnector(AsyncConnectorListener listener) {
            if (listener == null) throw new IllegalArgumentException();
            mAsyncConnectorListener = listener;
        }

        @Override
        public void run() {
            try {
                TLSSocketFactory tlsFact = new TLSSocketFactory();
                SSLSocket socket = (SSLSocket) tlsFact.createSocket(TCP_HOST, TCP_PORT);

                mIn = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                mOut = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket
                        .getOutputStream())), true);
            }
            catch(Exception e) {
                try {
                    mAsyncConnectorListener.onConnectResult(AsyncConnectorListener.CONNECT_RESULT_ERROR);
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
                    mAsyncConnectorListener.onConnectResult(AsyncConnectorListener.AUTH_RESULT_ERROR);
                }
                catch (Exception ex) {
                    Log.e(LOG_TAG, ex.getMessage());
                }

                return;
            }

            try {
                mAsyncConnectorListener.onConnectResult(AsyncConnectorListener.CONNECT_RESULT_OK);
            }
            catch (Exception ex) {
                Log.e(LOG_TAG, ex.getMessage());
            }
        }
    }


    private class AsyncWriter implements Runnable {

        private Handler mHandler;
        private volatile boolean mIsRunning;

        private static final String MESSAGE_KEY = "key";

        @Override
        public void run() {
            if (mIsRunning ) {
                Log.i(LOG_TAG, "AsyncWriter already running");
                return;
            }

            Looper.prepare();

            // Construct fore the current thread
            mHandler = new Handler() {
                @Override
                public void handleMessage(Message msg) {
                    mOut.println(msg.getData().getString(MESSAGE_KEY));
                    mOut.flush();
                }
            };

            // Run the message queue
            Looper.loop();
            mIsRunning = true;
        }

        public void sendData(String data) {
            Message msg = new Message();

            Bundle bundle = new Bundle();
            bundle.putString(MESSAGE_KEY, data);

            msg.setData(bundle);

            mHandler.sendMessage(msg);
        }

        public void shutdown() {
            mIsRunning = false;
            Looper.myLooper().quit();
            Thread.currentThread().interrupt();
            // if (Thread.currentThread().isInterrupted()) {}
        }
    }


    private class AsyncReader implements Runnable {

        private volatile boolean mIsRunning;

        /**
         *
         */
        @Override
        public void run() {
            mIsRunning = true;

            try {
                while (mIsRunning) {
                    // Handle the thread interruption
                    if (Thread.interrupted()) throw new InterruptedException();

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
            catch (InterruptedException e) {
                Log.e(LOG_TAG, "Interrupted: " + e.getMessage());
                e.printStackTrace();
            }
            catch (Exception e) {
                Log.e(LOG_TAG, "Error: " + e.getMessage());
                e.printStackTrace();
            }
            finally {
                // No more running
                mIsRunning = false;
            }
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
    public void start(final TCPClientListener listener) {

        if (listener == null) throw new IllegalArgumentException();
        mTCPClientListener = listener;

        AsyncConnector asyncConnector = new AsyncConnector(new AsyncConnectorListener() {
            @Override
            public void onConnectResult(int result) {
                if (result == AsyncConnectorListener.CONNECT_RESULT_OK) {

                    mTCPClientListener.onConnectResult(TCPClientListener.CONNECT_RESULT_OK);

                    mAsyncWriter = new AsyncWriter();
                    mAsyncWriterThread = new Thread(mAsyncWriter);
                    mAsyncWriterThread.setName("TCP Client async writer");
                    mAsyncWriterThread.start();

                    mAsyncReader = new AsyncReader();
                    mAsyncReaderThread = new Thread(mAsyncReader);
                    mAsyncReaderThread.setName("TCP Client async reader");
                    mAsyncReaderThread.start();
                }
                else {
                    mTCPClientListener.onConnectResult(TCPClientListener.CONNECT_RESULT_ERROR);
                }
            }
        });

        new Thread(asyncConnector).start();
    }

    /**
     *
     */
    public void shutdown() {
        if (mAsyncWriterThread != null && !mAsyncWriterThread.isInterrupted())
            mAsyncWriterThread.interrupt();
    }
}
