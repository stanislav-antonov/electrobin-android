package company.electrobin.i10n;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonArrayRequest;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;

import company.electrobin.ElectrobinApplication;
import company.electrobin.user.UserAuthListener;

public class I10n {

    private Context mContext;
    private ElectrobinApplication mApp;
    private SharedPreferences mSharedPref;
    private Locale mCurrentLocale;

    public final static String SHARED_PREFERENCES_FILE_KEY = I10n.class.getName();
    private final static String URL_I10n = "https://138.201.20.149/v1.02/language/?lang=%1$s";
    private final static String LOG_TAG = I10n.class.getSimpleName();

    /**
     *
     * @param context
     */
    public I10n(Context context) {
        mContext = context;
        mApp = (ElectrobinApplication)mContext;
        mSharedPref = mContext.getSharedPreferences(SHARED_PREFERENCES_FILE_KEY, Context.MODE_PRIVATE);
        mCurrentLocale = mContext.getResources().getConfiguration().locale;
    }

    /**
     *
     * @param listener
     */
    public void initialize(final I10nInitializeListener listener) {
        JsonArrayRequest request = new JsonArrayRequest(
            Request.Method.GET,
            String.format(URL_I10n, mCurrentLocale.getLanguage()),
            new Response.Listener<JSONArray>() {
                @Override
                public void onResponse(JSONArray response) {
                    if (response == null || response.length() == 0) {
                        listener.onInitializeError(UserAuthListener.ERROR_SYSTEM);
                        return;
                    }

                    try {
                        SharedPreferences.Editor editor = mSharedPref.edit();
                        editor.clear();

                        for (int i = 0; i < response.length(); i++) {
                            JSONObject jo = (JSONObject)response.get(i);

                            if (!jo.has("string_name") || !jo.has("string_text")) continue;

                            final String key = jo.getString("string_name");
                            if (key == null || key.isEmpty()) continue;

                            final String value = jo.getString("string_text");
                            if (value == null || value.isEmpty()) continue;

                            editor.putString(key, value);
                        }

                        editor.commit();
                    }
                    catch (Exception e) {
                        Log.e(LOG_TAG, "I10n initialize error: " + e.getMessage());
                        listener.onInitializeError(UserAuthListener.ERROR_SYSTEM);
                        return;
                    }

                    listener.onInitializeSuccess();
                }
            },
            new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Log.e(LOG_TAG, "Login error: " + error.getMessage());
                    listener.onInitializeError(UserAuthListener.ERROR_SYSTEM);
                }
            }
        );

        mApp.getRequestQueue().add(request);
    }

    /**
     *
     * @param name
     * @return
     */
    public String l(String name) {
        if (name == null || name.isEmpty()) return "";

        String localized = mSharedPref.getString(name, null);
        if (localized == null)
            localized = getStringResourceByName(name);

        return localized;
    }

    /**
     *
     * @param name
     * @return
     */
    private String getStringResourceByName(String name) {
        // Really must be a root package!
        final String rootPackage = ElectrobinApplication.class.getPackage().getName();
        return mContext.getString(mContext.getResources().getIdentifier(name, "string", rootPackage));
    }
}

// curl -k -X GET -d "lang=ru" https://138.201.20.149/v1.02/language/ -H 'Authorization: fcae3786eb0af330c12367ad485c95eac8267d92'