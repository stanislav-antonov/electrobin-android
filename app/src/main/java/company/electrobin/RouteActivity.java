package company.electrobin;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
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
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.ActionBar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import android.widget.Button;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

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

public class RouteActivity extends AppCompatActivity implements
        RouteListFragment.OnFragmentInteractionListener,
        RouteMapFragment.OnFragmentInteractionListener,
        UserProfileFragment.OnFragmentInteractionListener,
        FragmentManager.OnBackStackChangedListener {

    private User mUser;
    private I10n mI10n;
    private ElectrobinApplication mApp;

    private RelativeLayout mRlRouteUpdated;

    private FragmentManager mFragmentManager;
    private Route mCurrentRoute;

    private final static String FRAGMENT_USER_PROFILE = "fragment_user_profile";
    private final static String FRAGMENT_ROUTE_LIST = "fragment_route_list";
    private final static String FRAGMENT_ROUTE_MAP = "fragment_route_map";

    /**
     *
     */
    public static class Route {
        private Integer mId;
        private String mDate;
        private List<Point> mPointList;
        private Point mCurrentStartPoint;

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

        public String getPointsJSON() {
            String result = "";
            for (Point point : getPointList())
                result += String.format(", [%s, %s]", point.mLat, point.mLng);

            return String.format("[%s]", result.replaceFirst(", ", ""));
        }

        public void setStartPoint(double lat, double lng) {
            Point point = new Point();
            point.mLat = lat;
            point.mLng = lng;
            getPointList().add(0, point);
            mCurrentStartPoint = point;
        }

        public Point getStartPoint() {
            return mCurrentStartPoint;
        }

        public boolean hasStartPoint() {
            return mCurrentStartPoint != null;
        }

        public Integer getId() { return mId; }
    }

    /**
     *
     */
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

                        if (routeListFragment == null) {
                            FragmentTransaction fragmentTransaction = mFragmentManager.beginTransaction();
                            fragmentTransaction.replace(R.id.fragment_container, RouteListFragment.newInstance(RouteListFragment.LAYOUT_DISPLAYED_ROUTE_LIST),
                                    FRAGMENT_ROUTE_LIST).addToBackStack(null);
                            fragmentTransaction.commit();
                        }
                        else if (routeListFragment.isVisible()) {
                            // Just update route list
                            routeListFragment.showRouteList();
                            showRouteUpdatedNotification(true);
                        }
                        else {
                            showRouteUpdatedNotification(false);
                        }

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

    /**
     *
     */
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
     * @param savedInstanceState
     */
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

        mFragmentManager = getSupportFragmentManager();
        mFragmentManager.addOnBackStackChangedListener(this);

        FragmentTransaction fragmentTransaction = mFragmentManager.beginTransaction();
        fragmentTransaction.replace(R.id.fragment_container, RouteListFragment.newInstance(),
                FRAGMENT_ROUTE_LIST);
        fragmentTransaction.commit();

        shouldDisplayHomeUp();
    }

    /**
     *
     */
    @Override
    protected void onStart() {
        super.onStart();
        Intent intent = new Intent(this, TCPClientService.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    /**
     *
     */
    @Override
    protected void onStop() {
        super.onStop();
        if (mBound) {
            unbindService(mConnection);
            mBound = false;
        }
    }

    /**
     *
     * @param menu
     * @return
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_route, menu);
        return true;
    }

    /**
     *
     * @param item
     * @return
     */
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
    private void showRouteUpdatedNotification(boolean isOnRouteList) {
        final ObjectAnimator fadeIn = ObjectAnimator.ofFloat(mRlRouteUpdated, "alpha", 0.0f, 0.95f);
        fadeIn.setDuration(1000);

        final ObjectAnimator fadeOut = ObjectAnimator.ofFloat(mRlRouteUpdated, "alpha", 0.95f, 0.0f);
        fadeOut.setDuration(1000);

        final Button btnRouteList = (Button)mRlRouteUpdated.findViewById(R.id.route_list_button);
        final ImageButton btnClose = (ImageButton)mRlRouteUpdated.findViewById(R.id.close_button);

        if (isOnRouteList) {
            btnRouteList.setVisibility(View.GONE);
            btnClose.setVisibility(View.GONE);

            final AnimatorSet as = new AnimatorSet();
            as.play(fadeOut).after(fadeIn).after(2000L);

            as.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animation) {
                    mRlRouteUpdated.setVisibility(View.VISIBLE);
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    mRlRouteUpdated.setVisibility(View.GONE);
                }
            });

            as.start();
        }
        else {
            btnRouteList.setVisibility(View.VISIBLE);
            btnClose.setVisibility(View.VISIBLE);

            btnRouteList.setText(mI10n.l("to_route_list"));

            fadeIn.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animation) {
                    mRlRouteUpdated.setVisibility(View.VISIBLE);
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    btnClose.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) { fadeOut.start(); }
                    });

                    btnRouteList.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            fadeOut.start();

                            FragmentTransaction fragmentTransaction = mFragmentManager.beginTransaction();
                            fragmentTransaction.replace(R.id.fragment_container,
                                    RouteListFragment.newInstance(RouteListFragment.LAYOUT_DISPLAYED_ROUTE_LIST),
                                    FRAGMENT_ROUTE_LIST).addToBackStack(null);

                            fragmentTransaction.commit();
                        }
                    });
                }
            });

            fadeOut.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mRlRouteUpdated.setVisibility(View.GONE);
                }
            });

            fadeIn.start();
        }
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

    /**
     *
     */
    @Override
    public void onRouteBuildingStart() {

    }

    /**
     *
     */
    @Override
    public void onRouteBuildingReady() {

    }

    /**
     *
     */
    @Override
    public void onRouteStart() {
        FragmentTransaction fragmentTransaction = mFragmentManager.beginTransaction();
        fragmentTransaction.replace(R.id.fragment_container, RouteMapFragment.newInstance(), FRAGMENT_ROUTE_MAP).addToBackStack(null);
        fragmentTransaction.commit();
    }

    /**
     *
     */
    @Override
    public void onBackStackChanged() {
        shouldDisplayHomeUp();
    }

    /**
     *
     */
    public void shouldDisplayHomeUp(){
        final ActionBar actionBar = getSupportActionBar();
        if (actionBar != null)
            actionBar.setDisplayHomeAsUpEnabled(getSupportFragmentManager().getBackStackEntryCount() > 0);
    }

    /**
     *
     * @return
     */
    @Override
    public boolean onSupportNavigateUp() {
        getSupportFragmentManager().popBackStack();
        return true;
    }
}
