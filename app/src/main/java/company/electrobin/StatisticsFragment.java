package company.electrobin;

import android.app.Activity;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.util.List;

import company.electrobin.common.route.Route;
import company.electrobin.i10n.I10n;
import company.electrobin.user.User;

public class StatisticsFragment extends Fragment {

    private User mUser;
    private I10n mI10n;
    private ElectrobinApplication mApp;

    private LinearLayout mLlCommon;
    private Button btnNewRoute;

    private OnFragmentInteractionListener mListener;

    public final static String FRAGMENT_TAG = "fragment_statistics";

    public interface OnFragmentInteractionListener {
        public void onGetNewRoute();
        public Route onGetRoute();
    }

    private final Category[] mCategory = {
        new Category(0, 50, R.drawable.background_statistics_percent_1),
        new Category(50, 75, R.drawable.background_statistics_percent_2),
        new Category(75, 90, R.drawable.background_statistics_percent_3),
        new Category(90, 100, R.drawable.background_statistics_percent_4)
    };

    private class Category {

        private int mFrom;
        private int mTo;
        private int mBg;

        int mCount;
        int mVolume;

        Category(int from, int to, int bg) {
            mFrom = from;
            mTo = to;
            mBg = bg;
        }

        public int from() {
            return mFrom;
        }

        public int to() {
            return mTo;
        }

        public int bg() {
            return mBg;
        }

        @Override
        public String toString() {
            return String.format("%s - %s", mFrom, mTo);
        }

        @Override
        public int hashCode() {
            return Integer.parseInt( String.valueOf(mFrom) + String.valueOf(mTo) );
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || !(o instanceof Category)) {
                return false;
            }

            Category category = (Category)o;
            return (this.mFrom == category.mFrom && this.mTo == category.mTo);
        }
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
        mLlCommon = (LinearLayout)view.findViewById(R.id.common_layout);

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
        setActionBarTitle();
    }

    public void showUI() {
        ((TextView) mLlCommon.findViewById(R.id.run_text)).setText(mI10n.l("run_text"));
        ((TextView) mLlCommon.findViewById(R.id.fill_text)).setText(mI10n.l("fill"));
        ((TextView) mLlCommon.findViewById(R.id.count_text)).setText(mI10n.l("count"));
        ((TextView) mLlCommon.findViewById(R.id.volume_text)).setText(mI10n.l("volume"));
        ((TextView) mLlCommon.findViewById(R.id.summary_text)).setText(mI10n.l("summary"));

        final Route route = mListener.onGetRoute();
        if (route != null) {
            ((TextView) mLlCommon.findViewById(R.id.run_value)).setText(String.format(mI10n.l("run_value"), route.getRunFormatted()));

            List<Route.Point> list = route.getWayPointList();

            for (Route.Point point : list) {
                if (!point.mIsVisited) continue;
                int fullness = point.mFullness;

                if (fullness >= mCategory[0].from() && fullness <= mCategory[0].to()) {
                    mCategory[0].mCount++;
                    mCategory[0].mVolume += point.mVolume;
                }
                else if (fullness > mCategory[1].from() && fullness <= mCategory[1].to()) {
                    mCategory[1].mCount++;
                    mCategory[1].mVolume += point.mVolume;
                }
                else if (fullness > mCategory[2].from() && fullness <= mCategory[2].to()) {
                    mCategory[2].mCount++;
                    mCategory[2].mVolume += point.mVolume;
                }
                else if (fullness > mCategory[3].from() && fullness <= mCategory[3].to()) {
                    mCategory[3].mCount++;
                    mCategory[3].mVolume += point.mVolume;
                }
            }
        }

        (mLlCommon.findViewById(R.id.category_1_legend)).setBackgroundDrawable(getResources().getDrawable(mCategory[0].bg()));
        ((TextView)mLlCommon.findViewById(R.id.category_1_name)).setText(mCategory[0].toString());
        ((TextView)mLlCommon.findViewById(R.id.category_1_count)).setText(String.valueOf(mCategory[0].mCount));
        ((TextView)mLlCommon.findViewById(R.id.category_1_volume)).setText(String.valueOf(mCategory[0].mVolume));

        (mLlCommon.findViewById(R.id.category_2_legend)).setBackgroundDrawable(getResources().getDrawable(mCategory[1].bg()));
        ((TextView)mLlCommon.findViewById(R.id.category_2_name)).setText(mCategory[1].toString());
        ((TextView)mLlCommon.findViewById(R.id.category_2_count)).setText(String.valueOf(mCategory[1].mCount));
        ((TextView)mLlCommon.findViewById(R.id.category_2_volume)).setText(String.valueOf(mCategory[1].mVolume));

        (mLlCommon.findViewById(R.id.category_3_legend)).setBackgroundDrawable(getResources().getDrawable(mCategory[2].bg()));
        ((TextView)mLlCommon.findViewById(R.id.category_3_name)).setText(mCategory[2].toString());
        ((TextView)mLlCommon.findViewById(R.id.category_3_count)).setText(String.valueOf(mCategory[2].mCount));
        ((TextView)mLlCommon.findViewById(R.id.category_3_volume)).setText(String.valueOf(mCategory[2].mVolume));

        (mLlCommon.findViewById(R.id.category_4_legend)).setBackgroundDrawable(getResources().getDrawable(mCategory[3].bg()));
        ((TextView)mLlCommon.findViewById(R.id.category_4_name)).setText(mCategory[3].toString());
        ((TextView)mLlCommon.findViewById(R.id.category_4_count)).setText(String.valueOf(mCategory[3].mCount));
        ((TextView)mLlCommon.findViewById(R.id.category_4_volume)).setText(String.valueOf(mCategory[3].mVolume));

        int summaryCount = 0, summaryVolume = 0;
        for (int i = 0; i < 4; i++) {
            summaryCount += mCategory[i].mCount;
            summaryVolume += mCategory[i].mVolume;
        }

        ((TextView)mLlCommon.findViewById(R.id.summary_count)).setText(String.valueOf(summaryCount));
        ((TextView)mLlCommon.findViewById(R.id.summary_volume)).setText(String.valueOf(summaryVolume));

        final RelativeLayout rlMain = (RelativeLayout) mLlCommon.getParent();
        btnNewRoute = (Button)rlMain.findViewById(R.id.new_route_button);
        btnNewRoute.setText(mI10n.l("get_route"));
        btnNewRoute.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mListener.onGetNewRoute();
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        setActionBarTitle();
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden && !isRemoving()) setActionBarTitle();
    }

    private void setActionBarTitle() {
        final RouteActivity routeActivity = (RouteActivity) getActivity();
        if (routeActivity != null) routeActivity.setActionBarTitle(mI10n.l("statistics"));
    }
}
