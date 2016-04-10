package company.electrobin;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import company.electrobin.i10n.I10nInitializeListener;
import company.electrobin.user.User;
import company.electrobin.user.UserProfileLoadListener;

public class MainActivity extends Activity implements UserProfileLoadListener, I10nInitializeListener {

    private Handler mHandler = new Handler();
    private ElectrobinApplication mApp;
    private User mUser;

    private Handler mPendingEventDoneHandler;
    private int mPendingEventsCount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mApp = (ElectrobinApplication)getApplicationContext();
        mUser = mApp.getUser();

        mPendingEventsCount = 0;

        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (mUser.isLoggedIn()) {
                    mPendingEventsCount++;
                    mUser.loadProfile(MainActivity.this);
                }

                mPendingEventsCount++;
                mApp.getI10n().initialize(MainActivity.this);
            }
        }, 2000);

        mPendingEventDoneHandler = new Handler(new Handler.Callback() {
            @Override
            public boolean handleMessage(Message msg) {
                if (--mPendingEventsCount > 0)
                    return false;

                startActivity(new Intent(MainActivity.this, mUser.isLoggedIn() ? RouteActivity.class : AuthActivity.class));
                return true;
            }
        });
    }

    @Override
    public void onUserProfileLoadSuccess() {
        mPendingEventDoneHandler.sendEmptyMessage(10);
    }

    @Override
    public void onUserProfileLoadError(int error) {
        mPendingEventDoneHandler.sendEmptyMessage(10);
    }

    @Override
    public void onI10nInitializeSuccess() {
        mPendingEventDoneHandler.sendEmptyMessage(10);
    }

    @Override
    public void onI10nInitializeError(int error) {
        mPendingEventDoneHandler.sendEmptyMessage(10);
    }

    @Override
    public void onBackPressed() {
        // Do nothing
    }
}
