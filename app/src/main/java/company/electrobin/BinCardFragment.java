package company.electrobin;

import android.app.Activity;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
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

    private RelativeLayout mRlBinCard;
    private LinearLayout mLlAllBinsDone;

    private Button mBtnRoutePointDone;
    private Button mBtnRouteDone;

    private RadioButton mRbBinUnloadedOk;
    private RadioButton mRbBinUnloadedError;

    private TextView mTvRoutePointAddress;

    private RouteActivity.Route.Point mRoutePoint;

    private int mLayoutDisplayed;

    private OnFragmentInteractionListener mListener;

    public static final int LAYOUT_DISPLAYED_BIN_CARD = 1;
    public static final int LAYOUT_DISPLAYED_ALL_BINS_DONE = 2;

    private final static String BUNDLE_KEY_ROUTE_POINT = "route_point";
    public final static String FRAGMENT_TAG = "fragment_bin_card";

    public interface OnFragmentInteractionListener {
        public void onRoutePointDone(RouteActivity.Route.Point point, BinCardFragment fragment);
        public void onRouteDone();
    }

    public static BinCardFragment newInstance() {
        return new BinCardFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mRoutePoint = getArguments().getParcelable(BUNDLE_KEY_ROUTE_POINT);
        }

        mApp = (ElectrobinApplication)getActivity().getApplicationContext();
        mUser = mApp.getUser();
        mI10n = mApp.getI10n();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_bin_card, container, false);

        mRlBinCard = (RelativeLayout)view.findViewById(R.id.bin_card_layout);
        mLlAllBinsDone = (LinearLayout)view.findViewById(R.id.all_bins_done_layout);

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
        switch (mLayoutDisplayed) {
            case LAYOUT_DISPLAYED_BIN_CARD:
                showUIBinCard();
                break;
            case LAYOUT_DISPLAYED_ALL_BINS_DONE:
                showUIAllBinsDone();
                break;
            default:
                showUIBinCard();
                break;
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    public void setRoutePoint(RouteActivity.Route.Point point) {
        mRoutePoint = point;
    }

    public void setLayoutDisplayed(int layout) {
        mLayoutDisplayed = layout;
    }

    public void showUIBinCard() {
        mLlAllBinsDone.setVisibility(View.GONE);
        mRlBinCard.setVisibility(View.VISIBLE);

        mBtnRoutePointDone = (Button)mRlBinCard.findViewById(R.id.route_point_done_button);
        mBtnRoutePointDone.setText(mI10n.l("next_route_point"));
        mBtnRoutePointDone.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mListener.onRoutePointDone(mRoutePoint, BinCardFragment.this);
            }
        });

        ((TextView)mRlBinCard.findViewById(R.id.bin_comment_label_text)).setText(mI10n.l("comment"));
        ((TextView)mRlBinCard.findViewById(R.id.bin_status_label_text)).setText(mI10n.l("container_status"));

        mRbBinUnloadedOk = (RadioButton)mRlBinCard.findViewById(R.id.bin_unloaded_ok_radio);
        mRbBinUnloadedOk.setText(mI10n.l("container_status_unloaded_ok"));

        mRbBinUnloadedError = (RadioButton)mRlBinCard.findViewById(R.id.bin_unloaded_error_radio);
        mRbBinUnloadedError.setText(mI10n.l("container_status_unloaded_error"));

        mTvRoutePointAddress = (TextView)mRlBinCard.findViewById(R.id.address_text);
        mTvRoutePointAddress.setText(mRoutePoint.mAddress);

        ((TextView)mRlBinCard.findViewById(R.id.header_text)).setText(mI10n.l("have_arrived_route_point"));

        mLayoutDisplayed = LAYOUT_DISPLAYED_BIN_CARD;
    }

    public void showUIAllBinsDone() {
        mRlBinCard.setVisibility(View.GONE);
        mLlAllBinsDone.setVisibility(View.VISIBLE);

        ((TextView)mLlAllBinsDone.findViewById(R.id.header_text)).setText(mI10n.l("have_got_all_bins"));
        ((TextView)mLlAllBinsDone.findViewById(R.id.description_text)).setText(mI10n.l("please_move_to_the_base"));

        mBtnRouteDone = (Button)mLlAllBinsDone.findViewById(R.id.route_done_button);
        mBtnRouteDone.setText(mI10n.l("finish_route"));
        mBtnRouteDone.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mListener.onRouteDone();
            }
        });

        mLayoutDisplayed = LAYOUT_DISPLAYED_ALL_BINS_DONE;
    }
}
