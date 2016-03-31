package company.electrobin;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;

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
    private RelativeLayout mRlRouteList;

    // private boolean mRouteListDisplayed;

    private int mLayoutDisplayed;

    private OnFragmentInteractionListener mListener;

    private static final int LAYOUT_DISPLAYED_ROUTE_LIST = 1;
    private static final int LAYOUT_DISPLAYED_ROUTE_WAITING = 2;

    public interface OnFragmentInteractionListener {
        // TODO: Update argument type and name
        public void onFragmentInteraction(Uri uri);
    }

    private static class RouteListAdapter extends ArrayAdapter<RouteActivity.Route.Point> {

        private static class ViewHolder {
            private TextView mTvAddress;
        }

        public RouteListAdapter(Context context, List<RouteActivity.Route.Point> items) {
            super(context, R.layout.layout_route_list_item, items);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {

            ViewHolder viewHolder;

            if (convertView == null) {
                convertView = LayoutInflater.from(getContext())
                        .inflate(R.layout.layout_route_list_item, parent, false);

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
        // if (getArguments() != null) {
        //    mParam1 = getArguments().getString(ARG_PARAM1);
        //    mParam2 = getArguments().getString(ARG_PARAM2);
        //}

        mApp = (ElectrobinApplication)getActivity().getApplicationContext();
        mUser = mApp.getUser();
        mI10n = mApp.getI10n();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_route_list, container, false);

        mRlRouteWaiting = (RelativeLayout)view.findViewById(R.id.route_waiting_layout);
        mRlRouteList = (RelativeLayout)view.findViewById(R.id.route_list_layout);

        return view;
    }

    @Override
    public void onActivityCreated (Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        switch (mLayoutDisplayed) {
            case LAYOUT_DISPLAYED_ROUTE_WAITING:
                showRouteWaiting();
                break;
            case LAYOUT_DISPLAYED_ROUTE_LIST:
                showRouteList();
                break;
            default:
                showRouteWaiting();
                break;
        }
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
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    /**
     *
     */
    private void switchRouteWaitingLayout() {
        mRlRouteWaiting.setVisibility(View.VISIBLE);
        mRlRouteList.setVisibility(View.GONE);
    }

    /**
     *
     */
    private void switchRouteListLayout() {
        mRlRouteList.setVisibility(View.VISIBLE);
        mRlRouteWaiting.setVisibility(View.GONE);
    }

    /**
     *
     */
    public void showRouteWaiting() {
        switchRouteWaitingLayout();

        ((TextView)mRlRouteWaiting.findViewById(R.id.route_waiting_text_1)).setText(mI10n.l("route_waiting_1"));
        ((TextView)mRlRouteWaiting.findViewById(R.id.route_waiting_text_2)).setText(mI10n.l("route_waiting_2"));

        mLayoutDisplayed = LAYOUT_DISPLAYED_ROUTE_WAITING;
    }

    /**
     *
     */
    public void showRouteList() {
        switchRouteListLayout();

        final Button btnRouteStart = (Button)mRlRouteList.findViewById(R.id.route_start_button);
        btnRouteStart.setText(mI10n.l("route_start"));

        final TextView tvRouteDate = (TextView)mRlRouteList.findViewById(R.id.route_date_text);
        tvRouteDate.setVisibility(View.GONE);

        final ListView lvRoutePoints = (ListView)mRlRouteList.findViewById(R.id.route_point_list);
        lvRoutePoints.setVisibility(View.GONE);

        RouteActivity.Route route = ((RouteActivity)getActivity()).getCurrentRoute();

        if (route.getDate() != null) {
            tvRouteDate.setVisibility(View.VISIBLE);
            tvRouteDate.setText(String.format(mI10n.l("route_date"), route.getDate()));
        }

        if (!route.getPointList().isEmpty()) {
            lvRoutePoints.setVisibility(View.VISIBLE);
            lvRoutePoints.setAdapter(new RouteListAdapter(getActivity(), route.getPointList()));
        }

        mLayoutDisplayed = LAYOUT_DISPLAYED_ROUTE_LIST;
    }
}
