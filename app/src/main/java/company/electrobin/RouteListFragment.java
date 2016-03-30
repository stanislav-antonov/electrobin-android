package company.electrobin;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import company.electrobin.i10n.I10n;
import company.electrobin.user.User;

public class RouteListFragment extends Fragment {
    // TODO: Rename parameter arguments, choose names that match
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";

    private final static String LOG_TAG = RouteListFragment.class.getSimpleName();

    // TODO: Rename and change types of parameters
    private String mParam1;
    private String mParam2;

    private User mUser;
    private I10n mI10n;
    private ElectrobinApplication mApp;

    private RelativeLayout mRlRouteWaiting;
    private RelativeLayout mRlRouteReview;

    private boolean mRouteListDisplayed;

    private OnFragmentInteractionListener mListener;

    public interface OnFragmentInteractionListener {
        // TODO: Update argument type and name
        public void onFragmentInteraction(Uri uri);
    }

    private static class RouteListAdapter extends ArrayAdapter<RouteActivity.Route.Point> {

        private static class ViewHolder {
            private TextView mTvAddress;
        }

        public RouteListAdapter(Context context, List<RouteActivity.Route.Point> items) {
            super(context, R.layout.route_points_item_layout, items);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {

            ViewHolder viewHolder;

            if (convertView == null) {
                convertView = LayoutInflater.from(getContext())
                        .inflate(R.layout.route_points_item_layout, parent, false);

                viewHolder = new ViewHolder();
                viewHolder.mTvAddress = (TextView)convertView.findViewById(R.id.address_text);

                convertView.setTag(viewHolder);
            }
            else {
                viewHolder = (ViewHolder)convertView.getTag();
            }

            RouteActivity.Route.Point item = getItem(position);
            if (item != null)
                viewHolder.mTvAddress.setText(item.mAddress);

            return convertView;
        }
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @return A new instance of fragment RouteListFragment.
     */
    // TODO: Rename and change types and number of parameters
    // public static RouteListFragment newInstance(String param1, String param2) {
    public static RouteListFragment newInstance() {
        RouteListFragment fragment = new RouteListFragment();
        // Bundle args = new Bundle();
        // args.putString(ARG_PARAM1, param1);
        // args.putString(ARG_PARAM2, param2);
        // fragment.setArguments(args);
        return fragment;
    }

    public RouteListFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mParam1 = getArguments().getString(ARG_PARAM1);
            mParam2 = getArguments().getString(ARG_PARAM2);
        }

        mApp = (ElectrobinApplication)getActivity().getApplicationContext();
        mUser = mApp.getUser();
        mI10n = mApp.getI10n();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_route_list, container, false);

        mRlRouteWaiting = (RelativeLayout)view.findViewById(R.id.route_waiting_layout);
        mRlRouteReview = (RelativeLayout)view.findViewById(R.id.route_review_layout);

        return view;
    }

    @Override
    public void onActivityCreated (Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        if (mRouteListDisplayed)
            showRouteList();
        else
            showRouteWaiting();
    }

    // TODO: Rename method, update argument and hook method into UI event
    public void onButtonPressed(Uri uri) {
        if (mListener != null) {
            mListener.onFragmentInteraction(uri);
        }
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
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    private void switchRouteWaitingLayout() {
        mRlRouteWaiting.setVisibility(View.VISIBLE);
        mRlRouteReview.setVisibility(View.GONE);
    }

    private void switchRouteListLayout() {
        mRlRouteReview.setVisibility(View.VISIBLE);
        mRlRouteWaiting.setVisibility(View.GONE);
    }

    /**
     *
     */
    public void showRouteWaiting() {
        switchRouteWaitingLayout();

        ((TextView)mRlRouteWaiting.findViewById(R.id.route_waiting_text_1)).setText(mI10n.l("route_waiting_1"));
        ((TextView)mRlRouteWaiting.findViewById(R.id.route_waiting_text_2)).setText(mI10n.l("route_waiting_2"));

        mRouteListDisplayed = false;
    }

    /**
     *
     */
    public void showRouteList() {

        RouteActivity activity = (RouteActivity)getActivity();
        RouteActivity.Route route = activity.getCurrentRoute();

        switchRouteListLayout();

        final Button btnRouteStart = (Button)mRlRouteReview.findViewById(R.id.route_start_button);
        btnRouteStart.setText(mI10n.l("route_start"));

        final TextView tvRouteDate = (TextView)mRlRouteReview.findViewById(R.id.route_date_text);
        tvRouteDate.setVisibility(View.GONE);

        final ListView lvRoutePoints = (ListView)mRlRouteReview.findViewById(R.id.route_points_list);
        lvRoutePoints.setVisibility(View.GONE);

        if (route.getDate() != null) {
            tvRouteDate.setVisibility(View.VISIBLE);
            tvRouteDate.setText(String.format(mI10n.l("route_date"), route.getDate()));
        }

        if (!route.getPointList().isEmpty()) {
            lvRoutePoints.setVisibility(View.VISIBLE);
            lvRoutePoints.setAdapter(new RouteListAdapter(getActivity(), route.getPointList()));
            mRouteListDisplayed = true;
        }
    }
}
