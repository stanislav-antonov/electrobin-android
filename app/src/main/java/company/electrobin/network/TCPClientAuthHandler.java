package company.electrobin.network;

public class TCPClientAuthHandler implements Runnable {
    private String mMessage;
    public void setMessage(String message) {
        mMessage = message;
    }

    public String getMessage() {
        return mMessage;
    }

    @Override
    public void run() {
        // Do nothing
    }
}
