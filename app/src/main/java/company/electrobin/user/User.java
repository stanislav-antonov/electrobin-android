package company.electrobin.user;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

import company.electrobin.ElectrobinApplication;
import company.electrobin.common.Constants;
import company.electrobin.common.Installation;

public class User {

    private Context mContext;
    private ElectrobinApplication mApp;
    private SharedPreferences mSharedPref;

    private final static String URL_AUTH = Constants.REST_API_BASE_URL + "/auth-token/";
    private final static String URL_PROFILE = Constants.REST_API_BASE_URL + "/profile/";

    private UserProfile mProfile;

    private final static String LOG_TAG = User.class.getSimpleName();
    private final static String JSON_AUTH_TOKEN_KEY = "token";
    public final static String SHARED_PREFERENCES_FILE_KEY = User.class.getName();
    private final static String PREFERENCES_AUTH_TOKEN_KEY = "auth_token";
    private final static String PREFERENCES_PROFILE_KEY = "profile";

    public static class UserProfile {
        public String mUsername;
        public String mFirstName;
        public String mLastName;
        public String mEmail;
    }

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
        return mSharedPref.getString(PREFERENCES_AUTH_TOKEN_KEY, null);
    }

    /**
     *
     * @return
     */
    public boolean isLoggedIn() {
        return mSharedPref.getString(PREFERENCES_AUTH_TOKEN_KEY, null) != null;
    }

    /**
     *
     * @param username
     * @param password
     * @param listener
     */
    public void auth(final String username, final String password, final UserAuthListener listener) {
        // curl -X POST -d "username=test88&password=test&install_id=68753A44-4D6F-1226-9C60-0050E4C00067" https://138.201.20.149/v1.02/auth-token/
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

        JsonObjectRequest jsObjRequest = new JsonObjectRequest(Request.Method.POST, URL_AUTH, params,
            new Response.Listener<JSONObject>() {
                @Override
                public void onResponse(JSONObject response) {
                    if (response == null) {
                        listener.onAuthError(UserAuthListener.ERROR_SYSTEM);
                        return;
                    }

                    if (!response.has(JSON_AUTH_TOKEN_KEY)) {
                        listener.onAuthError(UserAuthListener.ERROR_INVALID_AUTH_CREDENTIALS);
                        return;
                    }

                    try {
                        SharedPreferences.Editor editor = mSharedPref.edit();
                        editor.putString(PREFERENCES_AUTH_TOKEN_KEY, response.getString(JSON_AUTH_TOKEN_KEY));
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
                    if (error != null && error.networkResponse != null && error.networkResponse.statusCode == 400) {
                        listener.onAuthError(UserAuthListener.ERROR_INVALID_AUTH_CREDENTIALS);
                        return;
                    }

                    listener.onAuthError(UserAuthListener.ERROR_SYSTEM);
                }
            }
        );

        mApp.getRequestQueue().add(jsObjRequest);
    }


    private void storeProfile(String strProfileJSON) {
        SharedPreferences.Editor editor = mSharedPref.edit();
        editor.putString(PREFERENCES_PROFILE_KEY, strProfileJSON);
        editor.commit();
    }


    private String retrieveProfile() {
        return mSharedPref.getString(PREFERENCES_PROFILE_KEY, null);
    }

    /**
     *
     * @param strProfileJSON
     * @return
     * @throws JSONException
     */
    private boolean setProfile(String strProfileJSON) throws JSONException {
        JSONObject joProfile = new JSONObject(strProfileJSON);
        UserProfile profile = new UserProfile();

        profile.mUsername = joProfile.getString("username");
        profile.mFirstName = joProfile.getString("first_name");
        profile.mLastName = joProfile.getString("last_name");
        profile.mEmail = joProfile.getString("email");

        mProfile = profile;

        return true;
    }

    /**
     *
     * @return
     */
    public UserProfile getProfile() {
        return mProfile;
    }

    /**
     *
     * @param listener
     */
    public void loadProfile(final UserProfileLoadListener listener) {
        // curl -k -X GET https://185.118.64.121/v1.02/profile/ -H 'Authorization: Token some-token-string'
        if (!isLoggedIn()) throw new IllegalStateException("User is not logged in");

        StringRequest stringRequest = new StringRequest(Request.Method.GET, URL_PROFILE,
            new Response.Listener<String>() {
                @Override
                public void onResponse(String strNewProfileJSON) {
                    String strCurrentProfileJSON = retrieveProfile();
                    try {
                        setProfile(strNewProfileJSON);
                        storeProfile(strNewProfileJSON);
                    }
                    catch (Exception e) {
                        Log.i(LOG_TAG, "Failed to set new profile: " + e.getMessage());
                        try {
                            setProfile(strCurrentProfileJSON);
                        } catch (Exception e1) {
                            Log.e(LOG_TAG, "Failed to load profile: " + e1.getMessage());
                            listener.onUserProfileLoadError(UserProfileLoadListener.ERROR_SYSTEM);

                            return;
                        }
                    }

                    listener.onUserProfileLoadSuccess();
                }
            },
            new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    if (error != null && error.networkResponse != null && error.networkResponse.statusCode == 401) {
                        listener.onUserProfileLoadError(UserProfileLoadListener.ERROR_INVALID_AUTH_TOKEN);
                        return;
                    }

                    Log.e(LOG_TAG, "Failed to load profile");
                    listener.onUserProfileLoadError(UserProfileLoadListener.ERROR_SYSTEM);
                }
            }
        ) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String>  params = new HashMap<String, String>();
                params.put("Authorization", String.format("Token %s", getAuthToken()));

                return params;
            }
        };

        mApp.getRequestQueue().add(stringRequest);
    }

    /**
     *
     */
    public void logOut() {
        SharedPreferences.Editor editor = mSharedPref.edit();
        editor.remove(PREFERENCES_AUTH_TOKEN_KEY);
        editor.commit();
    }
}
