package company.electrobin.user;

public interface UserAuthListener {

    public final static int ERROR_BAD_AUTH_PARAMS = 1;
    public final static int ERROR_SYSTEM = 2;

    void onAuthSuccess();
    void onAuthError(int error);
}
