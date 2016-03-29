package company.electrobin;

import android.annotation.SuppressLint;
// import android.app.ActionBar;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.ActionBar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;

import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import company.electrobin.i10n.I10n;
import company.electrobin.network.TCPClientListener;
import company.electrobin.user.User;
import company.electrobin.user.UserProfile;

public class RouteActivity extends AppCompatActivity {

    private ElectrobinApplication mApp;
    private User mUser;
    private I10n mI10n;

    private Button mBtn;
    private WebView mWvMap;

    private RelativeLayout mRlRouteMap;
    private RelativeLayout mRlRouteWaiting;
    private RelativeLayout mRlRouteReview;

    private RelativeLayout mRlLoading;
    private RelativeLayout mRlLoadRetry;

    private boolean mIsMapLoading;

    private Handler mHandler = new Handler();
    private MapLoadBreaker mMapLoadBreaker;

    private final static String LOG_TAG = RouteActivity.class.getSimpleName();
    private final static int TIMEOUT_MAP_LOAD = 30000;

    private class MyLocationListener implements LocationListener {

        private Location mCurrentLocation;
        private static final int LOCATION_EXPIRES_TIME_INTERVAL = 1000 * 60 * 2;

        @Override
        public synchronized void onLocationChanged(Location location) {
            // Check the new location fix
            if (isBetterLocation(location, mCurrentLocation))
                mCurrentLocation = location;

            double lat = mCurrentLocation.getLatitude();
            double lng = mCurrentLocation.getLongitude();

            double accuracy = mCurrentLocation.getAccuracy();
            String provider = mCurrentLocation.getProvider();

            Log.d(LOG_TAG, String.format("coords: [%1$s, %2$s], provider: %3$s, accuracy: %4$s, bearing: %5$s",
                    lat, lng, provider, accuracy, mCurrentLocation.getBearing()));

            String strJs = String.format("javascript:updatePosition(%1$s, %2$s)", lat, lng);
            mWvMap.loadUrl(strJs);
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {}

        @Override
        public void onProviderEnabled(String provider) {}

        @Override
        public void onProviderDisabled(String provider) {}

        /** Determines whether one Location reading is better than the current Location fix
         * @param location  The new Location that you want to evaluate
         * @param currentBestLocation  The current Location fix, to which you want to compare the new one
         */
        protected boolean isBetterLocation(Location location, Location currentBestLocation) {
            if (currentBestLocation == null)
                // A new location is always better than no location
                return true;

            // Check whether the new location fix is newer or older
            long timeDelta = location.getTime() - currentBestLocation.getTime();
            boolean isSignificantlyNewer = timeDelta > LOCATION_EXPIRES_TIME_INTERVAL;
            boolean isSignificantlyOlder = timeDelta < -LOCATION_EXPIRES_TIME_INTERVAL;
            boolean isNewer = timeDelta > 0;

            // If it's been more than two minutes since the current location, use the new location
            // because the user has likely moved
            if (isSignificantlyNewer) {
                return true;
                // If the new location is more than two minutes older, it must be worse
            }
            else if (isSignificantlyOlder) {
                return false;
            }

            // Check whether the new location fix is more or less accurate
            int accuracyDelta = (int)(location.getAccuracy() - currentBestLocation.getAccuracy());
            boolean isLessAccurate = accuracyDelta > 0;
            boolean isMoreAccurate = accuracyDelta < 0;
            boolean isSignificantlyLessAccurate = accuracyDelta > 200;

            // Check if the old and new location are from the same provider
            boolean isFromSameProvider = isSameProvider(location.getProvider(),
                    currentBestLocation.getProvider());

            // Determine location quality using a combination of timeliness and accuracy
            if (isMoreAccurate) {
                return true;
            }
            else if (isNewer && !isLessAccurate) {
                return true;
            }
            else if (isNewer && !isSignificantlyLessAccurate && isFromSameProvider) {
                return true;
            }

            return false;
        }

        /**
         *
         * @param provider1
         * @param provider2
         * @return
         */
        private boolean isSameProvider(String provider1, String provider2) {
            if (provider1 == null)
                return provider2 == null;

            return provider1.equals(provider2);
        }
    }

    private class MyWebViewClient extends WebViewClient {

        @Override
        public void onPageFinished(WebView view, String url) {
            LocationManager locationManager = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
            MyLocationListener locationListener = new MyLocationListener();

            try {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000L, 0F, locationListener);
            }
            catch(IllegalArgumentException e) {
                Log.e(LOG_TAG, "Network provider error: " + e.getMessage());
            }

            try {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0F, locationListener);
            }
            catch (IllegalArgumentException e) {
                Log.e(LOG_TAG, "GPS provider error: " + e.getMessage());
            }
        }

        @Override
        public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
            Log.d(LOG_TAG, String.format("Error: %1$s, %2$s, %3$s", errorCode, description, failingUrl));
        }
    }

    private class WebAppInterface {

        private Context mContext;

        public WebAppInterface(Context context) {
            mContext = context;
        }

        @JavascriptInterface
        public void onMapReady() {
            // Mark the map is not loading anymore
            mIsMapLoading = false;

            // Important: it must go before we make mRlLoading invisible
            if (mMapLoadBreaker != null) mHandler.removeCallbacks(mMapLoadBreaker);

            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mRlLoading.setVisibility(View.GONE);
                }
            });
        }
    }

    private class MapLoadBreaker implements Runnable  {
        @Override
        public void run() {
            // Mark the map is not loading anymore
            mIsMapLoading = false;
            mWvMap.stopLoading();

            mRlLoading.setVisibility(View.GONE);
            mRlLoadRetry.setVisibility(View.VISIBLE);

            Button btnLoadRetry = (Button)mRlLoadRetry.findViewById(R.id.load_retry_button);
            btnLoadRetry.setText(mI10n.l("retry"));
            btnLoadRetry.setOnClickListener(new MapLoadRetryHandler());

            TextView tvErrorMapLoad =(TextView)mRlLoadRetry.findViewById(R.id.error_map_load_text);
            tvErrorMapLoad.setText(mI10n.l("error_map_load"));
        }
    }

    private class MapLoadRetryHandler implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            mRlLoadRetry.setVisibility(View.GONE);
            mRlLoading.setVisibility(View.VISIBLE);
            loadMap();
        }
    }

    private class SocketAPIEventsHandler implements TCPClientListener {

        private final String LOG_TAG = SocketAPIEventsHandler.class.getName();

        private final static String JSON_ACTION_KEY = "action";
        private final static String JSON_ACTION_NEW_ROUTE = "new_route";
        private final static String JSON_ACTION_UPDATE_TOKEN = "update_token";

        @Override
        public void onConnectResult(int result) {
            Log.d(LOG_TAG, "Connect result: " + result);
            mBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {

                    // String cmd = "{\"action\":\"start_route\", \"route_id\":\"234\", \"created\":\"2014-12-28T19:50:40.964531Z\"}";
                    // tcpClient.sendData(cmd);
                }
            });
        }

        @Override
        public String onAuthToken() {
            return mUser.getAuthToken();
        }

        @Override
        public void onDataReceived(final String data) {
            try {
                Log.d(LOG_TAG, "Data received: " + data);
                final JSONObject json = new JSONObject(data);
                if (!json.has(JSON_ACTION_KEY)) {
                    Log.i(LOG_TAG, "No action");
                    return;
                }

                String action = json.getString(JSON_ACTION_KEY);
                switch (action) {
                    case JSON_ACTION_NEW_ROUTE:
                        showRouteReview(json);
                        break;
                    case JSON_ACTION_UPDATE_TOKEN:
                        break;
                }
            }
            catch (Exception e) {
                Log.d(LOG_TAG, e.getMessage());
            }
        }
    }

    /**
     *
     */
    private void showRouteWaiting() {
        mRlRouteWaiting.setVisibility(View.VISIBLE);
        mRlRouteReview.setVisibility(View.GONE);

        ((TextView)mRlRouteWaiting.findViewById(R.id.route_waiting_text_1)).setText(mI10n.l("route_waiting_1"));
        ((TextView)mRlRouteWaiting.findViewById(R.id.route_waiting_text_2)).setText(mI10n.l("route_waiting_2"));
    }

    /**
     *
     */
    private void showRouteReview(JSONObject json) {
        mRlRouteReview.setVisibility(View.VISIBLE);
        mRlRouteWaiting.setVisibility(View.GONE);

        final Button btnRouteStart = (Button)mRlRouteReview.findViewById(R.id.route_start_button);
        btnRouteStart.setText(mI10n.l("route_start"));

        final TextView tvRouteDate = (TextView)mRlRouteReview.findViewById(R.id.route_date_text);
        tvRouteDate.setVisibility(View.GONE);

        final String JSON_DATE_KEY = "created";
        if (json.has(JSON_DATE_KEY)) {
            String strDate = null;
            try {
                // 2014-12-28T19:50:40.964531Z
                strDate = json.getString(JSON_DATE_KEY);

                @SuppressLint("SimpleDateFormat")
                SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
                Date date = df.parse(strDate);

                @SuppressLint("SimpleDateFormat")
                Format formatter = new SimpleDateFormat("H:mm d.MM.yyyy");
                strDate = formatter.format(date);
            }
            catch(Exception e) {
                strDate = null;
                Log.e(LOG_TAG, e.getMessage());
            }

            if (strDate != null) {
                tvRouteDate.setVisibility(View.VISIBLE);
                tvRouteDate.setText(String.format(mI10n.l("route_date"), strDate));
            }
        }

        final ListView lvRoutePoints = (ListView)mRlRouteReview.findViewById(R.id.route_points_list);
        lvRoutePoints.setVisibility(View.GONE);

        final String JSON_POINTS_KEY = "points";
        final String JSON_POINTS_ADDRESS = "address";

        if (json.has(JSON_POINTS_KEY)) {
            List<String> addressList = new ArrayList<>();
            try {
                JSONArray pointList = json.getJSONArray(JSON_POINTS_KEY);
                for (int i = 0; i < pointList.length(); i++) {
                    JSONObject point = pointList.getJSONObject(i);
                    addressList.add(point.getString(JSON_POINTS_ADDRESS));
                }
            }
            catch (Exception e) {
                Log.e(LOG_TAG, e.getMessage());
                addressList.clear();

                (Toast.makeText(getApplicationContext(), "Для отладки: ошибка в данных о точках маршрута", Toast.LENGTH_LONG)).show();
                showRouteWaiting();
            }

            if (!addressList.isEmpty()) {
                lvRoutePoints.setVisibility(View.VISIBLE);
                lvRoutePoints.setAdapter(new ArrayAdapter<String>(getApplicationContext(), R.layout.route_points_item_layout, addressList));
            }
        }
    }

    /**
     *
     */
    @SuppressLint("SetJavaScriptEnabled")
    private void showRouteMap() {
        if (mWvMap == null) { // TODO: do we really need to cache map?
            mWvMap = (WebView)findViewById(R.id.map);

            WebSettings webSettings = mWvMap.getSettings();
            webSettings.setJavaScriptEnabled(true);

            mWvMap.addJavascriptInterface(new WebAppInterface(this), "Android");
            mWvMap.setWebViewClient(new MyWebViewClient());
        }

        mRlRouteReview.setVisibility(View.GONE);
        mRlRouteWaiting.setVisibility(View.GONE);
        mRlRouteMap.setVisibility(View.VISIBLE);

        loadMap();
    }

    /**
     *
     */
    private void loadMap() {
        try {
            InputStream is = getAssets().open("map.html");
            byte[] buffer = new byte[is.available()];
            is.read(buffer);
            is.close();

            String htmlText = new String(buffer);

            if (mIsMapLoading) mWvMap.stopLoading();

            mWvMap.loadDataWithBaseURL(
                    "http://ru.yandex.api.yandexmapswebviewexample.ymapapp",
                    htmlText,
                    "text/html",
                    "UTF-8",
                    null
            );
        }
        catch (IOException e) {
            Log.e(LOG_TAG, e.getMessage());
            throw new IllegalArgumentException(e.getMessage());
        }

        mIsMapLoading = true;
        mRlLoading.setVisibility(View.VISIBLE);

        if (mMapLoadBreaker != null) mHandler.removeCallbacks(mMapLoadBreaker);

        mMapLoadBreaker = new MapLoadBreaker();
        mHandler.postDelayed(mMapLoadBreaker, TIMEOUT_MAP_LOAD);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_route);

        mApp = (ElectrobinApplication)getApplicationContext();
        mUser = mApp.getUser();
        mI10n = mApp.getI10n();

        setupCustomActionBar();

        mBtn = (Button)findViewById(R.id.jump_button);

        mRlRouteMap = (RelativeLayout)findViewById(R.id.route_map_layout);
        mRlRouteWaiting = (RelativeLayout)findViewById(R.id.route_waiting_layout);
        mRlRouteReview = (RelativeLayout)findViewById(R.id.route_review_layout);

        mRlLoading = (RelativeLayout)findViewById(R.id.loading_layout);

        mRlLoadRetry = (RelativeLayout)findViewById(R.id.load_retry_layout);
        mRlLoadRetry.setVisibility(View.GONE);

        showRouteWaiting();

        try {
            mApp.getTCPClient().start(new SocketAPIEventsHandler());
        } catch (Exception e) {
            Log.e(LOG_TAG, "Failed to start TCPClient: " + e.getMessage());
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_route, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }
        else if (id == R.id.action_logout) {
            mUser.logOut();
            startActivity(new Intent(RouteActivity.this, AuthActivity.class));
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     *
     */
    private void setupCustomActionBar() {
        UserProfile uProfile = mUser.getProfile();
        if (uProfile == null) return;

        final ViewGroup actionBarLayout = (ViewGroup) getLayoutInflater().inflate(
                R.layout.action_bar_layout,
                null);

        final ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayShowHomeEnabled(false);
        actionBar.setDisplayShowTitleEnabled(false);
        actionBar.setDisplayShowCustomEnabled(true);
        actionBar.setCustomView(actionBarLayout);

        final Button btnActionBarUserProfile = (Button)findViewById(R.id.action_bar_user_profile_button);

        btnActionBarUserProfile.setText(uProfile.mName);
    }
}
