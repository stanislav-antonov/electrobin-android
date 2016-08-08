package company.electrobin;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.location.Location;
import android.location.LocationManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.ActionBar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import android.view.Window;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.google.firebase.iid.FirebaseInstanceId;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import company.electrobin.fcm.MyFirebaseInstanceIDService;
import company.electrobin.i10n.I10n;
import company.electrobin.location.UserLocation;
import company.electrobin.network.TCPClientListener;
import company.electrobin.network.TCPClientService;
import company.electrobin.user.User;

public class RouteActivity extends AppCompatActivity implements
        RouteListFragment.OnFragmentInteractionListener,
        RouteMapFragmentWebView.OnFragmentInteractionListener,
        UserProfileFragment.OnFragmentInteractionListener,
        BinCardFragment.OnFragmentInteractionListener,
        AllBinsDoneFragment.OnFragmentInteractionListener,
        StatisticsFragment.OnFragmentInteractionListener,
        FragmentManager.OnBackStackChangedListener {

    private User mUser;
    private I10n mI10n;
    private ElectrobinApplication mApp;

    private RelativeLayout mRlRouteUpdated;
    private LinearLayout mLlBottomNotification;
    public ImageButton mBtnActionBarUserProfile;
    public TextView mTvActionBarTitle;

    private FragmentManager mFragmentManager;
    private Route mRoute;
    private UserLocation mUserLocation;
    private JsonCommand mJsonCommand;

    private Dialog mRouteUpdatedDialog;
    private Dialog mRouteInterruptDialog;

    private boolean mRouteUpdatedPopupShowing = false;

    private Fragment mCurrentFragment;

    private final static String LOG_TAG = RouteActivity.class.getSimpleName();
    private static final String BUNDLE_KEY_ROUTE = "route";

    public final static int NOTIFICATION_NO_INTERNET_CONNECTION = 1;
    public final static int NOTIFICATION_NO_GPS = 2;

    public final static String FORMAT_DATE_ORIGINAL = "yyyy-MM-dd'T'HH:mm:ss.SSS";
    public final static String FORMAT_DATE_FORMATTED = "H:mm d.MM.yyyy";

    /**
     *
     */
    public static class Route implements Parcelable {
        private Integer mId;
        private Date mDate;
        private List<Point> mWayPointList;
        private boolean mAvoidTrafficJams;
        private Point mStartPoint;
        private float mRun;

        private final static String JSON_ROUTE_ID_KEY = "id";
        private final static String JSON_ROUTE_DATE_KEY = "created";
        private final static String JSON_ROUTE_POINTS_KEY = "points";
        private final static String JSON_ROUTE_POINT_ID_KEY = "id";
        private final static String JSON_ROUTE_POINT_ADDRESS_KEY = "address";
        private final static String JSON_ROUTE_POINT_CITY_KEY = "city";
        private final static String JSON_ROUTE_POINT_LONGITUDE_KEY = "longitude";
        private final static String JSON_ROUTE_POINT_LATITUDE_KEY = "latitude";
        private final static String JSON_ROUTE_POINT_FULLNESS_KEY = "fullness";
        private final static String JSON_ROUTE_POINT_VOLUME_KEY = "volume";

        public static class Point implements Parcelable {
            public int mId;
            public int mUniqueId;
            public String mAddress;
            public String mCity;
            public double mLat;
            public double mLng;

            // TODO: Bin related fields - need refactoring
            public int mFullness;
            public int mVolume;
            public boolean mIsUnloadedOk;
            public String mComment;

            public boolean mIsVisited;

            public Point() {}

            private Point(Parcel in) {
                mId = in.readInt();
                mUniqueId = in.readInt();
                mAddress = in.readString();
                mCity = in.readString();
                mLat = in.readDouble();
                mLng = in.readDouble();

                mFullness = in.readInt();
                mVolume = in.readInt();
                mIsUnloadedOk = in.readByte() != 0;
                mComment = in.readString();

                mIsVisited =  in.readByte() != 0;
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

                out.writeInt(mFullness);
                out.writeInt(mVolume);
                out.writeByte((byte) (mIsUnloadedOk ? 1 : 0));
                out.writeString(mComment);

                out.writeByte((byte) (mIsVisited ? 1 : 0));
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
                        point.mFullness = joPoint.getInt(JSON_ROUTE_POINT_FULLNESS_KEY);
                        point.mVolume = joPoint.getInt(JSON_ROUTE_POINT_VOLUME_KEY);

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

            mWayPointList = new ArrayList<Point>();
            in.readList(mWayPointList, Point.class.getClassLoader());

            mStartPoint = in.readParcelable(Point.class.getClassLoader());
            mRun = in.readFloat();
            mAvoidTrafficJams =  in.readByte() != 0;
        }

        public int describeContents() {
            return 0;
        }

        public void writeToParcel(Parcel out, int flags) {
            out.writeInt(mId);
            out.writeLong(mDate.getTime());
            out.writeList(mWayPointList);
            out.writeParcelable(mStartPoint, flags);
            out.writeFloat(mRun);
            out.writeByte((byte) (mAvoidTrafficJams ? 1 : 0));
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

        public float getRun() { return mRun; }

        public int getRunFormatted() {
            return Math.round(getRun() / 1000F);
        }

        public boolean getAvoidTrafficJams() { return mAvoidTrafficJams; }

        public void setAvoidTrafficJams(boolean isEnabled) { mAvoidTrafficJams = isEnabled; }

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
                    // We need only unvisited points
                    if (wayPoint.mIsVisited) continue;

                    JSONObject joWayPoint = new JSONObject();
                    joWayPoint.put("unique_id", wayPoint.mUniqueId);
                    joWayPoint.put("latitude", wayPoint.mLat);
                    joWayPoint.put("longitude", wayPoint.mLng);
                    joWayPoint.put("fullness", wayPoint.mFullness);
                    joWayPoint.put("volume", wayPoint.mVolume);

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

        public void setWayPointVisited(int uniqueId) {
            final Point point = getWayPointByUniqueId(uniqueId);
            if (point == null)
                throw new IllegalArgumentException();

            point.mIsVisited = true;
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

        public boolean hasUnvisitedPoints() {
            for (Point point : getWayPointList()) {
                if (!point.mIsVisited)
                    return true;
            }

            return false;
        }

        public Point getStartPoint() {
            return mStartPoint;
        }

        public boolean hasStartPoint() {
            return mStartPoint != null;
        }

        public Integer getId() { return mId; }

        public void addRun(float run) {
            mRun += run;
        }
    }

    /**
     *
     */
    private class SocketAPIEventsHandler implements TCPClientListener {

        private final String LOG_TAG = SocketAPIEventsHandler.class.getName();

        private final static String JSON_ACTION_KEY = "action";
        private final static String JSON_ACTION_NEW_ROUTE = "new_route";
        private final static String JSON_ACTION_UPDATE_TOKEN = "update_token";

        private final static long NOTIFICATION_NO_INTERNET_CONNECTION_TIMEOUT = 10000L;

        private Handler mHandler = new Handler();
        private Runnable mRunnable = new Runnable() {
            @Override
            public void run() {
                toggleNotification(NOTIFICATION_NO_INTERNET_CONNECTION, View.VISIBLE);
            }
        };

        @Override
        public void onConnectResult(int result) {
            Log.d(LOG_TAG, "Connect result: " + result);
            if (result == TCPClientListener.CONNECT_RESULT_OK) {
                mHandler.removeCallbacks(mRunnable);
                toggleNotification(NOTIFICATION_NO_INTERNET_CONNECTION, View.GONE);

                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        mJsonCommand.sendFCMToken(FirebaseInstanceId.getInstance().getToken());
                    }
               }, 2000);
            } else {
                toggleNotification(NOTIFICATION_NO_INTERNET_CONNECTION, View.VISIBLE);
            }
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

                        RouteListFragment routeListFragment = (RouteListFragment)mFragmentManager.findFragmentByTag(RouteListFragment.FRAGMENT_TAG);
                        if (routeListFragment != null && routeListFragment.isVisible()) {
                            routeListFragment.showUIRouteList();
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

        @Override
        public void onConnectionClosed() {
            mHandler.removeCallbacks(mRunnable);
            mHandler.postDelayed(mRunnable, NOTIFICATION_NO_INTERNET_CONNECTION_TIMEOUT);
        }
    }

    /**
     *
     */
    private class JsonCommand {

        final private Format mFormatter = new SimpleDateFormat(FORMAT_DATE_ORIGINAL);

        public void routeStart() {
            final Route route = getCurrentRoute();
            final String strJSON = String.format("{\"action\":\"start_route\", \"route_id\":\"%s\", \"created\":\"%s\"}",
                    route.getId(), getTime());
            mService.sendData(strJSON);
        }

        public void routeComplete() {
            final Route route = getCurrentRoute();
            final String strJSON = String.format("{\"action\":\"route_complete\", \"track\":\"%s\", \"route_id\":\"%s\", \"created\":\"%s\"}",
                    route.getRun(), route.getId(), getTime());
            mService.sendData(strJSON);
        }

        public void routeInterrupt() {
            final Route route = getCurrentRoute();
            final String strJSON = String.format("{\"action\":\"route_stop\", \"route_id\":\"%s\", \"created\":\"%s\"}",
                    route.getId(), getTime());
            mService.sendData(strJSON);
        }

        public void allBinsDone() {
            final Route route = getCurrentRoute();
            final String strJSON = String.format("{\"action\":\"moving_home\", \"track\":\"%s\", \"route_id\":\"%s\", \"created\":\"%s\"}",
                    route.getRunFormatted(), route.getId(), getTime());
            mService.sendData(strJSON);
        }

        public void binCollect(Route.Point point) {
            final Route route = getCurrentRoute();
            final String strJSON = String.format("{\"action\":\"collection\", \"route_id\":\"%s\", \"container_id\":\"%s\", \"comment\":\"%s\", \"created\":\"%s\", \"latitude\":\"%s\", \"longitude\":\"%s\", \"fullness\":\"%s\"}",
                    route.mId, point.mId, point.mComment, getTime(), point.mLat, point.mLng, point.mFullness);
            mService.sendData(strJSON);
        }

        public void sendFCMToken(String token) {
            final String strJSON = String.format("{\"action\":\"update_token\", \"token\":\"%s\"}", token);
            mService.sendData(strJSON);
        }

        private String getTime() {
            return mFormatter.format(new Date());
        }
    }

    private TCPClientService mService;
    private boolean mBound = false;

    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            TCPClientService.TCPClientServiceBinder binder = (TCPClientService.TCPClientServiceBinder)service;
            mService = binder.getService();
            mService.setListener(new SocketAPIEventsHandler());
            mBound = true;

            startService(new Intent(RouteActivity.this, TCPClientService.class));
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
        }
    };

    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch(intent.getAction()) {
                case UserLocation.BROADCAST_INTENT_LOCATION_CHANGED: {
                    final Bundle bundle = intent.getExtras();
                    if (bundle == null) return;

                    final Location currentLocation = bundle.getParcelable(UserLocation.BUNDLE_KEY_CURRENT_LOCATION);
                    final Location prevLocation = bundle.getParcelable(UserLocation.BUNDLE_KEY_PREV_LOCATION);

                    if (currentLocation != null && prevLocation != null
                            && currentLocation.getProvider().equals(LocationManager.GPS_PROVIDER)
                            && prevLocation.getProvider().equals(LocationManager.GPS_PROVIDER))
                    {
                        mRoute.addRun(currentLocation.distanceTo(prevLocation));
                    }

                    break;
                }

                case UserLocation.BROADCAST_INTENT_GPS_STATUS: {
                    final Bundle bundle = intent.getExtras();
                    if (bundle == null) return;
                    toggleNotification(NOTIFICATION_NO_GPS, bundle.getBoolean(UserLocation.BUNDLE_KEY_IS_GPS_AVAILABLE) ? View.GONE : View.VISIBLE);

                    break;
                }

                case MyFirebaseInstanceIDService.BROADCAST_INTENT_TOKEN_REFRESH: {
                    final Bundle bundle = intent.getExtras();
                    if (bundle == null) return;

                    String token = bundle.getString(MyFirebaseInstanceIDService.BUNDLE_KEY_TOKEN);
                    mJsonCommand.sendFCMToken(token);

                    break;
                }
            }
        }
    };

    /**
     *
     */
    private void setCurrentRoute(Route route) {
        mRoute = route;
    }

    /**
     *
     * @return
     */
    public Route getCurrentRoute() {
        return mRoute;
    }

    /**
     *
     * @return
     */
    public UserLocation getUserLocation() {
        return mUserLocation;
    }

    /**
     *
     * @param fragmentClass
     * @return
     */
    private Fragment switchToFragment(Class fragmentClass, boolean toBackStack) {
        final FragmentTransaction fragmentTransaction = mFragmentManager.beginTransaction();

        // Hide all fragments
        List<Fragment> fragmentList = mFragmentManager.getFragments();
        if (fragmentList != null) {
            for (Fragment fragment : fragmentList) {
                if (fragment != null && fragment.isVisible())
                    fragmentTransaction.hide(fragment);
            }
        }

        String toFragmentTag;
        try {
            toFragmentTag = (String)fragmentClass.getDeclaredField("FRAGMENT_TAG").get(null);
        } catch(Exception e) {
            Log.e(LOG_TAG, e.getMessage());
            return null;
        }

        Fragment toFragment = mFragmentManager.findFragmentByTag(toFragmentTag);
        if (toFragment != null) {
            mFragmentManager.popBackStack();

            if (toFragment.isDetached())
                fragmentTransaction.attach(toFragment);

            if (toFragment.isHidden())
                fragmentTransaction.show(toFragment);
        } else {
            try {
                Method newInstanceMethod = fragmentClass.getMethod("newInstance", null);
                toFragment = (Fragment) newInstanceMethod.invoke(null, null);
            } catch (Exception e) {
                Log.e(LOG_TAG, e.getMessage());
                return null;
            }

            fragmentTransaction.add(R.id.fragment_container, toFragment, toFragmentTag);
        }

        if (toBackStack) fragmentTransaction.addToBackStack(toFragmentTag);
        fragmentTransaction.commit();

        return toFragment;
    }

    /**
     *
     * @param fragmentClass
     * @return
     */
    private Fragment replaceToFragment(Class fragmentClass) {
        return replaceToFragment(fragmentClass, null);
    }

    /**
     *
     * @param fragmentClass
     * @return
     */
    private Fragment replaceToFragment(Class fragmentClass, Object args) {
        final String toFragmentTag;
        try {
            toFragmentTag = (String)fragmentClass.getDeclaredField("FRAGMENT_TAG").get(null);
        } catch(Exception e) {
            Log.e(LOG_TAG, e.getMessage());
            return null;
        }

        final Fragment toFragment;
        try {
            Method newInstanceMethod;
            if (args != null) {
                newInstanceMethod = fragmentClass.getMethod("newInstance", Object.class);
                toFragment = (Fragment) newInstanceMethod.invoke(null, args);
            } else {
                newInstanceMethod = fragmentClass.getMethod("newInstance", null);
                toFragment = (Fragment) newInstanceMethod.invoke(null, null);
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, e.getMessage());
            return null;
        }

        try {
            // mFragmentManager.beginTransaction().replace(R.id.fragment_container, toFragment, toFragmentTag).commitAllowingStateLoss();
            mFragmentManager.beginTransaction().replace(R.id.fragment_container, toFragment, toFragmentTag).commit();
            // mFragmentManager.executePendingTransactions();
            mFragmentManager.popBackStack();
        } catch (Exception e) {
            Log.e(LOG_TAG, e.getMessage());
            return null;
        }

        mCurrentFragment = toFragment;

        return toFragment;
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

        mUserLocation = new UserLocation(this);
        mJsonCommand = new JsonCommand();

        setupCustomActionBar();

        mRlRouteUpdated = (RelativeLayout)findViewById(R.id.route_updated_layout);
        mRlRouteUpdated.setVisibility(View.GONE);

        mLlBottomNotification = (LinearLayout)findViewById(R.id.bottom_notification_layout);

        ((TextView)mRlRouteUpdated.findViewById(R.id.route_updated_text)).setText(mI10n.l("route_updated"));

        mFragmentManager = getSupportFragmentManager();
        mFragmentManager.addOnBackStackChangedListener(this);

        shouldDisplayHomeUp();

        if (savedInstanceState != null) {
            mRoute = savedInstanceState.getParcelable(BUNDLE_KEY_ROUTE);
            mCurrentFragment = mFragmentManager.getFragment(savedInstanceState, "currentFragment");
            replaceToFragment(mCurrentFragment.getClass());
        }
        else {
            Bundle params = getIntent().getExtras();
            if (params != null) {
                mRoute = params.getParcelable(BUNDLE_KEY_ROUTE);
                replaceToFragment(RouteListFragment.class, RouteListFragment.LAYOUT_DISPLAYED_ROUTE_LIST);
            }
            else {
                replaceToFragment(RouteListFragment.class);
            }
        }
    }

    /**
     *
     */
    public void toggleNotification(int what, int visibility) {
        final TextView tvWhat;
        switch(what) {
            case NOTIFICATION_NO_INTERNET_CONNECTION:
                tvWhat = (TextView)mLlBottomNotification.findViewById(R.id.no_internet_connection_text);
                tvWhat.setText(mI10n.l("no_internet_connection"));
                break;
            case NOTIFICATION_NO_GPS:
                tvWhat = (TextView)mLlBottomNotification.findViewById(R.id.no_gps_text);
                tvWhat.setText(mI10n.l("no_gps"));
                break;
            default:
                return;
        }

        tvWhat.setVisibility(visibility);
    }

    /**
     *
     */
    @Override
    protected void onSaveInstanceState(Bundle bundle) {
        super.onSaveInstanceState(bundle);
        if (mRoute != null)
            bundle.putParcelable(BUNDLE_KEY_ROUTE, mRoute);
        mFragmentManager.putFragment(bundle, "currentFragment", mCurrentFragment);
    }

    /**
     *
     * @param bundle
     */
    @Override
    protected void onRestoreInstanceState(Bundle bundle) {
        super.onRestoreInstanceState(bundle);
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
    protected void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mBroadcastReceiver);
    }

    /**
     *
     */
    @Override
    protected void onResume() {
        super.onResume();

        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(UserLocation.BROADCAST_INTENT_LOCATION_CHANGED);
        intentFilter.addAction(UserLocation.BROADCAST_INTENT_GPS_STATUS);

        LocalBroadcastManager.getInstance(this).registerReceiver(mBroadcastReceiver, intentFilter);
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
        // getMenuInflater().inflate(R.menu.menu_route, menu);
        return false;
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
        if (isOnRouteList)
            showRouteUpdatedPopup();
        else
            showRouteUpdatedDialog();
    }

    /**
     *
     */
    private void showRouteUpdatedPopup() {
        if (mRouteUpdatedPopupShowing) return;

        final ObjectAnimator fadeIn = ObjectAnimator.ofFloat(mRlRouteUpdated, "alpha", 0.0f, 0.95f);
        fadeIn.setDuration(1000);

        final ObjectAnimator fadeOut = ObjectAnimator.ofFloat(mRlRouteUpdated, "alpha", 0.95f, 0.0f);
        fadeOut.setDuration(1000);

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
                mRouteUpdatedPopupShowing = false;
            }
        });

        as.start();
        mRouteUpdatedPopupShowing = true;
    }

    /**
     *
     */
    private void showRouteUpdatedDialog() {
        if (mRouteUpdatedDialog != null && mRouteUpdatedDialog.isShowing())
            return;

        mRouteUpdatedDialog = new Dialog(this, R.style.CustomDialog);
        mRouteUpdatedDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        mRouteUpdatedDialog.setCancelable(false);
        mRouteUpdatedDialog.setContentView(R.layout.layout_custom_dialog_1);

        final TextView tvMessage1 = (TextView) mRouteUpdatedDialog.findViewById(R.id.message_text_1);
        tvMessage1.setText(mI10n.l("attention"));

        final TextView tvMessage2 = (TextView) mRouteUpdatedDialog.findViewById(R.id.message_text_2);
        tvMessage2.setText(mI10n.l("route_updated"));

        final Button btnOk = (Button) mRouteUpdatedDialog.findViewById(R.id.ok_button);
        btnOk.setText(mI10n.l("to_route_list"));
        btnOk.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mRouteUpdatedDialog.dismiss();
                replaceToFragment(RouteListFragment.class, RouteListFragment.LAYOUT_DISPLAYED_ROUTE_LIST);
            }
        });

        mRouteUpdatedDialog.show();
    }

    /**
     *
     */
    private void showRouteInterruptDialog() {
        if (mRouteInterruptDialog != null && mRouteInterruptDialog.isShowing())
            return;

        mRouteInterruptDialog = new Dialog(this, R.style.CustomDialog);
        mRouteInterruptDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        mRouteInterruptDialog.setCancelable(true);
        mRouteInterruptDialog.setContentView(R.layout.layout_custom_dialog_2);

        final TextView tvMessage = (TextView) mRouteInterruptDialog.findViewById(R.id.message_text);
        tvMessage.setText(mI10n.l("route_interrupt_confirm"));

        final Button btnOk = (Button) mRouteInterruptDialog.findViewById(R.id.ok_button);
        btnOk.setText(mI10n.l("ok"));
        btnOk.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mRouteInterruptDialog.dismiss();
                replaceToFragment(RouteListFragment.class);
                mJsonCommand.routeInterrupt();
            }
        });

        final Button btnCancel = (Button) mRouteInterruptDialog.findViewById(R.id.cancel_button);
        btnCancel.setText(mI10n.l("cancel"));
        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mRouteInterruptDialog.dismiss();
            }
        });

        mRouteInterruptDialog.show();
    }

    /**
     *
     */
    private void setupCustomActionBar() {
        final ActionBar actionBar = getSupportActionBar();
        if (actionBar == null) return;

        final ViewGroup actionBarLayout = (ViewGroup)getLayoutInflater().inflate(
                R.layout.action_bar_layout,
                null);

        actionBar.setDisplayShowHomeEnabled(false);
        actionBar.setDisplayShowTitleEnabled(false);
        actionBar.setDisplayShowCustomEnabled(true);
        actionBar.setCustomView(actionBarLayout);

        // Remove the shadow
        actionBar.setElevation(0);

        mTvActionBarTitle = (TextView)findViewById(R.id.action_bar_title_text);

        mBtnActionBarUserProfile = (ImageButton)findViewById(R.id.action_bar_user_profile_button);
        mBtnActionBarUserProfile.setVisibility(View.VISIBLE);
        mBtnActionBarUserProfile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchToFragment(UserProfileFragment.class, true);
            }
        });
    }

    /**
     *
     */
    @Override
    public void onRouteInterrupt() {
        showRouteInterruptDialog();
    }

    /**
     *
     */
    @Override
    public boolean onGetInternetConnectionStatus() {
        return mBound && mService.isConnected();
    }

    /**
     *
     */
    @Override
    public boolean onGetGPSStatus() {
        return mUserLocation.isGPSEnabled();
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

        final BinCardFragment binCardFragment = (BinCardFragment)switchToFragment(BinCardFragment.class, true);
        binCardFragment.setRoutePoint(point);
    }

    /**
     *
     */
    @Override
    public void onRoutePointDone(Route.Point point) {
        final Route route = getCurrentRoute();

        try {
            route.setWayPointVisited(point.mUniqueId);
        } catch (Exception e) {
            Log.e(LOG_TAG, e.getMessage());
            return;
        }

        mUserLocation.stopLocationUpdates();
        mUserLocation.startLocationUpdates();

        mJsonCommand.binCollect(point);

        List<Fragment> fragmentList = mFragmentManager.getFragments();
        if (fragmentList != null) {
            for (Fragment fragment : fragmentList) {
                if (fragment != null && fragment.isHidden() && fragment.getTag().equals(RouteMapFragmentWebView.FRAGMENT_TAG)) {
                    fragment.onDestroy();
                }
            }
        }

        if (route.hasUnvisitedPoints()) {
            replaceToFragment(RouteMapFragmentWebView.class);
        }
        else {
            mJsonCommand.allBinsDone();
            replaceToFragment(AllBinsDoneFragment.class);
        }
    }

    /**
     *
     */
    @Override
    public void onRouteDone() {
        mJsonCommand.routeComplete();
        mUserLocation.stopLocationUpdates();

        replaceToFragment(StatisticsFragment.class);
    }

    /**
     *
     */
    @Override
    public void onGetNewRoute() {
        replaceToFragment(RouteListFragment.class);
    }

    /**
     *
     */
    @Override
    public void onRouteStart() {
        replaceToFragment(RouteMapFragmentWebView.class);

        mUserLocation.stopLocationUpdates();
        mUserLocation.startLocationUpdates();

        mJsonCommand.routeStart();
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
    public void shouldDisplayHomeUp() {
        final ActionBar actionBar = getSupportActionBar();
        if (actionBar != null)
            actionBar.setDisplayHomeAsUpEnabled(mFragmentManager.getBackStackEntryCount() > 0);
    }

    /**
     *
     * @return
     */
    @Override
    public boolean onSupportNavigateUp() {
        mFragmentManager.popBackStack();
        return true;
    }

    /**
     *
     */
    @Override
    public void onBackPressed() {
        if (mFragmentManager.getBackStackEntryCount() > 0)
            // Also works:
            // super.onBackPressed();
            onSupportNavigateUp();
    }

    /**
     *
     * @param title
     */
    public void setActionBarTitle(String title) {
        if (mTvActionBarTitle != null) mTvActionBarTitle.setText(title);
    }
}
