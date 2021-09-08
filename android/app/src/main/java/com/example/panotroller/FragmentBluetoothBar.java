package com.example.panotroller;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;

import androidx.fragment.app.Fragment;

import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

public class FragmentBluetoothBar extends Fragment {

    /* MEMBERS */
    private final static int UPDATE_FREQUENCY = 1000; // frequency at which we update, in ms
    final Handler updateHandler = new Handler();
    boolean mViewCreated = false;

    /* UI OBJECTS */
    private FrameLayout mFrameLayout;
    private TextView mTextView;

    public FragmentBluetoothBar() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
        mTextView = view.findViewById(R.id.bar_text);
        mFrameLayout.setOnClickListener(this::onBluetoothBarPress);
        mViewCreated = true;
    }

    @Override
    public void onStop() {
        super.onStop();
        Log.d("TAG", "Fragment onStop()");
        stopUpdates();
    }

    // Launch bluetooth config activity when anywhere in the fragment is pressed
    public void onBluetoothBarPress(View view) {
        Intent intent = new Intent(FragmentBluetoothBar.this.getActivity(), ActivityBluetoothConfig.class);
        startActivity(intent);
        // no need to pass any bluetooth data, as it's all handled by Bluetooth Service
    }

    public void beginUpdates(BluetoothService serviceIn) {
        // start updating bar every
        // TODO add retry capability?
        updateHandler.postDelayed(new Runnable() {
            public void run() {
                update(serviceIn.getBluetoothBarInfo()); // update bar with last latency
                if(serviceIn.getConnectionStatus() == BluetoothService.STATUS_CONNECTED) {
                    serviceIn.sendPing(false); // send a new ping too
                }
                updateHandler.postDelayed(this, UPDATE_FREQUENCY);
            }
        }, UPDATE_FREQUENCY);
    }

    public void stopUpdates() {
        Log.d("FRAG_STOPPED", "Fragment stopUpdates() called!");
        updateHandler.removeCallbacksAndMessages(null);
    }

    // Update bar status using the given information pulled from the bt. service
    @SuppressLint("SetTextI18n")
    public void update(BluetoothService.BluetoothBarInfo in) {
        Log.d("BT_BAR_UPDATE", "Bluetooth Bar update() called");
        if(mViewCreated) {
            // TODO improve performance by eliminating redundant setBackgroundColor calls
            switch(in.status) {
                case BluetoothService.STATUS_OFF:
                    this.getView().setBackgroundColor(getContext().getColor(R.color.red_disconnected));
                    mTextView.setText("Bluetooth Disabled");
                    break;
                case BluetoothService.STATUS_DISCONNECTED:
                    this.getView().setBackgroundColor(getContext().getColor(R.color.red_disconnected));
                    mTextView.setText("Bluetooth Disconnected");
                    break;
                case BluetoothService.STATUS_CONNECTING:
                    this.getView().setBackgroundColor(getContext().getColor(R.color.orange_connecting));
                    mTextView.setText("Connecting...");
                    break;
                case BluetoothService.STATUS_CONNECTED:
                    this.getView().setBackgroundColor(getContext().getColor(R.color.green_connected));
                    mTextView.setText("Connected (" + in.latency + "ms)");
                    break;
            }
        }
    }
}

