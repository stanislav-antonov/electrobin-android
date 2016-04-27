package company.electrobin;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.Button;
import android.widget.ExpandableListView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import company.electrobin.i10n.I10n;
import company.electrobin.user.User;

public class RouteListFragment extends Fragment {

    private User mUser;
    private I10n mI10n;
    private ElectrobinApplication mApp;

    private LinearLayout mLlRouteWaiting;
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

    private class RouteListAdapter extends BaseExpandableListAdapter {

        private final List<PointGroup> mItemList;
        private final LayoutInflater inflater;

        private class PointGroup {
            public final String mCity;
            private List<RouteActivity.Route.Point> mChildItemList;

            public PointGroup(String city) {
                mCity = city;
                mChildItemList = new ArrayList<RouteActivity.Route.Point>();
            }

            public List<RouteActivity.Route.Point> getChildItemList() {
                return mChildItemList;
            }
        }

        private class ViewHolder {
            private TextView mTvText;
        }

        public RouteListAdapter(Context context, List<RouteActivity.Route.Point> itemList) {
            inflater = LayoutInflater.from(context);
            mItemList = new ArrayList<>();
            HashMap<String, PointGroup> groups = new HashMap<>();
            for (RouteActivity.Route.Point p : itemList) {
                if (groups.containsKey(p.mCity)) {
                    groups.get(p.mCity).getChildItemList().add(p);
                } else {
                    PointGroup group = new PointGroup(p.mCity);
                    group.getChildItemList().add(p);
                    groups.put(p.mCity, group);
                    mItemList.add(group);
                }
            }

            Collections.sort(mItemList, new Comparator<PointGroup>() {
                @Override
                public int compare(PointGroup lhs, PointGroup rhs) {
                    return lhs.mCity.compareToIgnoreCase(rhs.mCity);
                }
            });
        }

        @Override
        public RouteActivity.Route.Point getChild(int groupPosition, int childPosition) {
            return mItemList.get(groupPosition).getChildItemList().get(childPosition);
        }

        @Override
        public long getChildId(int groupPosition, int childPosition) {
            return childPosition;
        }

        @Override
        public int getChildrenCount(int groupPosition) {
            return mItemList.get(groupPosition).getChildItemList().size();
        }

        @Override
        public View getChildView(int groupPosition, int childPosition, boolean isLastChild, View convertView,
                                 final ViewGroup parent) {
            ViewHolder viewHolder;

            if (convertView == null) {
                convertView = inflater.inflate(R.layout.layout_route_list_item, parent, false);

                viewHolder = new ViewHolder();
                viewHolder.mTvText = (TextView)convertView.findViewById(R.id.address_text);

                convertView.setTag(viewHolder);
            }
            else {
                viewHolder = (ViewHolder)convertView.getTag();
            }

            final RouteActivity.Route.Point item = getChild(groupPosition, childPosition);
            if (item != null)
                viewHolder.mTvText.setText(item.mAddress);

            return convertView;
        }

        @Override
        public PointGroup getGroup(int groupPosition) {
            return mItemList.get(groupPosition);
        }

        @Override
        public int getGroupCount() {
            return mItemList.size();
        }

        @Override
        public long getGroupId(final int groupPosition) {
            return groupPosition;
        }

        @Override
        public View getGroupView(int groupPosition, boolean isExpanded, View convertView, ViewGroup parent) {
            ViewHolder viewHolder;

            if (convertView == null) {
                convertView = inflater.inflate(R.layout.layout_route_list_group_item, null);

                viewHolder = new ViewHolder();
                viewHolder.mTvText = (TextView)convertView.findViewById(R.id.city_text);

                convertView.setTag(viewHolder);
            }
            else {
                viewHolder = (ViewHolder)convertView.getTag();
            }

            final PointGroup item = getGroup(groupPosition);
            viewHolder.mTvText.setText(item.mCity);

            ((ExpandableListView)parent).expandGroup(groupPosition);

            return convertView;
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }

        @Override
        public boolean isChildSelectable(int groupPosition, int childPosition) {
            return true;
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

        mLlRouteWaiting = (LinearLayout)view.findViewById(R.id.route_waiting_layout);
        mRlRouteList = (RelativeLayout)view.findViewById(R.id.route_list_layout);

        return view;
    }

    @Override
    public void onActivityCreated (Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if (savedInstanceState != null) {
            mLayoutDisplayed = savedInstanceState.getInt(BUNDLE_KEY_DISPLAY_LAYOUT);
        }

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

        setActionBarTitle();
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
     */
    @Override
    public void onResume() {
        super.onResume();
        setActionBarTitle();
    }

    /*;

     */
    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    /**
     *
     */
    private void switchRouteWaitingLayout() {
        mLlRouteWaiting.setVisibility(View.VISIBLE);
        mRlRouteList.setVisibility(View.GONE);
    }

    /**
     *
     */
    private void switchRouteListLayout() {
        mRlRouteList.setVisibility(View.VISIBLE);
        mLlRouteWaiting.setVisibility(View.GONE);
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

        ((TextView)mLlRouteWaiting.findViewById(R.id.route_waiting_text_1)).setText(mI10n.l("route_waiting_1"));

        View circle1 = mLlRouteWaiting.findViewById(R.id.circle_1);
        final GradientDrawable gd1 = new GradientDrawable();
        gd1.setShape(GradientDrawable.OVAL);
        circle1.setBackground(gd1);

        View circle2 = mLlRouteWaiting.findViewById(R.id.circle_2);
        final GradientDrawable gd2 = new GradientDrawable();
        gd2.setShape(GradientDrawable.OVAL);
        circle2.setBackground(gd2);

        View circle3 = mLlRouteWaiting.findViewById(R.id.circle_3);
        final GradientDrawable gd3 = new GradientDrawable();
        gd3.setShape(GradientDrawable.OVAL);
        circle3.setBackground(gd3);

        final String color1 = "#9cd1e6";
        final String color2 = "#0089bf";

        AnimatorSet as = new AnimatorSet();
        as.playSequentially(colorChanger(gd1, color1, color2, 200),
                colorChanger(gd1, color2, color1, 500),
                colorChanger(gd2, color1, color2, 200),
                colorChanger(gd2, color2, color1, 500),
                colorChanger(gd3, color1, color2, 200),
                colorChanger(gd3, color2, color1, 500)
        );

        as.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                gd1.setColor(Color.parseColor(color1));
                gd2.setColor(Color.parseColor(color1));
                gd3.setColor(Color.parseColor(color1));
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                animation.start();
            }
        });

        as.start();

        mLayoutDisplayed = LAYOUT_DISPLAYED_ROUTE_WAITING;
    }

    /**
     *
     */
    public void showUIRouteList() {
        switchRouteListLayout();

        final RouteActivity.Route route = mListener.onGetRoute();

        // final TextView tvRouteDate = (TextView)mRlRouteList.findViewById(R.id.route_date_text);
        // tvRouteDate.setVisibility(View.GONE);
        // tvRouteDate.setVisibility(View.VISIBLE);
        // tvRouteDate.setText(String.format(mI10n.l("route_date"), route.getDateFormatted(RouteActivity.Route.FORMAT_DATE_FORMATTED)));

        final ExpandableListView elvRoutePoints = (ExpandableListView)mRlRouteList.findViewById(R.id.route_point_list);
        elvRoutePoints.setVisibility(View.VISIBLE);
        elvRoutePoints.setGroupIndicator(null);
        elvRoutePoints.setOnGroupClickListener(new ExpandableListView.OnGroupClickListener() {
            @Override
            public boolean onGroupClick(ExpandableListView parent, View v, int groupPosition, long id) {
                return true;
            }
        });

        elvRoutePoints.setAdapter(new RouteListAdapter(getActivity(), route.getWayPointList()));

        final Button btnRouteStart = (Button)mRlRouteList.findViewById(R.id.route_start_button);
        btnRouteStart.setText(mI10n.l("route_start"));
        btnRouteStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mListener.onRouteStart();
            }
        });

        mLayoutDisplayed = LAYOUT_DISPLAYED_ROUTE_LIST;
    }

    /**
     *
     * @param bundle
     */
    @Override
    public void onSaveInstanceState(Bundle bundle) {
        super.onSaveInstanceState(bundle);
        bundle.putInt(BUNDLE_KEY_DISPLAY_LAYOUT, mLayoutDisplayed);
    }

    /**
     *
     * @param gd
     * @param from
     * @param to
     * @return
     */
    private static ValueAnimator colorChanger(final GradientDrawable gd, String from, String to, int duration) {
        ValueAnimator va = ObjectAnimator.ofObject(new ArgbEvaluator(),
                Color.parseColor(from), Color.parseColor(to));
        va.setDuration(duration);
        va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                gd.setColor((Integer) animation.getAnimatedValue());
            }
        });

        return va;
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
        if (routeActivity != null) routeActivity.setActionBarTitle(mI10n.l("route_list"));
    }
}
