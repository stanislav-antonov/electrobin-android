package company.electrobin;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;

import company.electrobin.i10n.I10n;
import company.electrobin.user.User;

public class UserProfileFragment extends Fragment {

    private OnFragmentInteractionListener mListener;

    private User mUser;
    private I10n mI10n;
    private ElectrobinApplication mApp;

    private Handler mHandler = new Handler();

    private TextView mTvConnectionStatus;

    public final static String FRAGMENT_TAG = "fragment_user_profile";

    public interface OnFragmentInteractionListener {
        public boolean onGetIsConnected();
    }

    /**
     *
     * @return
     */
    public static UserProfileFragment newInstance() {
        return new UserProfileFragment();

    }

    public UserProfileFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mApp = (ElectrobinApplication)getActivity().getApplicationContext();
        mUser = mApp.getUser();
        mI10n = mApp.getI10n();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_user_profile, container, false);

        mTvConnectionStatus = (TextView)view.findViewById(R.id.connection_status_text);

        return view;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            mListener = (OnFragmentInteractionListener)activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement OnFragmentInteractionListener");
        }
    }

    /**
     *
     * @param isConnected
     */
    public void showIsConnected(final boolean isConnected) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                String strConnectionStatus = mI10n.l("connection_status");
                strConnectionStatus = String.format(strConnectionStatus, isConnected
                        ? mI10n.l("connection_status_connected") : mI10n.l("connection_status_disconnected"));
                mTvConnectionStatus.setText(strConnectionStatus);
            }
        });
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        showIsConnected(mListener.onGetIsConnected());

        View view = getView();
        if (view == null) return;

        User.UserProfile profile = mUser.getProfile();
        if (profile != null) {
            String strUserName = String.format(mI10n.l("driver_is"), String.format("%s %s", mUser.getProfile().mFirstName, mUser.getProfile().mLastName));
            ((TextView)view.findViewById(R.id.user_name_text)).setText(strUserName);
        }

        final Button btnLogOut = (Button)view.findViewById(R.id.logout_button);
        btnLogOut.setText(mI10n.l("action_logout"));
        btnLogOut.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mUser.logOut();
                startActivity(new Intent(getActivity(), AuthActivity.class));
            }
        });
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }
}
