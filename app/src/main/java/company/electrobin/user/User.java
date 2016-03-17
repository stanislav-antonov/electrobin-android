package company.electrobin.user;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;

import org.json.JSONObject;

import company.electrobin.ElectrobinApplication;
import company.electrobin.common.Installation;

public class User {

    private Context mContext;
    private ElectrobinApplication mApp;
    private SharedPreferences mSharedPref;

    private final static String URL_LOGIN = "https://138.201.20.149/v1.02/auth-token/";
    private final static String LOG_TAG = User.class.getSimpleName();
    private final static String AUTH_TOKEN_KEY = "auth_token";
    public final static String SHARED_PREFERENCES_FILE_KEY = User.class.getName();

    /**
     *
     * @param context
     */
    public User(Context context) {
        mContext = context;
        mApp = (ElectrobinApplication)mContext;
        mSharedPref = mContext.getSharedPreferences(SHARED_PREFERENCES_FILE_KEY, Context.MODE_PRIVATE);
    }

    /**
     *
     * @return
     */
    public String getAuthToken() {
        return mSharedPref.getString(AUTH_TOKEN_KEY, null);
    }

    /**
     *
     * @return
     */
    public boolean isLoggedIn() {
        return mSharedPref.getString(AUTH_TOKEN_KEY, null) != null;
    }

    /**
     *
     * @param username
     * @param password
     * @param listener
     */
    public void auth(final String username, final String password, final UserAuthListener listener) {
        if (username == null || username.isEmpty()) throw new IllegalArgumentException();
        if (password == null  || password.isEmpty())  throw new IllegalArgumentException();
        if (listener == null) throw new IllegalArgumentException();

        JSONObject params = new JSONObject();
        try {
            params.put("username", username);
            params.put("password", password);
            params.put("install_id", Installation.id(mContext));
        }
        catch(Exception e) {
            listener.onAuthError(UserAuthListener.ERROR_SYSTEM);
            return;
        }

        JsonObjectRequest jsObjRequest = new JsonObjectRequest(Request.Method.POST, URL_LOGIN, params,
            new Response.Listener<JSONObject>() {
                @Override
                public void onResponse(JSONObject response) {
                    if (response == null) {
                        listener.onAuthError(UserAuthListener.ERROR_SYSTEM);
                        return;
                    }

                    if (!response.has("token")) {
                        listener.onAuthError(UserAuthListener.ERROR_BAD_AUTH_CREDENTIALS);
                        return;
                    }

                    try {
                        SharedPreferences.Editor editor = mSharedPref.edit();
                        editor.putString(AUTH_TOKEN_KEY, response.getString("token"));
                        editor.commit();
                    }
                    catch(Exception e) {
                        listener.onAuthError(UserAuthListener.ERROR_SYSTEM);
                        return;
                    }

                    listener.onAuthSuccess();
                }
            },
            new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    // For a proper reason API give us 400 *error* code when auth credentials are wrong.
                    // So we need to handle it here.
                    if (error.networkResponse.statusCode == 400) {
                        listener.onAuthError(UserAuthListener.ERROR_BAD_AUTH_CREDENTIALS);
                        return;
                    }

                    Log.e(LOG_TAG, "Login error: " + error.getMessage());
                    listener.onAuthError(UserAuthListener.ERROR_SYSTEM);
                }
            }
        );

        mApp.getRequestQueue().add(jsObjRequest);
    }

    /**
     *
     */
    public void logOut() {
        SharedPreferences.Editor editor = mSharedPref.edit();
        editor.remove(AUTH_TOKEN_KEY);
        editor.commit();
    }
}

// curl -X POST -d "username=test88&password=test&install_id=68753A44-4D6F-1226-9C60-0050E4C00067" https://138.201.20.149/v1.02/auth-token/
