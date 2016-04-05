package company.electrobin;

import android.app.Activity;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.TextView;

import company.electrobin.i10n.I10n;
import company.electrobin.user.User;


public class BinCardFragment extends Fragment {

    private User mUser;
    private I10n mI10n;
    private ElectrobinApplication mApp;

    private Button mBtnNextRoutePoint;

    private RadioButton mRbBinUnloadedOk;
    private RadioButton mRbBinUnloadedError;

    private TextView mTvRoutePointAddress;

    private OnFragmentInteractionListener mListener;

    public interface OnFragmentInteractionListener {

        public void onNextRoutePoint();
    }

    public static BinCardFragment newInstance() {
        return new BinCardFragment();
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
        View view = inflater.inflate(R.layout.fragment_bin_card, container, false);
        mBtnNextRoutePoint = (Button)view.findViewById(R.id.next_route_point_button);
        mBtnNextRoutePoint.setText(mI10n.l("to_next_route_point"));

        ((TextView)view.findViewById(R.id.bin_comment_label_text)).setText(mI10n.l("comment"));
        ((TextView)view.findViewById(R.id.bin_status_label_text)).setText(mI10n.l("container_status"));

        mRbBinUnloadedOk = (RadioButton)view.findViewById(R.id.bin_unloaded_ok_radio);
        mRbBinUnloadedOk.setText(mI10n.l("container_status_unloaded_ok"));

        mRbBinUnloadedError = (RadioButton)view.findViewById(R.id.bin_unloaded_error_radio);
        mRbBinUnloadedError.setText(mI10n.l("container_status_unloaded_error"));

        mTvRoutePointAddress = (TextView)view.findViewById(R.id.route_point_address_text);

        ((TextView)view.findViewById(R.id.header_text)).setText(mI10n.l("have_arrived_the_route_point"));

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
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }
}
