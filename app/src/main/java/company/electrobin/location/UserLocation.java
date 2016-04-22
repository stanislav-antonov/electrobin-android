package company.electrobin.location;

import android.content.Context;
import android.content.Intent;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.Toast;

/**
 *
 */
public class UserLocation {

    private LocationManager mLocationManager;
    private UserLocationListener mLocationListener;
    private GpsStatusChecker mGpsStatusChecker;

    private boolean mIsRunning;
    private Context mContext;

    private static final long GPS_LOCATION_UPDATES_MIN_TIME_INTERVAL = 1000L;
    private static final long GPS_LOCATION_UPDATES_MIN_DISTANCE = 10L;

    private static final long NETWORK_LOCATION_UPDATES_MIN_TIME_INTERVAL = 5000L;
    private static final long NETWORK_LOCATION_UPDATES_MIN_DISTANCE = 10L;

    private static final long GPS_STATUS_CHECKER_UPDATES_MIN_TIME_INTERVAL = 5000L;
    private static final long GPS_STATUS_CHECKER_UPDATES_MIN_DISTANCE = 0L;

    private static final String LOG_TAG = UserLocation.class.getSimpleName();

    public static final String BUNDLE_KEY_CURRENT_LOCATION = "current_location";
    public static final String BUNDLE_KEY_PREV_LOCATION = "prev_location";

    public static final String BUNDLE_KEY_IS_GPS_AVAILABLE = "is_gps_available";

    public static final String BROADCAST_INTENT_LOCATION_CHANGED = "location_changed";
    public static final String BROADCAST_INTENT_GPS_STATUS = "gps_status";

    /**
     *
     */
    private class GpsStatusChecker implements LocationListener {

        private volatile Handler mHandler = new Handler();
        private volatile Runnable mRunnable;

        private static final int TIME_INTERVAL = 15000;

        public GpsStatusChecker() {
            startTimer();
        }

        private void startTimer() {
            if (mRunnable != null) {
                mHandler.removeCallbacks(mRunnable);
                mRunnable = null;
            }

            mRunnable = new Runnable() {
                @Override
                public void run() {
                    broadcastGpsStatus(false);
                    mHandler.postDelayed(this, TIME_INTERVAL);
                }
            };

            mHandler.postDelayed(mRunnable, TIME_INTERVAL);
        }

        @Override
        public synchronized void onLocationChanged(Location location) {
            if (!location.getProvider().equals(LocationManager.GPS_PROVIDER)) return;
            startTimer();
            broadcastGpsStatus(true);
        }

        private void broadcastGpsStatus(boolean isGpsAvailable) {
            Intent intent = new Intent(BROADCAST_INTENT_GPS_STATUS);
            intent.putExtra(BUNDLE_KEY_IS_GPS_AVAILABLE, isGpsAvailable);
            LocalBroadcastManager.getInstance(mContext).sendBroadcast(intent);
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {}

        @Override
        public void onProviderEnabled(String provider) {}

        @Override
        public void onProviderDisabled(String provider) {}
    }

    /**
     *
     */
    private class UserLocationListener implements LocationListener {

        private Location mCurrentLocation;
        private static final int LOCATION_EXPIRES_TIME_INTERVAL = 1000 * 60 * 2;

        @Override
        public synchronized void onLocationChanged(Location location) {
            if (!isBetterLocation(location, mCurrentLocation))
                return;

            if (mCurrentLocation != null)
                location.setBearing(mCurrentLocation.bearingTo(location));

            broadcastLocation(location, mCurrentLocation);
            mCurrentLocation = location;
        }

        private void broadcastLocation(Location currentLocation, Location prevLocation) {
            Intent intent = new Intent(BROADCAST_INTENT_LOCATION_CHANGED);
            intent.putExtra(BUNDLE_KEY_CURRENT_LOCATION, currentLocation);
            intent.putExtra(BUNDLE_KEY_PREV_LOCATION, prevLocation);
            LocalBroadcastManager.getInstance(mContext).sendBroadcast(intent);
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
    private class GpsStatusListener implements GpsStatus.Listener {
        @Override
        public void onGpsStatusChanged(int event) {
            if (event == GpsStatus.GPS_EVENT_FIRST_FIX) {
                // Got first fix since GPS starting
            }
        }
    }

    public UserLocation(Context context) {
        mContext = context;
        mLocationManager = (LocationManager)mContext.getSystemService(Context.LOCATION_SERVICE);
        mLocationManager.addGpsStatusListener(new GpsStatusListener());
    }

    public void startLocationUpdates() {
        if (mIsRunning) {
            Log.i(LOG_TAG, "Location already running");
            return;
        }

        mLocationListener = new UserLocationListener();

        try {
            mLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,
                    NETWORK_LOCATION_UPDATES_MIN_TIME_INTERVAL, NETWORK_LOCATION_UPDATES_MIN_DISTANCE, mLocationListener);
        } catch(IllegalArgumentException e) {
            Log.e(LOG_TAG, "Network provider error: " + e.getMessage());
        }

        try {
            mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                    GPS_LOCATION_UPDATES_MIN_TIME_INTERVAL, GPS_LOCATION_UPDATES_MIN_DISTANCE, mLocationListener);
        } catch (IllegalArgumentException e) {
            Log.e(LOG_TAG, "GPS provider error: " + e.getMessage());
        }

        mGpsStatusChecker = new GpsStatusChecker();

        try {
            mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                    GPS_STATUS_CHECKER_UPDATES_MIN_TIME_INTERVAL, GPS_STATUS_CHECKER_UPDATES_MIN_DISTANCE, mGpsStatusChecker);
        } catch (IllegalArgumentException e) {
            Log.e(LOG_TAG, "GPS provider error: " + e.getMessage());
        }

        mIsRunning = true;
    }

    public void stopLocationUpdates() {
        if (mLocationListener != null)
            mLocationManager.removeUpdates(mLocationListener);

        if (mGpsStatusChecker != null)
            mLocationManager.removeUpdates(mGpsStatusChecker);

        mIsRunning = false;
    }
}
