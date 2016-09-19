package company.electrobin.common.route;

import android.annotation.SuppressLint;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import com.google.gson.Gson;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import company.electrobin.RouteActivity;
import company.electrobin.common.Serializable;

import static company.electrobin.RouteActivity.FORMAT_DATE_ORIGINAL;

public class Route implements Parcelable, Serializable {
    public Integer mId;

    private Date mDate;
    private float mRun;
    private boolean mAvoidTrafficJams;

    private int mState;

    private Point mStartPoint;
    private List<Point> mWayPointList;

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

    public final static int ROUTE_STATE_INITIAL = 1;
    public final static int ROUTE_STATE_STARTED = 2;
    public final static int ROUTE_STATE_MOVING  = 3;

    private final static String LOG_TAG = RouteActivity.class.getSimpleName();

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

        public static Point create(JSONObject joPoint) throws Exception {
            Point point = new Point();

            point.mId = joPoint.getInt(JSON_ROUTE_POINT_ID_KEY);
            point.mAddress = joPoint.getString(JSON_ROUTE_POINT_ADDRESS_KEY);
            point.mCity = joPoint.getString(JSON_ROUTE_POINT_CITY_KEY);
            point.mLng = joPoint.getDouble(JSON_ROUTE_POINT_LONGITUDE_KEY);
            point.mLat = joPoint.getDouble(JSON_ROUTE_POINT_LATITUDE_KEY);
            point.mFullness = joPoint.getInt(JSON_ROUTE_POINT_FULLNESS_KEY);
            point.mVolume = joPoint.getInt(JSON_ROUTE_POINT_VOLUME_KEY);

            return point;
        }
    }

    /**
     *
     * @param serialized
     * @return
     */
    public static Route create(String serialized) {
        Gson gson = new Gson();
        return gson.fromJson(serialized, Route.class);
    }

    /**
     *
     */
    public static Route create(JSONObject json) throws Exception {
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

        List<Route.Point> pointList = new ArrayList<>();
        if (json.has(JSON_ROUTE_POINTS_KEY)) {
            try {
                JSONArray jaPointList = json.getJSONArray(JSON_ROUTE_POINTS_KEY);
                for (int i = 0; i < jaPointList.length(); i++) {
                    Route.Point point = Route.Point.create(jaPointList.getJSONObject(i));
                    point.mUniqueId = i + 1;
                    pointList.add(point);
                }
            }
            catch (Exception e) {
                pointList.clear();
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
        mAvoidTrafficJams = in.readByte() != 0;
        // mIsStarted = in.readByte() != 0;
        mState = in.readInt();
    }

    public void update(JSONObject json) throws Exception {
        // {"action": "new_route", "points": [{"city": "\u041b\u0435\u0441\u043d\u043e\u0439 \u0433\u043e\u0440\u043e\u0434\u043e\u043a", "title": "XX-8888-AA 123", "country": "\u0420\u043e\u0441\u0441\u0438\u044f", "longitude": 37.199621195322315, "volume": 100, "fullness": 76, "address": "\u0421\u0442\u0443\u0434\u0435\u043d\u0447\u0435\u0441\u043a\u0430\u044f \u0434.2", "latitude": 55.641236622653075, "id": 36}, {"city": "\u041c\u043e\u0441\u043a\u0432\u0430", "title": "MSA-351675", "country": "\u0420\u043e\u0441\u0441\u0438\u044f", "longitude": 37.38555, "volume": 100, "fullness": 60, "address": "\u0420\u0443\u043c\u044f\u043d\u0446\u0435\u0432\u0441\u043a\u0430\u044f 20", "latitude": 55.64583, "id": 44}, {"city": "\u041c\u043e\u0441\u043a\u0432\u0430", "title": "MSA-35167513", "country": "\u0420\u043e\u0441\u0441\u0438\u044f", "longitude": 37.66982316447123, "volume": 100, "fullness": 97, "address": "\u042d\u043b\u0435\u0432\u0430\u0442\u043e\u0440\u043d\u0430\u044f \u0443\u043b. \u0434.1\u043a3", "latitude": 55.60211310127687, "id": 26}, {"city": "\u041a\u0440\u0430\u0441\u043d\u043e\u0437\u043d\u0430\u043c\u0435\u043d\u0441\u043a", "title": "XX-8888-AA", "country": "\u0420\u043e\u0441\u0441\u0438\u044f", "longitude": 37.032079691417934, "volume": 100, "fullness": 52, "address": "\u0443\u043b.\u041f\u043e\u0431\u0435\u0434\u044b, 23", "latitude": 55.59071765188984, "id": 35}, {"city": "\u041c\u043e\u0441\u043a\u0432\u0430", "title": "44444444", "country": "\u0420\u043e\u0441\u0441\u0438\u044f", "longitude": 37.667698854931054, "volume": 100, "fullness": 57, "address": "\u041c\u0430\u043b\u0430\u0445\u0438\u0442\u043e\u0432\u0430\u044f \u0443\u043b, \u0434.21", "latitude": 55.82938843126003, "id": 42}, {"city": "\u041c\u043e\u0441\u043a\u0432\u0430", "title": "AAA-123", "country": "\u0420\u043e\u0441\u0441\u0438\u044f", "longitude": 37.611216897496696, "volume": 100, "fullness": 78, "address": "\u041c\u043e\u0445\u043e\u0432\u0430\u044f \u0443\u043b.4/7 \u04412", "latitude": 55.75305392060291, "id": 41}, {"city": "\u041c\u043e\u0441\u043a\u0432\u0430", "title": "MSA-351675+SLAVE_2", "country": "\u0420\u043e\u0441\u0441\u0438\u044f", "longitude": 37.38597571330045, "volume": 100, "fullness": 43, "address": "\u041f\u0440\u043e\u0438\u0437\u0432\u043e\u0434\u0441\u0442\u0432\u0435\u043d\u043d\u0430\u044f \u0443\u043b.\u0434.6 \u044132", "latitude": 55.64548651148923, "id": 25}, {"city": "\u041b\u0435\u0441\u043d\u043e\u0439 \u0433\u043e\u0440\u043e\u0434\u043e\u043a", "title": "Test_Den", "country": "\u0420\u043e\u0441\u0441\u0438\u044f", "longitude": 37.553930277355484, "volume": 100, "fullness": 0, "address": "kjljk", "latitude": 55.629389964847256, "id": 40}], "id": 780, "created": "2016-09-19T15:08:00.404142+00:00"}
        Integer routeId = null;
        if (json.has(JSON_ROUTE_ID_KEY)) {
            try {
                routeId = json.getInt(JSON_ROUTE_ID_KEY);
            } catch (Exception e) {
                throw new Exception(e.getMessage());
            }

            if (mId != null && !mId.equals(routeId))
                throw new IllegalStateException("Unexpected route id");
        }

        if (routeId == null) throw new Exception("No route id");

        if (json.has(JSON_ROUTE_POINTS_KEY) && !mWayPointList.isEmpty()) {
            try {
                JSONArray jaPointList = json.getJSONArray(JSON_ROUTE_POINTS_KEY);
                for (int i = 0; i < jaPointList.length(); i++) {
                    Route.Point tmpPoint;
                    try {
                        tmpPoint = Route.Point.create(jaPointList.getJSONObject(i));
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "Failed to update point: " + e.getMessage());
                        continue;
                    }

                    for (int j = 0; j < mWayPointList.size(); j++) {
                        final Route.Point point = mWayPointList.get(j);
                        if (point.mId == tmpPoint.mId) {
                            point.mAddress = tmpPoint.mAddress;
                            point.mCity = tmpPoint.mCity;
                            point.mLng = tmpPoint.mLng;
                            point.mLat = tmpPoint.mLat;
                            point.mFullness = tmpPoint.mFullness;
                            point.mVolume = tmpPoint.mVolume;

                            break;
                        }
                    }
                }
            }
            catch (Exception e) {
                throw new Exception(e.getMessage());
            }
        }
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
        // out.writeByte((byte) (mIsStarted ? 1 : 0));
        out.writeInt(mState);
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

    public void setState(int state) {
        mState = state;
    }

    public int getState() {
        return mState;
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

    public String serialize() {
        Gson gson = new Gson();
        return gson.toJson(this);
    }
}