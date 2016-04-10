package company.electrobin;

import android.app.Activity;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;

import company.electrobin.i10n.I10n;
import company.electrobin.user.User;

public class StatisticsFragment extends Fragment {

    private User mUser;
    private I10n mI10n;
    private ElectrobinApplication mApp;

    private RelativeLayout mRlCommon;
    private Button btnNewRoute;

    private OnFragmentInteractionListener mListener;

    public final static String FRAGMENT_TAG = "fragment_statistics";

    public interface OnFragmentInteractionListener {
        public void onGetNewRoute();
        public RouteActivity.Route onGetRoute();
    }

    public static StatisticsFragment newInstance() {
        return new StatisticsFragment();
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

        View view = inflater.inflate(R.layout.fragment_statistics, container, false);
        mRlCommon = (RelativeLayout)view.findViewById(R.id.common_layout);
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
        showUI();
    }

    public void showUI() {
        ((TextView)mRlCommon.findViewById(R.id.header_text)).setText(mI10n.l("statistics"));

        final RouteActivity.Route route = mListener.onGetRoute();
        if (route != null)
            ((TextView) mRlCommon.findViewById(R.id.run_text)).setText(String.format(mI10n.l("run"), route.getRunFormatted()));

        btnNewRoute = (Button)mRlCommon.findViewById(R.id.new_route_button);
        btnNewRoute.setText(mI10n.l("get_route"));
        btnNewRoute.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mListener.onGetNewRoute();
            }
        });
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }
}
