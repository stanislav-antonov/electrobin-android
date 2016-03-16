package company.electrobin;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;

import company.electrobin.i10n.I10nInitializeListener;
import company.electrobin.user.User;

public class MainActivity extends AppCompatActivity {

    private Handler mHandler = new Handler();
    private ElectrobinApplication mApp;
    private User mUser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mApp = (ElectrobinApplication)getApplicationContext();
        mUser = mApp.getUser();

        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                final Intent intent = new Intent(MainActivity.this,
                    mUser.isLoggedIn() ? RouteActivity.class : AuthActivity.class);

                mApp.getI10n().initialize(new I10nInitializeListener() {
                    @Override
                    public void onInitializeSuccess() {
                        startActivity(intent);
                    }

                    @Override
                    public void onInitializeError(int error) {
                        startActivity(intent);
                    }
                });
            }
        }, 2000);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
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

        return super.onOptionsItemSelected(item);
    }
}
