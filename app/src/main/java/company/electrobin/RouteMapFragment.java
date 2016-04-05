package company.electrobin;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;

import company.electrobin.i10n.I10n;
import company.electrobin.user.User;

public class RouteMapFragment extends Fragment {

    private User mUser;
    private I10n mI10n;
    private ElectrobinApplication mApp;

    private WebView mWvMap;

    private RelativeLayout mRlRouteMap;
    private RelativeLayout mRlLoading;
    private RelativeLayout mRlLoadRetry;
    private RelativeLayout mRlRouteBuilding;

    private MapLoadBreaker mMapLoadBreaker;
    private RouteViewer mRouteViewer;

    private int mMapCurrentState;

    private OnFragmentInteractionListener mListener;
    private Handler mHandler = new Handler();

    private final static String LOG_TAG = RouteActivity.class.getSimpleName();

    private final static int MAP_STATE_INITIAL = 0;
    private final static int MAP_STATE_LOADING = 1;
    private final static int MAP_STATE_READY = 2;

    /**
     *
     */
    public interface OnFragmentInteractionListener {
        public RouteActivity.Route onGetRoute();
        public void onRouteBuildingStart();
        public void onRouteBuildingReady();
        public void onRoutePointClick(int idx);
    }

    /**
     *
     */
    private class UserLocationListener implements LocationListener {

        private Location mCurrentLocation;
        private static final int LOCATION_EXPIRES_TIME_INTERVAL = 1000 * 60 * 2;

        @Override
        public synchronized void onLocationChanged(Location location) {
            // Check the new location fix
            if (isBetterLocation(location, mCurrentLocation))
                mCurrentLocation = location;

            mRouteViewer.notifyGotUserLocation(mCurrentLocation);
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

    /**
     *
     */
    private class MapWebViewClient extends WebViewClient {

        @Override
        public void onPageFinished(WebView view, String url) {
            LocationManager locationManager = (LocationManager)getActivity().getSystemService(Context.LOCATION_SERVICE);
            UserLocationListener locationListener = new UserLocationListener();

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

    /**
     *
     */
    private class MapJavaScriptInterface {

        private Context mContext;

        /**
         *
         * @param context
         */
        public MapJavaScriptInterface(Context context) {
            mContext = context;
        }

        /**
         *
         */
        @JavascriptInterface
        public void onMapReady() {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mMapCurrentState = MAP_STATE_READY;
                    mMapLoadBreaker.cancel();

                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            mRlLoading.setVisibility(View.GONE);
                            mRouteViewer.notifyMapReady();
                        }
                    });
                }
            });
        }

        /**
         *
         */
        @JavascriptInterface
        public void onRouteBuildingStart() {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mListener.onRouteBuildingStart();
                }
            });
        }

        /**
         *
         */
        @JavascriptInterface
        public void onRouteBuildingReady() {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mRouteViewer.notifyRouteDisplayed();
                    mListener.onRouteBuildingReady();
                }
            });
        }


        /**
         *
         */
        @JavascriptInterface
        public void onRoutePointClick(final int idx) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    (Toast.makeText(getActivity(), "Point idx: " + idx, Toast.LENGTH_SHORT)).show();
                    mListener.onRoutePointClick(idx);
                }
            });
        }
    }

    /**
     *
     */
    private class MapLoadBreaker implements Runnable  {

        private final static int TIMEOUT_MAP_LOAD = 30000;

        /**
         *
         */
        public void watch() {
            cancel();
            mHandler.postDelayed(this, TIMEOUT_MAP_LOAD);
        }

        /**
         *
         */
        public void cancel() {
            mHandler.removeCallbacks(this);
        }

        /**
         *
         */
        @Override
        public void run() {
            // Break the map loading
            mWvMap.stopLoading();
            mMapCurrentState = MAP_STATE_INITIAL;

            mRlLoading.setVisibility(View.GONE);
            mRlLoadRetry.setVisibility(View.VISIBLE);

            Button btnLoadRetry = (Button)mRlLoadRetry.findViewById(R.id.load_retry_button);
            btnLoadRetry.setText(mI10n.l("retry"));
            btnLoadRetry.setOnClickListener(new MapLoadRetryHandler());

            TextView tvErrorMapLoad =(TextView)mRlLoadRetry.findViewById(R.id.error_map_load_text);
            tvErrorMapLoad.setText(mI10n.l("error_map_load"));
        }
    }

    /**
     *
     */
    private class MapLoadRetryHandler implements View.OnClickListener {
        /**
         *
         * @param v
         */
        @Override
        public void onClick(View v) {
            mRlLoadRetry.setVisibility(View.GONE);
            mRlLoading.setVisibility(View.VISIBLE);
            loadMap();
        }
    }

    /**
     *
     */
    private class RouteViewer {

        private boolean mHasMapReady;
        private boolean mGotFirstLocation;

        private Location mCurrentLocation;

        /**
         *
         */
        public void notifyMapReady() {
            if (mHasMapReady) return;
            mHasMapReady = true;

            if (!mGotFirstLocation)
                mRlRouteBuilding.setVisibility(View.VISIBLE);

            drawFirstRoute();
        }

        /**
         *
         * @param location
         */
        public void notifyGotUserLocation(Location location) {
            mCurrentLocation = location;

            if (mGotFirstLocation) return;
            mListener.onGetRoute().setStartPoint(mCurrentLocation.getLatitude(), mCurrentLocation.getLongitude());
            mGotFirstLocation = true;

            drawFirstRoute();
        }

        /**
         *
         */
        public void notifyRouteDisplayed() {
            mRlRouteBuilding.setVisibility(View.GONE);
            drawUserLocation();
        }

        /**
         *
         */
        private void drawFirstRoute() {
            if (mGotFirstLocation && mHasMapReady)
                drawRoute();
        }

        /**
         *
         */
        private void reset() {
            mHasMapReady = false;
            mGotFirstLocation = false;
        }

        /**
         *
         */
        private void drawRoute() {
            final RouteActivity.Route route = mListener.onGetRoute();
            mWvMap.loadUrl(String.format("javascript:displayRoute('%s')", route.getPointsJSON()));
        }

        /**
         *
         */
        public void drawUserLocation() {
            mWvMap.loadUrl(String.format("javascript:updatePosition(%1$s, %2$s)",
                    mCurrentLocation.getLatitude(), mCurrentLocation.getLongitude()));
        }
    }

    /**
     *
     * @return
     */
    public static RouteMapFragment newInstance() {
        return new RouteMapFragment();
    }

    /**
     *
     * @param savedInstanceState
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mApp = (ElectrobinApplication)getActivity().getApplicationContext();
        mUser = mApp.getUser();
        mI10n = mApp.getI10n();

        mMapLoadBreaker = new MapLoadBreaker();
        mRouteViewer = new RouteViewer();
    }

    /**
     *
     * @param inflater
     * @param container
     * @param savedInstanceState
     * @return
     */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_route_map, container, false);

        mRlRouteMap = (RelativeLayout)view.findViewById(R.id.route_map_layout);
        mRlLoading = (RelativeLayout)view.findViewById(R.id.loading_layout);

        mRlLoadRetry = (RelativeLayout)view.findViewById(R.id.load_retry_layout);
        mRlLoadRetry.setVisibility(View.GONE);

        mRlRouteBuilding = (RelativeLayout)view.findViewById(R.id.route_building_layout);
        mRlRouteBuilding.setVisibility(View.GONE);
        ((TextView) mRlRouteBuilding.findViewById(R.id.route_building_text)).setText(mI10n.l("route_building"));

        return view;
    }

    /**
     *
     * @param activity
     */
    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            mListener = (OnFragmentInteractionListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement OnFragmentInteractionListener");
        }
    }

    @Override
    public void onActivityCreated (Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        showRouteMap();
    }

    /**
     *
     */
    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    /**
     *
     */
    @SuppressLint("SetJavaScriptEnabled")
    private void showRouteMap() {
        if (mWvMap == null) { // TODO: do we really need to cache map?
            mWvMap = (WebView)getActivity().findViewById(R.id.map);

            WebSettings webSettings = mWvMap.getSettings();
            webSettings.setJavaScriptEnabled(true);

            mWvMap.addJavascriptInterface(new MapJavaScriptInterface(getActivity()), "Android");
            mWvMap.setWebViewClient(new MapWebViewClient());
        }

        mRlRouteMap.setVisibility(View.VISIBLE);

        loadMap();
    }

    /**
     *
     */
    private void loadMap() {
        if (mMapCurrentState == MAP_STATE_LOADING) return;

        try {
            InputStream is = getActivity().getAssets().open("map.html");
            byte[] buffer = new byte[is.available()];
            is.read(buffer);
            is.close();

            String htmlText = new String(buffer);

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

        mMapCurrentState = MAP_STATE_LOADING;
        mRlLoading.setVisibility(View.VISIBLE);
        mMapLoadBreaker.watch();
    }
}


/*

{"action":"new_route","created":"2014-12-28T19:50:40.964531Z","id":"21","points" : [{"id":333, "address":"Leninsky 21 22 33","city":"Moscow", "latitude":48.785346371124746, "longitude":44.57456924862674}, {"id":211, "address":"Ленинский","city":"Moscow", "latitude":48.78560940518449, "longitude":44.58332181428101}]}


 */