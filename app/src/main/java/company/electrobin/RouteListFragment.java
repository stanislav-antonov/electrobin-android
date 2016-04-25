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
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
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
}
