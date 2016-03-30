package company.electrobin;

import android.annotation.SuppressLint;
import android.app.Activity;
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


/**
 * A simple {@link Fragment} subclass.
 * Activities that contain this fragment must implement the
 * {@link RouteListFragment.OnFragmentInteractionListener} interface
 * to handle interaction events.
 * Use the {@link RouteListFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class RouteListFragment extends Fragment {
    // TODO: Rename parameter arguments, choose names that match
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";

    private final static String LOG_TAG = RouteListFragment.class.getSimpleName();
    private final static String JSON_DATE_KEY = "created";
    private final static String JSON_POINTS_KEY = "points";
    private final static String JSON_POINTS_ADDRESS = "address";

    // TODO: Rename and change types of parameters
    private String mParam1;
    private String mParam2;

    private User mUser;
    private I10n mI10n;
    private ElectrobinApplication mApp;

    private RelativeLayout mRlRouteWaiting;
    private RelativeLayout mRlRouteReview;

    private OnFragmentInteractionListener mListener;

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

    /**
     *
     */
    public void showRouteWaiting() {
        mRlRouteWaiting.setVisibility(View.VISIBLE);
        mRlRouteReview.setVisibility(View.GONE);

        ((TextView)mRlRouteWaiting.findViewById(R.id.route_waiting_text_1)).setText(mI10n.l("route_waiting_1"));
        ((TextView)mRlRouteWaiting.findViewById(R.id.route_waiting_text_2)).setText(mI10n.l("route_waiting_2"));
    }

    /**
     *
     */
    public void showRouteList(JSONObject json) {
        mRlRouteReview.setVisibility(View.VISIBLE);
        mRlRouteWaiting.setVisibility(View.GONE);

        final Button btnRouteStart = (Button)mRlRouteReview.findViewById(R.id.route_start_button);
        btnRouteStart.setText(mI10n.l("route_start"));

        final TextView tvRouteDate = (TextView)mRlRouteReview.findViewById(R.id.route_date_text);
        tvRouteDate.setVisibility(View.GONE);

        if (json.has(JSON_DATE_KEY)) {
            String strDate = null;
            try {
                // 2014-12-28T19:50:40.964531Z
                strDate = json.getString(JSON_DATE_KEY);

                @SuppressLint("SimpleDateFormat")
                SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
                Date date = df.parse(strDate);

                @SuppressLint("SimpleDateFormat")
                Format formatter = new SimpleDateFormat("H:mm d.MM.yyyy");
                strDate = formatter.format(date);
            }
            catch(Exception e) {
                strDate = null;
                Log.e(LOG_TAG, e.getMessage());
            }

            if (strDate != null) {
                tvRouteDate.setVisibility(View.VISIBLE);
                tvRouteDate.setText(String.format(mI10n.l("route_date"), strDate));
            }
        }

        final ListView lvRoutePoints = (ListView)mRlRouteReview.findViewById(R.id.route_points_list);
        lvRoutePoints.setVisibility(View.GONE);

        if (json.has(JSON_POINTS_KEY)) {
            List<String> addressList = new ArrayList<>();
            try {
                JSONArray pointList = json.getJSONArray(JSON_POINTS_KEY);
                for (int i = 0; i < pointList.length(); i++) {
                    JSONObject point = pointList.getJSONObject(i);
                    addressList.add(point.getString(JSON_POINTS_ADDRESS));
                }
            }
            catch (Exception e) {
                Log.e(LOG_TAG, e.getMessage());
                addressList.clear();

                (Toast.makeText(getActivity(), "Для отладки: ошибка в данных о точках маршрута", Toast.LENGTH_LONG)).show();
                showRouteWaiting();
            }

            if (!addressList.isEmpty()) {
                lvRoutePoints.setVisibility(View.VISIBLE);
                lvRoutePoints.setAdapter(new ArrayAdapter<String>(getActivity(), R.layout.route_points_item_layout, addressList));
            }
        }
    }


    /**
     * This interface must be implemented by activities that contain this
     * fragment to allow an interaction in this fragment to be communicated
     * to the activity and potentially other fragments contained in that
     * activity.
     * <p/>
     * See the Android Training lesson <a href=
     * "http://developer.android.com/training/basics/fragments/communicating.html"
     * >Communicating with Other Fragments</a> for more information.
     */
    public interface OnFragmentInteractionListener {
        // TODO: Update argument type and name
        public void onFragmentInteraction(Uri uri);
    }

}
