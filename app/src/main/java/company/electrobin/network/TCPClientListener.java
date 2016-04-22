package company.electrobin.network;

public interface TCPClientListener {

    public static final int CONNECT_RESULT_OK = 1;
    public static final int CONNECT_RESULT_ERROR = 2;
    public static final int AUTH_RESULT_ERROR = 3;

    void onConnectResult(int result);
    String onAuthToken();
    void onDataReceived(String data);
    void onConnectionClosed();
}
