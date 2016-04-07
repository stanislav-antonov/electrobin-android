package company.electrobin;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Parcelable;
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
        BinCardFragment.OnFragmentInteractionListener,
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
    private final static String FRAGMENT_BIN_CARD = "fragment_bin_card";

    private final static String LOG_TAG = RouteActivity.class.getSimpleName();

    /**
     *
     */
    public static class Route implements Parcelable {
        private Integer mId;
        private Date mDate;
        private List<Point> mWayPointList;
        private Point mStartPoint;

        private final static String JSON_ROUTE_ID_KEY = "id";
        private final static String JSON_ROUTE_DATE_KEY = "created";
        private final static String JSON_ROUTE_POINTS_KEY = "points";
        private final static String JSON_ROUTE_POINT_ID_KEY = "id";
        private final static String JSON_ROUTE_POINT_ADDRESS_KEY = "address";
        private final static String JSON_ROUTE_POINT_CITY_KEY = "city";
        private final static String JSON_ROUTE_POINT_LONGITUDE_KEY = "longitude";
        private final static String JSON_ROUTE_POINT_LATITUDE_KEY = "latitude";

        public final static String FORMAT_DATE_ORIGINAL = "yyyy-MM-dd'T'HH:mm:ss.SSS";
        public final static String FORMAT_DATE_FORMATTED = "H:mm d.MM.yyyy";

        public static class Point implements Parcelable {
            public Integer mId;
            public int mUniqueId;
            public String mAddress;
            public String mCity;
            public double mLat;
            public double mLng;

            public Point() {}

            private Point(Parcel in) {
                mId = in.readInt();
                mUniqueId = in.readInt();
                mAddress = in.readString();
                mCity = in.readString();
                mLat = in.readDouble();
                mLng = in.readDouble();
            }

            public int describeContents() {
                return 0;
            }

            public void writeToParcel(Parcel out, int flags) {
                out.writeInt(mId);
                out.writeInt(mUniqueId);
                out.writeString(mAddress);
                out.writeString(mCity);
                out.writeDouble(mLat);
                out.writeDouble(mLng);
            }

            public static final Parcelable.Creator<Point> CREATOR
                    = new Parcelable.Creator<Point>() {
                public Point createFromParcel(Parcel in) {
                    return new Point(in);
                }

                public Point[] newArray(int size) {
                    return new Point[size];
                }
            };
        }

        /**
         *
         */
        public static Route newInstance(JSONObject json) throws Exception {
            Integer routeId = null;
            if (json.has(JSON_ROUTE_ID_KEY)) {
                try {
                    routeId = json.getInt(JSON_ROUTE_ID_KEY);
                } catch (Exception e) {
                    throw new Exception(e.getMessage());
                }
            }

            if (routeId == null) throw new Exception("No route id");

            Date routeDate = null;
            if (json.has(JSON_ROUTE_DATE_KEY)) {
                try {
                    // 2014-12-28T19:50:40.964531Z
                    String strRouteDate = json.getString(JSON_ROUTE_DATE_KEY);

                    @SuppressLint("SimpleDateFormat")
                    SimpleDateFormat df = new SimpleDateFormat(FORMAT_DATE_ORIGINAL);
                    routeDate = df.parse(strRouteDate);
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

                        point.mUniqueId = i + 1;

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

        public Route(int id, Date date, List<Point> wayPointList) {
            mId = id;
            mDate = date;
            mWayPointList = wayPointList;
        }

        private Route(Parcel in) {
            mId = in.readInt();
            mDate = new Date(in.readLong());
            in.readList(mWayPointList, Point.class.getClassLoader());
            mStartPoint = in.readParcelable(Point.class.getClassLoader());
        }

        public int describeContents() {
            return 0;
        }

        public void writeToParcel(Parcel out, int flags) {
            out.writeInt(mId);
            out.writeLong(mDate.getTime());
            out.writeList(mWayPointList);
            out.writeParcelable(mStartPoint, flags);
        }

        public static final Parcelable.Creator<Route> CREATOR
                = new Parcelable.Creator<Route>() {
            public Route createFromParcel(Parcel in) {
                return new Route(in);
            }

            public Route[] newArray(int size) {
                return new Route[size];
            }
        };

        public Date getDate() { return mDate; }

        public String getDateFormatted(String format) {
            @SuppressLint("SimpleDateFormat")
            Format formatter = new SimpleDateFormat(format);
            return formatter.format(mDate);
        }

        public List<Point> getWayPointList() { return mWayPointList; }

        public String asJSON() {
            try {
                JSONObject jo = new JSONObject();

                if (mStartPoint != null) {
                    JSONObject joStartPoint = new JSONObject();
                    joStartPoint.put("latitude", mStartPoint.mLat);
                    joStartPoint.put("longitude", mStartPoint.mLng);

                    jo.put("start_point", joStartPoint);
                }

                JSONArray jaWayPoints = new JSONArray();
                for (Point wayPoint : getWayPointList()) {
                    JSONObject joWayPoint = new JSONObject();
                    joWayPoint.put("unique_id", wayPoint.mUniqueId);
                    joWayPoint.put("latitude", wayPoint.mLat);
                    joWayPoint.put("longitude", wayPoint.mLng);

                    jaWayPoints.put(joWayPoint);
                }

                jo.put("way_points", jaWayPoints);

                return jo.toString();
            }
            catch(Exception e) {
                Log.e(LOG_TAG, e.getMessage());
                return null;
            }
        }

        public void setStartPoint(double lat, double lng) {
            Point point = new Point();
            point.mLat = lat;
            point.mLng = lng;

            mStartPoint = point;
        }

        public Point getWayPoint(int idx) {
            try {
                return mWayPointList.get(idx);
            } catch (IndexOutOfBoundsException e) {
                return null;
            }
        }

        public Point getWayPointByUniqueId(int uniqueId) {
            for (Point point : getWayPointList()) {
                if (point.mUniqueId == uniqueId)
                    return point;
            }

            return null;
        }

        public Point getStartPoint() {
            return mStartPoint;
        }

        public boolean hasStartPoint() {
            return mStartPoint != null;
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
                        setCurrentRoute(Route.newInstance(json));

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
                            routeListFragment.redrawUIRouteList();
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
    }

    /**
     *
     */
    private class UserProfileShowHandler implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            RouteMapFragment routeMapFragment = (RouteMapFragment)mFragmentManager
                    .findFragmentByTag(FRAGMENT_ROUTE_MAP);

            FragmentTransaction fragmentTransaction = mFragmentManager.beginTransaction();

            if (routeMapFragment != null && routeMapFragment.isVisible()) {

                fragmentTransaction.hide(routeMapFragment);

                UserProfileFragment userProfileFragment = (UserProfileFragment)mFragmentManager
                        .findFragmentByTag(FRAGMENT_USER_PROFILE);

                if (userProfileFragment != null) {
                    fragmentTransaction.show(userProfileFragment);
                } else {
                    fragmentTransaction.add(R.id.fragment_container, UserProfileFragment.newInstance(), FRAGMENT_USER_PROFILE);
                }
            }
            else {
                fragmentTransaction.replace(R.id.fragment_container, UserProfileFragment.newInstance(), FRAGMENT_USER_PROFILE);
            }

            fragmentTransaction.addToBackStack(null);
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
    public void onRoutePointClick(int uniqueId) {
        final Route.Point point = getCurrentRoute().getWayPointByUniqueId(uniqueId);
        if (point == null) return;

        final FragmentTransaction fragmentTransaction = mFragmentManager.beginTransaction();
        final RouteMapFragment routeMapFragment = (RouteMapFragment)mFragmentManager
                .findFragmentByTag(FRAGMENT_ROUTE_MAP);

        if (routeMapFragment != null && routeMapFragment.isVisible()) {

            fragmentTransaction.hide(routeMapFragment);

            final BinCardFragment binCardFragment = (BinCardFragment)mFragmentManager
                    .findFragmentByTag(FRAGMENT_BIN_CARD);

            if (binCardFragment != null) {
                fragmentTransaction.show(binCardFragment);
                binCardFragment.redrawUI(point);
            } else {
                fragmentTransaction.add(R.id.fragment_container, BinCardFragment.newInstance(point), FRAGMENT_BIN_CARD);
            }
        }
        else {
            fragmentTransaction.replace(R.id.fragment_container, BinCardFragment.newInstance(point), FRAGMENT_BIN_CARD);
        }

        fragmentTransaction.addToBackStack(null);
        fragmentTransaction.commit();
    }

    /**
     *
     */
    @Override
    public void onNextRoutePoint(Route.Point currentPoint) {





        FragmentTransaction fragmentTransaction = mFragmentManager.beginTransaction();
        fragmentTransaction.replace(R.id.fragment_container, RouteMapFragment.newInstance(), FRAGMENT_ROUTE_MAP).addToBackStack(null);
        fragmentTransaction.commit();
    }

    /**
     *
     */
    @Override
    public void onRouteStart() {
        FragmentTransaction fragmentTransaction = mFragmentManager.beginTransaction();
        fragmentTransaction.replace(R.id.fragment_container, RouteMapFragment.newInstance(), FRAGMENT_ROUTE_MAP).addToBackStack(null);
        fragmentTransaction.commit();

        // TODO: Not totally correct to make this call here..
        final Route route = getCurrentRoute();
        final String strJSON = String.format("{\"action\":\"start_route\", \"route_id\":\"%s\", \"created\":\"%s\"}",
                route.getId(), route.getDateFormatted(Route.FORMAT_DATE_ORIGINAL));
        mService.sendData(strJSON);
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
