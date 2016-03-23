package company.electrobin.i10n;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonArrayRequest;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URI;
import java.util.Locale;

import company.electrobin.ElectrobinApplication;
import company.electrobin.common.Constants;

public class I10n {

    private Context mContext;
    private ElectrobinApplication mApp;
    private SharedPreferences mSharedPref;
    private Locale mCurrentLocale;

    public final static String SHARED_PREFERENCES_FILE_KEY = I10n.class.getName() + ".%1$s";
    private final static String LAST_LANG_KEY = "__last_lang";
    private final static String URL_I10n = Constants.REST_API_BASE_URL + "/language/?lang=%1$s";
    private final static String LOG_TAG = I10n.class.getSimpleName();

    /**
     *
     * @param context
     */
    public I10n(Context context) {
        mContext = context;
        mApp = (ElectrobinApplication)mContext;

        resetLocale();
    }

    /**
     *
     */
    public void resetLocale() {
        mCurrentLocale = mContext.getResources().getConfiguration().locale;

        // Make the file name according to the current lang of device
        mSharedPref = mContext.getSharedPreferences(String.format(SHARED_PREFERENCES_FILE_KEY,
                mCurrentLocale.getLanguage()), Context.MODE_PRIVATE);
    }

    /**
     *
     * @param listener
     */
    public void initialize(final I10nInitializeListener listener) {

        if (mSharedPref.getString(LAST_LANG_KEY, null) != null) {
            listener.onInitializeSuccess();
            return;
        }

        JsonArrayRequest request = new JsonArrayRequest(
            Request.Method.GET, String.format(URL_I10n, mCurrentLocale.getLanguage()),
            new Response.Listener<JSONArray>() {
                @Override
                public void onResponse(JSONArray response) {
                    if (response == null || response.length() == 0) {
                        Log.e(LOG_TAG, "I10n initialize error: empty data");
                        listener.onInitializeError(I10nInitializeListener.ERROR_EMPTY_DATA);
                        return;
                    }

                    try {
                        SharedPreferences.Editor editor = mSharedPref.edit();
                        editor.putString(LAST_LANG_KEY, mCurrentLocale.getLanguage());

                        for (int i = 0; i < response.length(); i++) {
                            String key, value;
                            try {
                                JSONObject jo = (JSONObject) response.get(i);

                                if (!jo.has("string_name") || !jo.has("string_text")) continue;

                                key = jo.getString("string_name");
                                if (key == null || key.isEmpty()) continue;

                                value = jo.getString("string_text");
                                if (value == null || value.isEmpty()) continue;
                            }
                            catch (JSONException e) {
                                Log.e(LOG_TAG, "I10n initialize error: " + e.getMessage());
                                continue;
                            }

                            editor.putString(key, value);
                        }

                        editor.commit();
                    }
                    catch (Exception e) {
                        Log.e(LOG_TAG, "I10n initialize error: " + e.getMessage());
                        listener.onInitializeError(I10nInitializeListener.ERROR_SYSTEM);
                        return;
                    }

                    listener.onInitializeSuccess();
                }
            },
            new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Log.e(LOG_TAG, "I10n initialize error: " + error.getMessage());
                    listener.onInitializeError(I10nInitializeListener.ERROR_NETWORK);
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