package company.electrobin;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.app.Fragment;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.RelativeLayout;

import java.io.IOException;
import java.io.InputStream;

public class RouteMapFragment extends Fragment {

    private WebView mWvMap;

    private RelativeLayout mRlRouteMap;
    private RelativeLayout mRlLoading;
    private RelativeLayout mRlLoadRetry;

    private MapLoadBreaker mMapLoadBreaker;

    private boolean mIsMapLoading;

    private OnFragmentInteractionListener mListener;
    private Handler mHandler = new Handler();

    private final static String LOG_TAG = RouteActivity.class.getSimpleName();
    private final static int TIMEOUT_MAP_LOAD = 30000;

    public interface OnFragmentInteractionListener {
        public void onFragmentInteraction(Uri uri);
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

    private class MapWebViewClient extends WebViewClient {

        @Override
        public void onPageFinished(WebView view, String url) {
            LocationManager locationManager = (LocationManager)getActivity().getSystemService(Context.LOCATION_SERVICE);
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

    private class MapJavaScriptInterface {

        private Context mContext;

        public MapJavaScriptInterface(Context context) {
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

    public static RouteMapFragment newInstance() {
        return new RouteMapFragment();
    }

    public RouteMapFragment() {

    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_route_map, container, false);

        mRlRouteMap = (RelativeLayout)view.findViewById(R.id.route_map_layout);
        mRlLoading = (RelativeLayout)view.findViewById(R.id.loading_layout);

        mRlLoadRetry = (RelativeLayout)view.findViewById(R.id.load_retry_layout);
        mRlLoadRetry.setVisibility(View.GONE);

        return view;
    }

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
        try {
            InputStream is = getActivity().getAssets().open("map.html");
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
}
