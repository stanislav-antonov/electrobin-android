package company.electrobin;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
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
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import company.electrobin.i10n.I10n;
import company.electrobin.network.TCPClientListener;
import company.electrobin.network.TCPClientService;
import company.electrobin.user.User;
import company.electrobin.user.User.UserProfile;

public class RouteActivity extends AppCompatActivity implements RouteListFragment.OnFragmentInteractionListener, UserProfileFragment.OnFragmentInteractionListener, FragmentManager.OnBackStackChangedListener {

    private ElectrobinApplication mApp;
    private User mUser;
    private I10n mI10n;

    private WebView mWvMap;

    private RelativeLayout mRlRouteMap;

    private RelativeLayout mRlLoading;
    private RelativeLayout mRlLoadRetry;
    private RelativeLayout mRlRouteUpdated;

    private Route mCurrentRoute;

    private boolean mIsMapLoading;

    private Handler mHandler = new Handler();
    private MapLoadBreaker mMapLoadBreaker;

    private FragmentManager mFragmentManager;

    private final static String LOG_TAG = RouteActivity.class.getSimpleName();
    private final static int TIMEOUT_MAP_LOAD = 30000;

    private final static String FRAGMENT_USER_PROFILE = "fragment_user_profile";
    private final static String FRAGMENT_ROUTE_LIST = "fragment_route_list";

    public static class Route {
        private Integer mId;
        private String mDate;
        private List<Point> mPointList;

        public Route(int id, String date, List<Point> pointList) {
            mId = id;
            mDate = date;
            mPointList = pointList;
        }

        public static class Point {
            public Integer mId;
            public String mAddress;
            public String mCity;
            public double mLat;
            public double mLng;
        }

        public String getDate() { return mDate; }

        public List<Point> getPointList() { return mPointList; }

        public Integer getId() { return mId; }
    }

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
            // mIsMapLoading = false;
            // mWvMap.stopLoading();

            // mRlLoading.setVisibility(View.GONE);
            // mRlLoadRetry.setVisibility(View.VISIBLE);

            // Button btnLoadRetry = (Button)mRlLoadRetry.findViewById(R.id.load_retry_button);
            // btnLoadRetry.setText(mI10n.l("retry"));
            // btnLoadRetry.setOnClickListener(new MapLoadRetryHandler());

            // TextView tvErrorMapLoad =(TextView)mRlLoadRetry.findViewById(R.id.error_map_load_text);
            // tvErrorMapLoad.setText(mI10n.l("error_map_load"));
        }
    }

    private class MapLoadRetryHandler implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            // mRlLoadRetry.setVisibility(View.GONE);
            // mRlLoading.setVisibility(View.VISIBLE);
            // loadMap();
        }
    }

    private class SocketAPIEventsHandler implements TCPClientListener {

        private final String LOG_TAG = SocketAPIEventsHandler.class.getName();

        private final static String JSON_ACTION_KEY = "action";
        private final static String JSON_ACTION_NEW_ROUTE = "new_route";
        private final static String JSON_ACTION_UPDATE_TOKEN = "update_token";

        private final static String JSON_ROUTE_ID_KEY = "id";
        private final static String JSON_ROUTE_DATE_KEY = "created";
        private final static String JSON_ROUTE_POINTS_KEY = "points";
        private final static String JSON_ROUTE_POINT_ID_KEY = "id";
        private final static String JSON_ROUTE_POINT_ADDRESS_KEY = "address";
        private final static String JSON_ROUTE_POINT_CITY_KEY = "city";
        private final static String JSON_ROUTE_POINT_LONGITUDE_KEY = "longitude";
        private final static String JSON_ROUTE_POINT_LATITUDE_KEY = "latitude";


        @Override
        public void onConnectResult(int result) {
            Log.d(LOG_TAG, "Connect result: " + result);
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

                if (!json.has(JSON_ACTION_KEY)) return;
                String action = json.getString(JSON_ACTION_KEY);

                switch (action) {
                    case JSON_ACTION_NEW_ROUTE: {
                        setCurrentRoute(newRoute(json));

                        RouteListFragment routeListFragment = (RouteListFragment)mFragmentManager
                                .findFragmentByTag(FRAGMENT_ROUTE_LIST);

                        if (routeListFragment != null && routeListFragment.isVisible()) {
                            // Just update route list
                            routeListFragment.showRouteList();
                        }
                        else {
                            FragmentTransaction fragmentTransaction = mFragmentManager.beginTransaction();
                            fragmentTransaction.replace(R.id.fragment_container, RouteListFragment.newInstance(RouteListFragment.LAYOUT_DISPLAYED_ROUTE_LIST),
                                    FRAGMENT_ROUTE_LIST).addToBackStack(null);
                            fragmentTransaction.commit();
                        }

                        showRouteUpdatedNotification();

                        break;
                    }

                    case JSON_ACTION_UPDATE_TOKEN:
                        break;
                }
            } catch (Exception e) {
                Log.d(LOG_TAG, e.getMessage());
            }
        }

        /**
         *
         */
        private Route newRoute(JSONObject json) throws Exception {
            Integer routeId = null;
            if (json.has(JSON_ROUTE_ID_KEY)) {
                try {
                    routeId = json.getInt(JSON_ROUTE_ID_KEY);
                } catch (Exception e) {
                    throw new Exception(e.getMessage());
                }
            }

            if (routeId == null) throw new Exception("No route id");

            String routeDate = null;
            if (json.has(JSON_ROUTE_DATE_KEY)) {
                try {
                    // 2014-12-28T19:50:40.964531Z
                    routeDate = json.getString(JSON_ROUTE_DATE_KEY);

                    @SuppressLint("SimpleDateFormat")
                    SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
                    Date date = df.parse(routeDate);

                    @SuppressLint("SimpleDateFormat")
                    Format formatter = new SimpleDateFormat("H:mm d.MM.yyyy");
                    routeDate = formatter.format(date);
                }
                catch (Exception e) {
                    throw new Exception(e.getMessage());
                }
            }

            if (routeDate == null) throw new Exception("No route date");

            List<RouteActivity.Route.Point> pointList = new ArrayList<>();
            if (json.has(JSON_ROUTE_POINTS_KEY)) {
                try {
                    JSONArray jaPointList = json.getJSONArray(JSON_ROUTE_POINTS_KEY);
                    for (int i = 0; i < jaPointList.length(); i++) {
                        JSONObject joPoint = jaPointList.getJSONObject(i);

                        Route.Point point = new Route.Point();

                        point.mId = joPoint.getInt(JSON_ROUTE_POINT_ID_KEY);
                        point.mAddress = joPoint.getString(JSON_ROUTE_POINT_ADDRESS_KEY);
                        point.mCity = joPoint.getString(JSON_ROUTE_POINT_CITY_KEY);
                        point.mLng = joPoint.getDouble(JSON_ROUTE_POINT_LONGITUDE_KEY);
                        point.mLat = joPoint.getDouble(JSON_ROUTE_POINT_LATITUDE_KEY);

                        pointList.add(point);
                    }
                }
                catch (Exception e) {
                    throw new Exception(e.getMessage());
                }
            }

            if (pointList.isEmpty()) throw new Exception("Route point list is empty");

            return new Route(routeId, routeDate, pointList);
        }
    }

    private class UserProfileShowHandler implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            FragmentTransaction fragmentTransaction = mFragmentManager.beginTransaction();
            fragmentTransaction.replace(R.id.fragment_container, UserProfileFragment.newInstance(), FRAGMENT_USER_PROFILE).addToBackStack(null);
            fragmentTransaction.commit();
        }
    }

    private TCPClientService mService;
    private boolean mBound = false;

    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            TCPClientService.TCPClientServiceBinder binder = (TCPClientService.TCPClientServiceBinder)service;
            mService = binder.getService();
            mBound = true;

            mService.start(new SocketAPIEventsHandler());
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
        }
    };

    /**
     *
     */
    private void setCurrentRoute(Route route) {
        mCurrentRoute = route;
    }

    /**
     *
     * @return
     */
    public Route getCurrentRoute() {
        return mCurrentRoute;
    }

    /**
     *
     */
    @SuppressLint("SetJavaScriptEnabled")
    private void showRouteMap() {
        if (mWvMap == null) { // TODO: do we really need to cache map?
            // mWvMap = (WebView)findViewById(R.id.map);

            WebSettings webSettings = mWvMap.getSettings();
            webSettings.setJavaScriptEnabled(true);

            mWvMap.addJavascriptInterface(new WebAppInterface(this), "Android");
            mWvMap.setWebViewClient(new MyWebViewClient());
        }

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

        mRlRouteUpdated = (RelativeLayout)findViewById(R.id.route_updated_layout);
        mRlRouteUpdated.setVisibility(View.GONE);

        ((TextView)mRlRouteUpdated.findViewById(R.id.route_updated_text)).setText(mI10n.l("route_updated"));

        //mRlRouteMap = (RelativeLayout)findViewById(R.id.route_map_layout);
        mRlLoading = (RelativeLayout)findViewById(R.id.loading_layout);

        //mRlLoadRetry = (RelativeLayout)findViewById(R.id.load_retry_layout);
        // mRlLoadRetry.setVisibility(View.GONE);

        mFragmentManager = getSupportFragmentManager();
        mFragmentManager.addOnBackStackChangedListener(this);

        FragmentTransaction fragmentTransaction = mFragmentManager.beginTransaction();
        fragmentTransaction.replace(R.id.fragment_container, RouteListFragment.newInstance(),
                FRAGMENT_ROUTE_LIST);
        fragmentTransaction.commit();

        shouldDisplayHomeUp();
    }

    @Override
    protected void onStart() {
        super.onStart();
        Intent intent = new Intent(this, TCPClientService.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mBound) {
            unbindService(mConnection);
            mBound = false;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_route, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     *
     */
    private void showRouteUpdatedNotification() {
        ObjectAnimator fadeIn = ObjectAnimator.ofFloat(mRlRouteUpdated, "alpha", 0.0f, 0.95f);
        fadeIn.setDuration(1000);

        ObjectAnimator fadeOut = ObjectAnimator.ofFloat(mRlRouteUpdated, "alpha", 0.95f, 0.0f);
        fadeOut.setDuration(1000);

        final AnimatorSet as = new AnimatorSet();
        as.play(fadeOut).after(fadeIn).after(2000L);

        as.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {
                mRlRouteUpdated.setVisibility(View.VISIBLE);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                mRlRouteUpdated.setVisibility(View.GONE);
            }

            @Override
            public void onAnimationCancel(Animator animation) {}

            @Override
            public void onAnimationRepeat(Animator animation) {}
        });

        as.start();
    }

    /**
     *
     */
    private void setupCustomActionBar() {
        UserProfile uProfile = mUser.getProfile();
        if (uProfile == null) return;

        final ActionBar actionBar = getSupportActionBar();
        if (actionBar == null) return;

        final ViewGroup actionBarLayout = (ViewGroup)getLayoutInflater().inflate(
                R.layout.action_bar_layout,
                null);

        actionBar.setDisplayShowHomeEnabled(false);
        actionBar.setDisplayShowTitleEnabled(false);
        actionBar.setDisplayShowCustomEnabled(true);
        actionBar.setCustomView(actionBarLayout);

        final Button btnActionBarUserProfile = (Button)findViewById(R.id.action_bar_user_profile_button);
        btnActionBarUserProfile.setText(String.format("%s %s", uProfile.mFirstName, uProfile.mLastName));
        btnActionBarUserProfile.setOnClickListener(new UserProfileShowHandler());
    }

    /**
     *
     */
    @Override
    public boolean onGetIsConnected() {
        return mBound && mService.isConnected();
    }

    /**
     *
     */
    @Override
    public Route onGetRoute() {
        return getCurrentRoute();
    }

    @Override
    public void onBackStackChanged() {
        shouldDisplayHomeUp();
    }

    public void shouldDisplayHomeUp(){
        final ActionBar actionBar = getSupportActionBar();
        if (actionBar != null)
            actionBar.setDisplayHomeAsUpEnabled(getSupportFragmentManager().getBackStackEntryCount() > 0);
    }

    @Override
    public boolean onSupportNavigateUp() {
        getSupportFragmentManager().popBackStack();
        return true;
    }
}
