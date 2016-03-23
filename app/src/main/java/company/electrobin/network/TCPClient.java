package company.electrobin.network;

import android.content.Context;
import android.os.Handler;

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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import javax.net.ssl.SSLSocket;

import company.electrobin.common.Constants;


public class TCPClient {

    private TCPClientAuthHandler mListener;
    private Context mContext;
    private Thread mThread;

    private BlockingQueue<Pair<String, Runnable>> mQueue;

    private final static String LOG_TAG = TCPClient.class.getSimpleName();
    private final static String TCP_HOST = Constants.SOCKET_API_HOST;
    private final static int TCP_PORT = Constants.SOCKET_API_PORT;

    private class TCPClientRunnable implements Runnable {

        private boolean mIsRunning;

        private BufferedReader mIn;
        private PrintWriter mOut;

        private final BlockingQueue<Pair<String, Runnable>> mQueue;
        private final Handler mHandler;

        public TCPClientRunnable(BlockingQueue<Pair<String, Runnable>> queue, Handler handler) {
            mQueue = queue;
            mHandler = handler;
        }

        /**
         *
         * @param data
         * @throws IOException
         */
        private void send(String data) throws IOException {
            if (mOut == null || mOut.checkError())
                throw new IOException("Error sending message");

            mOut.println(data);
            mOut.flush(); // TODO: Maybe this explicit is not necessary during the PrintWriter usage with autoflush
        }

        /**
         *
         */
        @Override
        public void run() {
            try {
                TLSSocketFactory tlsFact = new TLSSocketFactory();
                SSLSocket socket = (SSLSocket)tlsFact.createSocket(TCP_HOST, TCP_PORT);

                mIn = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                mOut = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket
                        .getOutputStream())), true);

                String helloStr = mIn.readLine();
                if (!mQueue.isEmpty()) {
                    Pair<String, Runnable> pair = mQueue.poll();
                    TCPClientAuthHandler authHandler = (TCPClientAuthHandler)pair.second;
                    if (authHandler != null) {
                        mHandler.post(authHandler);

                        while (true) {
                            if (mQueue.isEmpty()) continue;
                            pair = mQueue.poll();
                            break;
                        }

                        send(pair.first);
                        String authResStr = mIn.readLine();

                        authHandler = (TCPClientAuthHandler)pair.second;
                        authHandler.setMessage(authResStr);

                        mHandler.post(authHandler);
                    }
                }

                // Try to run...
                mIsRunning = true;

                while (mIsRunning) {
                    // Handle the thread interruption
                    if (Thread.interrupted()) throw new InterruptedException();

                    TCPClientResponseHandler responseHandler = null;

                    if (!mQueue.isEmpty()) {
                        Pair<String, Runnable> pair = mQueue.poll();
                        responseHandler = (TCPClientResponseHandler)pair.second;
                        send(pair.first);
                    }

                    // Assume the server always respond us the data trailing with the \n
                    String data = mIn.readLine();

                    // And we need to answer to each message from server
                    if (data != null) {
                        send("OK");

                        Log.d(LOG_TAG, "Got data from server: " + data);

                        if (responseHandler != null) {
                            responseHandler.setMessage(data);
                            mHandler.post(responseHandler);
                        }
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
                try {
                    mIn.close();
                    mOut.flush();
                    mOut.close();
                }
                catch (Exception e) {
                    // Log.e(LOG_TAG, e.getMessage());
                }
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
     * @param handler
     */
    public void sendData(String data, Runnable handler) {
        if (data == null || data.isEmpty())
            throw new IllegalArgumentException();

        try {
            mQueue.put(Pair.create(data, handler));
        }
        catch (InterruptedException e) {
            // TODO: Need to handle InterruptedException correctly
            Log.e(LOG_TAG, e.getMessage());
        }
    }

    public void sendToken(String token, TCPClientAuthHandler handler) {
        sendData(String.format("Token:%1$s", token), handler);
    }

    /**
     *
     * @param handler
     */
    public void start(TCPClientAuthHandler handler) {
        mQueue = new LinkedBlockingQueue<Pair<String, Runnable>>();
        mThread = new Thread(new TCPClientRunnable(mQueue, new Handler(mContext.getMainLooper())));
        sendData("HELLO!", handler);
        mThread.start();
    }

    /**
     *
     */
    public void stop() {
        if (mThread != null && !mThread.isInterrupted())
            mThread.interrupt();
    }
}
