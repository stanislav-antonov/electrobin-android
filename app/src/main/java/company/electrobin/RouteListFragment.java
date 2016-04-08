package company.electrobin;

import android.app.Activity;
import android.content.Context;
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

    private User mUser;
    private I10n mI10n;
    private ElectrobinApplication mApp;

    private RelativeLayout mRlRouteWaiting;
    private RelativeLayout mRlRouteList;

    private int mLayoutDisplayed;

    private OnFragmentInteractionListener mListener;

    public static final int LAYOUT_DISPLAYED_ROUTE_LIST = 1;
    public static final int LAYOUT_DISPLAYED_ROUTE_WAITING = 2;

    private static final String BUNDLE_KEY_DISPLAY_LAYOUT = "display_layout";
    private final static String LOG_TAG = RouteListFragment.class.getSimpleName();
    public final static String FRAGMENT_TAG = "fragment_route_list";

    public interface OnFragmentInteractionListener {
        public RouteActivity.Route onGetRoute();
        public void onRouteStart();
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
        return new RouteListFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mLayoutDisplayed = getArguments().getInt(BUNDLE_KEY_DISPLAY_LAYOUT);
        }

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
                showUIRouteWaiting();
                break;
            case LAYOUT_DISPLAYED_ROUTE_LIST:
                showUIRouteList();
                break;
            default:
                showUIRouteWaiting();
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
     * @param layout
     */
    public void setLayoutDisplayed(int layout) {
        mLayoutDisplayed = layout;
    }

    /**
     *
     */
    public void showUIRouteWaiting() {
        switchRouteWaitingLayout();

        ((TextView)mRlRouteWaiting.findViewById(R.id.route_waiting_text_1)).setText(mI10n.l("route_waiting_1"));
        ((TextView)mRlRouteWaiting.findViewById(R.id.route_waiting_text_2)).setText(mI10n.l("route_waiting_2"));

        mLayoutDisplayed = LAYOUT_DISPLAYED_ROUTE_WAITING;
    }

    /**
     *
     */
    public void showUIRouteList() {
        switchRouteListLayout();

        final Button btnRouteStart = (Button)mRlRouteList.findViewById(R.id.route_start_button);
        btnRouteStart.setText(mI10n.l("route_start"));

        final TextView tvRouteDate = (TextView)mRlRouteList.findViewById(R.id.route_date_text);
        tvRouteDate.setVisibility(View.GONE);

        final ListView lvRoutePoints = (ListView)mRlRouteList.findViewById(R.id.route_point_list);
        lvRoutePoints.setVisibility(View.GONE);

        RouteActivity.Route route = mListener.onGetRoute();
        tvRouteDate.setVisibility(View.VISIBLE);
        tvRouteDate.setText(String.format(mI10n.l("route_date"), route.getDateFormatted(RouteActivity.Route.FORMAT_DATE_FORMATTED)));

        lvRoutePoints.setVisibility(View.VISIBLE);
        lvRoutePoints.setAdapter(new RouteListAdapter(getActivity(), route.getWayPointList()));

        btnRouteStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mListener.onRouteStart();
            }
        });

        mLayoutDisplayed = LAYOUT_DISPLAYED_ROUTE_LIST;
    }
}
