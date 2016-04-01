package company.electrobin;

import android.app.Application;
import android.util.Log;

import com.crashlytics.android.Crashlytics;
import company.electrobin.i10n.I10n;
import company.electrobin.network.TCPClientService;
import company.electrobin.network.TLSSocketFactory;
import company.electrobin.user.User;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.HurlStack;
import com.android.volley.toolbox.Volley;

import io.fabric.sdk.android.Fabric;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;


public class ElectrobinApplication extends Application  {

    private User mUser;
    private RequestQueue mRequestQueue;
    private I10n mI10n;
    private TCPClientService mTCPClient;

    private static final String LOG_TAG = ElectrobinApplication.class.getName();

    /**
     *
     * @return
     */
    public synchronized ElectrobinApplication getInstance() {
        return ElectrobinApplication.this;
    }

    /**
     *
     * @return
     */
    public synchronized I10n getI10n() {
        if (mI10n == null)
            mI10n = new I10n(getApplicationContext());

        return mI10n;
    }

    /**
     *
     * @return
     */
    public synchronized User getUser() {
        if (mUser == null)
            mUser = new User(getApplicationContext());

        return mUser;
    }

    /**
     *
     * @return
     */
    public synchronized RequestQueue getRequestQueue() {
        if (mRequestQueue == null) {
            HurlStack hurlStack = new HurlStack() {
                @Override
                protected HttpURLConnection createConnection(URL url) throws IOException {
                    HttpsURLConnection httpsURLConnection = (HttpsURLConnection)super.createConnection(url);
                    try {
                        httpsURLConnection.setSSLSocketFactory(new TLSSocketFactory());
                        httpsURLConnection.setHostnameVerifier(new HostnameVerifier() {
                            @Override
                            public boolean verify(String hostname, SSLSession session) {
                                return true;
                            }
                        });
                    }
                    catch (Exception e) {
                        Log.e(LOG_TAG, e.getMessage());
                        e.printStackTrace();
                    }

                    return httpsURLConnection;
                }
            };

            mRequestQueue = Volley.newRequestQueue(getApplicationContext(), hurlStack);
        }

        return mRequestQueue;
    }

    /**
     *
     */
    @Override
    public void onCreate() {
        super.onCreate();
        Fabric.with(this, new Crashlytics());
    }
}
