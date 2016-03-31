package company.electrobin;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.Toast;

import company.electrobin.i10n.I10n;
import company.electrobin.user.User;
import company.electrobin.user.UserAuthListener;
import company.electrobin.user.UserProfileLoadListener;

public class AuthActivity extends AppCompatActivity {

    private ElectrobinApplication mApp;
    private User mUser;
    private I10n mI10n;

    private EditText mEtUsername;
    private EditText mEtPassword;
    private Button mSignInBtn;

    private RelativeLayout mRlLoading;
    private RelativeLayout mRlMain;

    private final static String LOG_TAG = AuthActivity.class.getSimpleName();

    private class SignInActionHandler implements View.OnClickListener, UserAuthListener, UserProfileLoadListener {
        @Override
        public void onClick(View v) {
            String username = mEtUsername.getText().toString().trim();
            String password = mEtPassword.getText().toString().trim();

            if (username.isEmpty()) {
                Toast.makeText(getBaseContext(), mI10n.l("username_empty"), Toast.LENGTH_LONG).show();
                return;
            }

            if (password.isEmpty()) {
                Toast.makeText(getBaseContext(), mI10n.l("password_empty"), Toast.LENGTH_LONG).show();
                return;
            }

            mSignInBtn.setEnabled(false);
            mEtUsername.setEnabled(false);
            mEtPassword.setEnabled(false);

            mRlLoading.setVisibility(View.VISIBLE);
            mRlMain.setVisibility(View.GONE);

            mUser.auth(username, password, SignInActionHandler.this);
        }

        @Override
        public void onAuthSuccess() {
            mUser.loadProfile(this);
        }

        @Override
        public void onAuthError(int error) {
            mSignInBtn.setEnabled(true);
            mEtUsername.setEnabled(true);
            mEtPassword.setEnabled(true);

            mRlLoading.setVisibility(View.GONE);
            mRlMain.setVisibility(View.VISIBLE);

            String strMessage = mI10n.l("error_common");
            if (error == UserAuthListener.ERROR_INVALID_AUTH_CREDENTIALS) {
                strMessage = mI10n.l("username_or_password_wrong");
                mEtPassword.getText().clear();
            }

            Toast.makeText(getBaseContext(), strMessage, Toast.LENGTH_LONG).show();
        }

        @Override
        public void onUserProfileLoadSuccess() {
            mRlLoading.setVisibility(View.GONE);

            Log.d(LOG_TAG, "Successfully authenticated, auth token is " + mUser.getAuthToken());
            startActivity(new Intent(AuthActivity.this, RouteActivity.class));
        }

        @Override
        public void onUserProfileLoadError(int error) {
            mSignInBtn.setEnabled(true);
            mEtUsername.setEnabled(true);
            mEtPassword.setEnabled(true);

            mRlLoading.setVisibility(View.GONE);
            mRlMain.setVisibility(View.VISIBLE);

            mUser.logOut();

            Toast.makeText(getBaseContext(), mI10n.l("error_common"), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auth);

        mApp = (ElectrobinApplication)getApplicationContext();
        mUser = mApp.getUser();
        mI10n = mApp.getI10n();

        mEtUsername = (EditText)findViewById(R.id.username_input);
        mEtPassword = (EditText)findViewById(R.id.password_input);
        mSignInBtn = (Button)findViewById(R.id.signin_button);

        mRlLoading = (RelativeLayout)findViewById(R.id.loading_layout);
        mRlLoading.setVisibility(View.GONE);

        mRlMain = (RelativeLayout)findViewById(R.id.main_layout);
        mRlMain.setVisibility(View.VISIBLE);

        mEtUsername.setHint(mI10n.l("username"));
        mEtPassword.setHint(mI10n.l("password"));

        mSignInBtn.setText(mI10n.l("signin"));
        mSignInBtn.setOnClickListener(new SignInActionHandler());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // getMenuInflater().inflate(R.menu.menu_auth, menu);
        return false;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return super.onOptionsItemSelected(item);
    }
}
