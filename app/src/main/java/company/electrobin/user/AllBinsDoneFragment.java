package company.electrobin.user;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import company.electrobin.ElectrobinApplication;
import company.electrobin.R;
import company.electrobin.i10n.I10n;


public class AllBinsDoneFragment extends Fragment {

    private User mUser;
    private I10n mI10n;
    private ElectrobinApplication mApp;

    private Button mBtnRouteDone;

    private OnFragmentInteractionListener mListener;

    public final static String FRAGMENT_TAG = "fragment_all_bins_done";

    public interface OnFragmentInteractionListener {
        public void onRouteDone();
    }

    public static AllBinsDoneFragment newInstance() {
        return new AllBinsDoneFragment();
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
        View view = inflater.inflate(R.layout.fragment_all_bins_done, container, false);

        ((TextView)view.findViewById(R.id.header_text)).setText(mI10n.l("have_got_all_bins"));
        ((TextView)view.findViewById(R.id.description_text)).setText(mI10n.l("please_move_to_the_base"));

        mBtnRouteDone = (Button)view.findViewById(R.id.route_done_button);
        mBtnRouteDone.setText(mI10n.l("finish_route"));

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
        mBtnRouteDone.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mListener.onRouteDone();
            }
        });
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }
}
