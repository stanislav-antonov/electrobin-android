package company.electrobin.user;

public interface UserLoadProfileListener {
    public final static int ERROR_INVALID_AUTH_TOKEN = 1;
    public final static int ERROR_SYSTEM = 2;

    void onGetProfileSuccess();
    void onGetProfileError(int error);
}




