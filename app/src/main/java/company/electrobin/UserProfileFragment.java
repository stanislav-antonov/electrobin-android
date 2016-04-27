package company.electrobin;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import company.electrobin.i10n.I10n;
import company.electrobin.user.User;

public class UserProfileFragment extends Fragment {

    private OnFragmentInteractionListener mListener;

    private User mUser;
    private I10n mI10n;
    private ElectrobinApplication mApp;

    private Handler mHandler = new Handler();

    private TextView mTvInternetConnectionStatus;
    private TextView mTvGPSStatus;

    public final static String FRAGMENT_TAG = "fragment_user_profile";

    public interface OnFragmentInteractionListener {
        public boolean onGetInternetConnectionStatus();
        public boolean onGetGPSStatus();
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

        mTvInternetConnectionStatus = (TextView)view.findViewById(R.id.internet_connection_status_text);
        mTvGPSStatus = (TextView)view.findViewById(R.id.gps_status_text);

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
    public void showInternetConnectionStatus(final boolean isConnected) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                String strStatus = isConnected ? mI10n.l("online") : mI10n.l("offline");
                mTvInternetConnectionStatus.setText(strStatus);
            }
        });
    }

    /**
     *
     * @param isEnabled
     */
    public void showGPSStatus(final boolean isEnabled) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                String strStatus = isEnabled ? mI10n.l("enabled") : mI10n.l("disabled");
                mTvGPSStatus.setText(strStatus);
            }
        });
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        showInternetConnectionStatus(mListener.onGetInternetConnectionStatus());
        showGPSStatus(mListener.onGetGPSStatus());

        View view = getView();
        if (view == null) return;

        User.UserProfile profile = mUser.getProfile();
        if (profile != null) {
            String strUserName = String.format("%s %s", mUser.getProfile().mFirstName, mUser.getProfile().mLastName);
            ((TextView)view.findViewById(R.id.user_name_text)).setText(strUserName);
        }

        ((TextView)view.findViewById(R.id.internet_connection_text)).setText(mI10n.l("internet_connection"));
        ((TextView)view.findViewById(R.id.gps_text)).setText(mI10n.l("gps"));

        final Button btnLogOut = (Button)view.findViewById(R.id.logout_button);
        btnLogOut.setText(mI10n.l("action_logout"));
        btnLogOut.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mUser.logOut();
                startActivity(new Intent(getActivity(), AuthActivity.class));
            }
        });

        final RouteActivity routeActivity = (RouteActivity)getActivity();
        if (routeActivity != null)
            routeActivity.mBtnActionBarUserProfile.setVisibility(View.GONE);

        setActionBarTitle();
    }

    /**
     *
     */
    @Override
    public void onPause() {
        super.onPause();
    }

    /**
     *
     */
    @Override
    public void onStop() {
        super.onStop();
        final RouteActivity routeActivity = (RouteActivity)getActivity();
        if (routeActivity != null)
            routeActivity.mBtnActionBarUserProfile.setVisibility(View.VISIBLE);
    }

    /**
     *
     */
    @Override
    public void onResume() {
        super.onResume();
        setActionBarTitle();
    }

    /**
     *
     */
    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    /**
     *
     * @param isVisibleToUser
     */
    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
    }

    /**
     *
     * @param hidden
     */
    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden && !isRemoving()) setActionBarTitle();
    }

    /**
     *
     */
    private void setActionBarTitle() {
        final RouteActivity routeActivity = (RouteActivity) getActivity();
        if (routeActivity != null) routeActivity.setActionBarTitle(mI10n.l("profile"));
    }
}
