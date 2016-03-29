package company.electrobin.user;

public interface UserProfileLoadListener {
    public final static int ERROR_INVALID_AUTH_TOKEN = 1;
    public final static int ERROR_SYSTEM = 2;

    void onUserProfileLoadSuccess();
    void onUserProfileLoadError(int error);
}




