package company.electrobin;

import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;

import java.io.IOException;
import java.io.InputStream;

import company.electrobin.user.User;

public class RouteActivity extends AppCompatActivity {

    private User mUser;
    private Button mBtn;
    private WebView mVwMap;

    private final static String LOG_TAG = RouteActivity.class.getSimpleName();

    private class MyLocationListener implements LocationListener {

        private Location mCurrentLocation;

        @Override
        public synchronized void onLocationChanged(Location location) {

            double lat = location.getLatitude();
            double lng = location.getLongitude();

            double accuracy = location.getAccuracy();
            String provider = location.getProvider();

            Log.d(LOG_TAG, String.format("coords: [%1$s, %2$s], provider: %3$s, accuracy: %4$s",
                    lat, lng, provider, accuracy));

            String strJs = String.format("javascript:updatePosition(%1$s, %2$s)", lat, lng);
            mVwMap.loadUrl(strJs);
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {}

        @Override
        public void onProviderEnabled(String provider) {}

        @Override
        public void onProviderDisabled(String provider) {}
    }

    private class MyWebViewClient extends WebViewClient {
        @Override
        public void onPageFinished(WebView view, String url){
            LocationManager locationManager = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
            MyLocationListener locationListener = new MyLocationListener();

            try {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000L, 0F, locationListener);
            } catch(IllegalArgumentException e) {
                Log.e(LOG_TAG, "Network provider error: " + e.getMessage());
            }

            try {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0F, locationListener);
            } catch (IllegalArgumentException e) {
                Log.e(LOG_TAG, "GPS provider error: " + e.getMessage());
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_route);

        mUser = ((ElectrobinApplication)getApplicationContext()).getUser();
        mBtn = (Button)findViewById(R.id.jump_button);

        mVwMap = (WebView)findViewById(R.id.map);
        WebSettings webSettings = mVwMap.getSettings();
        webSettings.setJavaScriptEnabled(true);

        try {
            InputStream is = getAssets().open("map.html");
            byte[] buffer = new byte[is.available()];
            is.read(buffer);
            is.close();

            String htmlText = new String(buffer);
            mVwMap.loadDataWithBaseURL(
                    "http://ru.yandex.api.yandexmapswebviewexample.ymapapp",
                    htmlText,
                    "text/html",
                    "UTF-8",
                    null
            );
        }
        catch (IOException e) {
            e.printStackTrace();
        }

        mVwMap.setWebViewClient(new MyWebViewClient());
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
}
