package company.electrobin.i10n;

public interface I10nInitializeListener {

    public final static int ERROR_SYSTEM = 1;

    void onInitializeSuccess();
    void onInitializeError(int error);
}

