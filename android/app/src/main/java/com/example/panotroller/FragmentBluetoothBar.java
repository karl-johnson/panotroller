package com.example.panotroller;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;

import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link FragmentBluetoothBar#newInstance} factory method to
 * create an instance of this fragment.
 */
public class FragmentBluetoothBar extends Fragment {



    // TODO: Rename parameter arguments, choose names that match
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";

    // TODO: Rename and change types of parameters
    private String mParam1;
    private String mParam2;

    /* UI OBJECTS */
    private FrameLayout mFrameLayout;
    private TextView mTextView;

    public FragmentBluetoothBar() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param param1 Parameter 1.
     * @param param2 Parameter 2.
     * @return A new instance of fragment BluetoothBar.
     */
    // TODO: Rename and change types and number of parameters
    public static FragmentBluetoothBar newInstance(String param1, String param2) {
        FragmentBluetoothBar fragment = new FragmentBluetoothBar();
        Bundle args = new Bundle();
        args.putString(ARG_PARAM1, param1);
        args.putString(ARG_PARAM2, param2);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments() != null) {
            mParam1 = getArguments().getString(ARG_PARAM1);
            mParam2 = getArguments().getString(ARG_PARAM2);
        }

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_bluetooth_bar, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mFrameLayout = view.findViewById(R.id.bar_frame);
        mTextView = view.findViewById(R.id.textView);

        mFrameLayout.setOnClickListener(this::onBluetoothBarPress);
    }

    // Launch bluetooth config activity when anywhere in the fragment is pressed
    public void onBluetoothBarPress(View view) {
        Intent intent = new Intent(FragmentBluetoothBar.this.getActivity(), ActivityBluetoothConfig.class);
        startActivity(intent);
        // no need to pass any bluetooth data, as it's all handled by Bluetooth Service
    }

    // Update bar status using the given information pulled from the bt. service by the hosting act.
    // Put this information together into a BluetoothBarInfo object defined in the service code
    // We could bind the fragment to the service for this but this is only slightly less elegant
    @SuppressLint("SetTextI18n")
    public void updateBluetoothBar(BluetoothService.BluetoothBarInfo in) {
        // TODO improve performance by eliminating redundant setBackgroundColor calls
        switch(in.status) {
            case BluetoothService.STATUS_OFF:
                mFrameLayout.setBackgroundColor(getContext().getColor(R.color.red_disconnected));
                mTextView.setText("Bluetooth Disabled");
                break;
            case BluetoothService.STATUS_DISCONNECTED:
                mFrameLayout.setBackgroundColor(getContext().getColor(R.color.red_disconnected));
                mTextView.setText("Bluetooth Disconnected");
                break;
            case BluetoothService.STATUS_CONNECTING:
                mFrameLayout.setBackgroundColor(getContext().getColor(R.color.orange_connecting));
                mTextView.setText("Connecting...");
                break;
            case BluetoothService.STATUS_CONNECTED:
                mFrameLayout.setBackgroundColor(getContext().getColor(R.color.green_connected));
                mTextView.setText("Connected (" + in.latency + "ms)");
                break;
        }
    }
}

