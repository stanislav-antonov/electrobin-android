package company.electrobin.i10n;

public interface I10nInitializeListener {

    public final static int ERROR_SYSTEM = 1;
    public final static int ERROR_EMPTY_DATA = 2;
    public final static int ERROR_NETWORK = 3;

    void onInitializeSuccess();
    void onInitializeError(int error);
}

