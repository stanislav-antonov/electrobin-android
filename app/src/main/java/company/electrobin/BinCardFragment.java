package company.electrobin;

import android.app.Activity;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RelativeLayout;
import android.widget.TextView;

import company.electrobin.i10n.I10n;
import company.electrobin.user.User;


public class BinCardFragment extends Fragment {

    private User mUser;
    private I10n mI10n;
    private ElectrobinApplication mApp;

    private Button mBtnRoutePointDone;
    private RadioButton mRbBinUnloadedOk;
    private RadioButton mRbBinUnloadedError;
    private EditText mEtBinComment;

    private TextView mTvRoutePointAddress;

    private RouteActivity.Route.Point mRoutePoint;

    private OnFragmentInteractionListener mListener;

    private final static String BUNDLE_KEY_ROUTE_POINT = "route_point";
    public final static String FRAGMENT_TAG = "fragment_bin_card";

    public interface OnFragmentInteractionListener {
        public void onRoutePointDone(RouteActivity.Route.Point point);
    }

    public static BinCardFragment newInstance() {
        return new BinCardFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null)
            mRoutePoint = savedInstanceState.getParcelable(BUNDLE_KEY_ROUTE_POINT);

        mApp = (ElectrobinApplication)getActivity().getApplicationContext();
        mUser = mApp.getUser();
        mI10n = mApp.getI10n();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_bin_card, container, false);

        mBtnRoutePointDone = (Button)view.findViewById(R.id.route_point_done_button);
        mBtnRoutePointDone.setText(mI10n.l("next_route_point"));
        mBtnRoutePointDone.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mRoutePoint.mIsUnloadedOk = mRbBinUnloadedOk.isChecked();
                mRoutePoint.mComment = mEtBinComment.getText().toString();
                mListener.onRoutePointDone(mRoutePoint);
            }
        });

        ((TextView)view.findViewById(R.id.bin_comment_label_text)).setText(mI10n.l("comment"));
        ((TextView)view.findViewById(R.id.bin_status_label_text)).setText(mI10n.l("container_status"));

        mRbBinUnloadedOk = (RadioButton)view.findViewById(R.id.bin_unloaded_ok_radio);
        mRbBinUnloadedOk.setText(mI10n.l("container_status_unloaded_ok"));

        mRbBinUnloadedError = (RadioButton)view.findViewById(R.id.bin_unloaded_error_radio);
        mRbBinUnloadedError.setText(mI10n.l("container_status_unloaded_error"));

        mEtBinComment = (EditText)view.findViewById(R.id.bin_comment_input);

        mTvRoutePointAddress = (TextView)view.findViewById(R.id.address_text);
        mTvRoutePointAddress.setText(mRoutePoint.mAddress);

        ((TextView)view.findViewById(R.id.header_text)).setText(mI10n.l("have_arrived_route_point"));

        return view;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            mListener = (OnFragmentInteractionListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement OnFragmentInteractionListener");
        }
    }

    @Override
    public void onActivityCreated (Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        setActionBarTitle();
    }

    /**
     *
     */
    @Override
    public void onStop() {
        super.onStop();
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
     * @param point
     */
    public void setRoutePoint(RouteActivity.Route.Point point) {
        mRoutePoint = point;
    }

    /**
     *
     * @param bundle
     */
    @Override
    public void onSaveInstanceState(Bundle bundle) {
        super.onSaveInstanceState(bundle);
        if (mRoutePoint != null)
            bundle.putParcelable(BUNDLE_KEY_ROUTE_POINT, mRoutePoint);
    }

    /**
     *
     */
    private void setActionBarTitle() {
        final RouteActivity routeActivity = (RouteActivity) getActivity();
        if (routeActivity != null) routeActivity.setActionBarTitle(mI10n.l("container"));
    }
}
