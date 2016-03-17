package company.electrobin;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import company.electrobin.i10n.I10n;
import company.electrobin.user.User;
import company.electrobin.user.UserAuthListener;

public class AuthActivity extends AppCompatActivity {

    private ElectrobinApplication mApp;
    private User mUser;
    private I10n mI10n;

    private EditText mEtUsername;
    private EditText mEtPassword;
    private Button mSignInBtn;

    private class SignInActionHandler implements View.OnClickListener, UserAuthListener {
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
            mUser.auth(username, password, SignInActionHandler.this);
        }

        @Override
        public void onAuthSuccess() {
            mSignInBtn.setEnabled(true);
            mEtUsername.setEnabled(true);
            mEtPassword.setEnabled(true);
            Toast.makeText(getBaseContext(), mUser.getAuthToken(), Toast.LENGTH_LONG).show();
        }

        @Override
        public void onAuthError(int error) {
            mSignInBtn.setEnabled(true);
            mEtUsername.setEnabled(true);
            mEtPassword.setEnabled(true);

            String strMessage = mI10n.l("error_common");
            if (error == UserAuthListener.ERROR_BAD_AUTH_PARAMS)
                strMessage = mI10n.l("username_or_password_wrong");

            Toast.makeText(getBaseContext(), strMessage, Toast.LENGTH_LONG).show();
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

        mEtUsername.setHint(mI10n.l("username"));
        mEtPassword.setHint(mI10n.l("password"));

        mSignInBtn.setText(mI10n.l("signin"));
        mSignInBtn.setOnClickListener(new SignInActionHandler());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_auth, menu);
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
