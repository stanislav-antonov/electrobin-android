package company.electrobin;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;

import company.electrobin.i10n.I10n;
import company.electrobin.user.User;

public class UserProfileFragment extends Fragment {

    private OnFragmentInteractionListener mListener;

    private User mUser;
    private I10n mI10n;
    private ElectrobinApplication mApp;

    private TextView mTvConnectionStatus;

    public interface OnFragmentInteractionListener {
        // TODO: Update argument type and name
        public void onFragmentInteraction(Uri uri);
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

    public void showConnectionStatusConnected(boolean isConnected) {

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

    @Override
    public void onActivityCreated (Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }
}
