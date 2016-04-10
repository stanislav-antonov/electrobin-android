package company.electrobin;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.os.Handler;
import android.support.v4.content.LocalBroadcastManager;
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

    private int mMapState;

    private OnFragmentInteractionListener mListener;
    private Handler mHandler = new Handler();

    private final static String LOG_TAG = RouteActivity.class.getSimpleName();
    public final static String FRAGMENT_TAG = "fragment_route_map";

    private final static int MAP_STATE_INITIAL = 0;
    private final static int MAP_STATE_LOADING = 1;
    private final static int MAP_STATE_READY = 2;

    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Bundle bundle = intent.getExtras();

            if (bundle == null) return;
            Location location = bundle.getParcelable(RouteActivity.UserLocation.BUNDLE_KEY_LOCATION);

            if (location == null) return;
            mRouteViewer.notifyGotUserLocation(location);

            mTvBearing.setText(String.format("Bearing: %s", location.getBearing()));
        }
    };

    /**
     *
     */
    public interface OnFragmentInteractionListener {
        public RouteActivity.Route onGetRoute();
        public void onRouteBuildingStart();
        public void onRouteBuildingReady();
        public void onRoutePointClick(int id);
    }

    /**
     *
     */
    private class MapWebViewClient extends WebViewClient {

        @Override
        public void onPageFinished(WebView view, String url) {
            // TODO: Maybe a bad design?
            RouteActivity routeActivity = (RouteActivity)getActivity();
            routeActivity.getUserLocation().startLocationUpdates();
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
                    mMapState = MAP_STATE_READY;
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
        public void onRoutePointClick(final int id) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mListener.onRoutePointClick(id);
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
            mMapState = MAP_STATE_INITIAL;

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

            try {
                loadMap();
            } catch (Exception e) {
                Log.e(LOG_TAG, e.getMessage());
            }
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

            drawUserLocation();

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
            mWvMap.loadUrl(String.format("javascript:displayRoute('%s')", route.asJSON()));
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

    private TextView mTvBearing;

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

        mRlRouteMap = (RelativeLayout) view.findViewById(R.id.route_map_layout);
        mRlRouteMap.setVisibility(View.VISIBLE);

        mRlLoading = (RelativeLayout) view.findViewById(R.id.loading_layout);
        mRlLoading.setVisibility(View.GONE);

        mRlLoadRetry = (RelativeLayout) view.findViewById(R.id.load_retry_layout);
        mRlLoadRetry.setVisibility(View.GONE);

        mRlRouteBuilding = (RelativeLayout) view.findViewById(R.id.route_building_layout);
        mRlRouteBuilding.setVisibility(View.GONE);

        ((TextView) mRlRouteBuilding.findViewById(R.id.route_building_text)).setText(mI10n.l("route_building"));

        mTvBearing = (TextView)view.findViewById(R.id.bearing_text);

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

    /**
     *
     * @return
     * @throws Exception
     */
    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private WebView prepareMapWebView() throws Exception {
        final View view = getView();
        if (view == null) throw new IllegalStateException("Root view is null");

        final WebView wv;
        try {
            wv = (WebView) view.findViewById(R.id.map);

            WebSettings webSettings = wv.getSettings();
            webSettings.setJavaScriptEnabled(true);
            wv.addJavascriptInterface(new MapJavaScriptInterface(getActivity()), "Android");
            wv.setWebViewClient(new MapWebViewClient());

            return wv;
        }
        catch(Exception e) {
            throw new Exception(e.getMessage());
        }
    }

    /**
     *
     * @param savedInstanceState
     */
    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        if (mWvMap == null) {
            try {
                mWvMap = prepareMapWebView();
                loadMap();
            } catch (Exception e) {
                Log.e(LOG_TAG, e.getMessage());
            }
        }
    }

    /**
     *
     * @param outState
     */
    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
    }

    /**
     *
     */
    @Override
    public void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver(mBroadcastReceiver);
    }

    /**
     *
     */
    @Override
    public void onResume() {
        super.onResume();
        LocalBroadcastManager.getInstance(getActivity()).registerReceiver(mBroadcastReceiver,
                new IntentFilter(RouteActivity.UserLocation.BROADCAST_INTENT));
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
    private void loadMap() throws Exception {
        if (mMapState == MAP_STATE_LOADING) return;

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
            throw new Exception(e.getMessage());
        }

        mMapState = MAP_STATE_LOADING;
        mRlLoading.setVisibility(View.VISIBLE);
        mMapLoadBreaker.watch();
    }
}


/*

{"action":"new_route","created":"2014-12-28T19:50:40.964531Z","id":"21","points":[{"id":333, "address":"Точка 1","city":"Moscow", "latitude":48.785346371124746, "longitude":44.57456924862674},{"id":211, "address":"Точка 2","city":"Moscow", "latitude":48.78560940518449, "longitude":44.58332181428101},{"id":213, "address":"Точка 3","city":"Moscow", "latitude":48.77855526553813, "longitude":44.5642583540142}]}

 */