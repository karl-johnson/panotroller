package com.example.panotroller;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class ActivityBluetoothConfig extends AppCompatActivity {

    /* CONSTANTS */
    // request code used to identify that the bluetooth on/off activity was launched
    private final static int REQUEST_ENABLE_BT = 1;

    /* DECLARE UI OBJECTS */
    private TextView mBluetoothStatusText;
    //private Button mDiscoverButton;
    private Button mDisconnectButton;
    private TextView mDevicesListViewTitle;
    private ListView mDevicesListView;

    /* BLUETOOTH RELATED OBJECTS */

    private BluetoothService mBluetoothService; //
    private BluetoothAdapter mBTAdapter = BluetoothAdapter.getDefaultAdapter();
    private Handler mHandler = new BluetoothConfigHandler(); // Handler to deal with information coming back over BT connection
    private boolean mIsBound = false; // Tracks whether unbinding is necessary on act. exit

    /* OTHER MEMBERS */
    private Set<BluetoothDevice> mPairedDevices; // set to keep track of all paired devices
    private List<String> mPairedDeviceNames = new ArrayList<String>();
    private final Random mRandom = new Random();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // apply layout
        setContentView(R.layout.activity_bluetooth_config);

        /* ASSIGN UI OBJECTS */
        mBluetoothStatusText = (TextView) findViewById(R.id.bluetoothStatus);
        //mDiscoverButton = (Button) findViewById(R.id.discover);
        mDisconnectButton = (Button) findViewById(R.id.disconnect);
        mDevicesListViewTitle = (TextView) findViewById(R.id.textView);
        mDevicesListView = (ListView)findViewById(R.id.devicesListView);

        /* CONFIGURE ACTION BAR */
        setTitle("Bluetooth Config");
        Toolbar thisToolbar = (Toolbar) findViewById(R.id.bt_setup_toolbar);
        setSupportActionBar(thisToolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true); // add back button to action bar

        /* CONFIGURE DISCONNECT BUTTON */
        // these are just defaults as the onBluetoothStatusChange doesn't work until the service
        // is active, which isn't instant
        mDisconnectButton.setEnabled(false);
        mDisconnectButton.getBackground().setAlpha(128); // TODO doesn't work as intended yet
        // set click behavior
        mDisconnectButton.setOnClickListener(this::disconnect);
    }

    public void onStart() {
        super.onStart();
        Log.d("TRY_BT_SERVICE", "Trying to connect to BT service");
        Intent BTServiceIntent = new Intent(this, BluetoothService.class);
        getApplicationContext().bindService(
                BTServiceIntent, mServiceConnection, Context.BIND_AUTO_CREATE);
        Log.d("TRY_BT_SERVICE", "Past BT service code");
        mIsBound = true;
        if(mBluetoothService != null) {
            Log.d("BT_SERVICE_EXISTS", "BT Service exists");
            Toast.makeText(getApplicationContext(),
                    "Bluetooth Service Exists", Toast.LENGTH_SHORT).show();
        }
    }

    protected void onDestroy() {
        super.onDestroy();
        if (mIsBound) {
            // Release information about the service's state.
            getApplicationContext().unbindService(mServiceConnection);
            mIsBound = false;
        }
    }

    /* TOOLBAR BUTTON BEHAVIOR */

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // make back button actually go back
        if (item.getItemId() == android.R.id.home) {
            onBackPressed(); // redundant functionality as back button
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /* SERVICE CONNECTION - NEEDED TO CONNECT TO BLUETOOTH SERVICE */

    private ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            BluetoothService.LocalBinder binder = (BluetoothService.LocalBinder) service;
            mBluetoothService = binder.getService();
            Log.d("SERVICE_CONNECTED","BT Service Connected");
            onServiceConnectedBluetoothTasks();
        }
        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            // don't need to do anything but apparently we need to say this anyways

        }
    };

    private void onServiceConnectedBluetoothTasks() {
        /* FUNDAMENTAL BLUETOOTH TASKS */
        // this is called only once we're bound to the service
        mBluetoothService.setHandler(mHandler);
        // turn on bluetooth if the user has it turned off
        if(mBluetoothService.getConnectionStatus() >= BluetoothService.STATUS_CONNECTING) {
            onBluetoothStatusChange();
            return;
        }

        if(!mBTAdapter.isEnabled()) {
            // if it is off, launch built-in activity to turn on bluetooth
            // onResume is called upon returning from ACTION_REQUEST_ENABLE so this will keep
            // appearing until bluetooth is enabled!
            updateBluetoothStatus(BluetoothService.STATUS_OFF, null);
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            // this is non-blocking!
        }
        if(mBTAdapter.isEnabled()) {// things to do once BT adapter is enabled
            // set proper text
            updateBluetoothStatus(BluetoothService.STATUS_DISCONNECTED, null);
            // assign list of paired devices
            mPairedDevices = mBTAdapter.getBondedDevices();
            // iterate through these to get simple List of device names
            for(BluetoothDevice device : mPairedDevices) mPairedDeviceNames.add(device.getName());
            if(mPairedDevices.size() == 0) {
                // no paired devices, oh no!
                mPairedDeviceNames.add("No paired devices!");
            }
            // use a simple ArrayAdapter with this list to populate our listView with device names
            mDevicesListView.setAdapter(new ArrayAdapter<String>(
                    this, R.layout.device_item, R.id.name, mPairedDeviceNames));
            // now link the behavior we want when an item is clicked
            mDevicesListView.setOnItemClickListener(mDeviceClickListener);
            //Log.i("BT_DEVICE_LIST", "mPairedDevices size: " + mPairedDeviceNames.size());
        }
    }

    /* LISTENER - DETERMINES WHAT WE DO WHEN A DEVICE IS CLICKED ON */

    private AdapterView.OnItemClickListener mDeviceClickListener =
            new AdapterView.OnItemClickListener() {
        public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
            // iterate through set until we reach the id we clicked
            BluetoothDevice nonFinalDevice = null;
            int index = 0;
            for(BluetoothDevice device : mPairedDevices) {
                if(index == id) {
                    nonFinalDevice = device;
                    break;
                }
                index++;
            }
            final BluetoothDevice clickedDevice = nonFinalDevice;
            if(clickedDevice == null) {
                // there was an issue and we didn't find the clicked device?
                Toast.makeText(getBaseContext(),
                        "Couldn't find clicked device!", Toast.LENGTH_SHORT).show();
            }
            // Update GUI to say we're connecting
            updateBluetoothStatus(BluetoothService.STATUS_CONNECTING, clickedDevice);

            // Spawn a thread to handle the blocking bluetooth connection process
            new Thread() {
                public void run() {
                    boolean fail = false;
                    try {
                        Log.d("BT_CONNECTION", "Starting socket creation attempt");
                        mBluetoothService.createBluetoothSocket(clickedDevice);
                    } catch (IOException e) {
                        fail = true;
                        Log.d("BT_CONNECTION", "Socket creation failed");
                        Toast.makeText(getBaseContext(), "Socket creation failed", Toast.LENGTH_SHORT).show();
                    }
                    // Establish the Bluetooth socket connection.
                    try {
                        Log.d("BT_CONNECTION", "Starting socket connection attempt");
                        mBluetoothService.getBluetoothSocket().connect();
                    } catch (IOException e) {
                        try {
                            Log.d("BT_CONNECTION", "Socket connection failed, closing");
                            // this exception means the socket creation failed
                            fail = true;
                            mBluetoothService.getBluetoothSocket().close();
                            // send message out of this thread using same handler as the service
                            mHandler.obtainMessage(
                                    BluetoothService.CONN_STATUS_UPDATED,
                                    BluetoothService.STATUS_DISCONNECTED, 0).sendToTarget();
                        } catch (IOException e2) {
                            //insert code to deal with this
                            Toast.makeText(getBaseContext(),
                                    "Socket creation failed", Toast.LENGTH_SHORT).show();
                        }
                    }
                    if(!fail) {
                        Log.d("BT_CONNECTION", "Socket succesfully created");
                        // socket creation DIDN'T fail
                        try {
                            // create bluetooth thread now that the socket is created
                            mBluetoothService.createBluetoothThread();
                        } catch (IOException e3) {
                            // thread didn't get created,
                            Log.e("THREAD_CREATION_FAILED",e3.getMessage());
                        }
                        if(mBluetoothService.getBluetoothThread() == null) {
                            Toast.makeText(getBaseContext(),
                                    "Thread creation failed", Toast.LENGTH_SHORT).show();
                        }
                        // we should now be connected to the HC-05 module
                        // to ensure there is actually a microcontroller on the other end, ping it
                        mBluetoothService.sendInstructionViaThread(
                                new BluetoothInstruction(
                                        GeneratedConstants.INST_PING_INT,
                                        (short) mRandom.nextInt(),
                                        (short) mRandom.nextInt())); // just random test numbers
                    }
                }
            }.start();
        }
    };

    /* DISCONNECTING FROM A DEVICE */

    private void disconnect(View view) {
        Log.d("BT_CONNECTION", "Called disconnect()");
        mBluetoothService.getBluetoothThread().disconnect();
        // TODO check that we really disconnected

        updateBluetoothStatus(BluetoothService.STATUS_DISCONNECTED, null);
    }

    /* HANDLER - DETERMINES WHAT WE DO WITH AN INCOMING MESSAGE FROM BLUETOOTH SERVICE */
    class BluetoothConfigHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch(msg.what) {
                case BluetoothService.CONN_STATUS_UPDATED:
                    // TODO check if object is right type
                    updateBluetoothStatus(msg.arg1, (BluetoothDevice) msg.obj);
                    break;
                case BluetoothService.NEW_INSTRUCTION_IN:
                    BluetoothInstruction returnInstruction = (BluetoothInstruction) msg.obj;
                    if(mBluetoothService.getConnectionStatus() == BluetoothService.STATUS_CONNECTING) {
                        // this is our first test ping being returned
                        // check that data from ping was preserved
                        if(returnInstruction.int1 == mBluetoothService.lastSentInstruction.int1
                        && returnInstruction.int2 == mBluetoothService.lastSentInstruction.int2) {
                            // use null BluetoothDevice to signal that device hasn't changed
                            updateBluetoothStatus(BluetoothService.STATUS_CONNECTED, null);
                            long latency = System.currentTimeMillis() - mBluetoothService.lastSentInstructionTime;
                            Log.d("LATENCY", "Latency from ping was " + latency + " ms");
                        }
                        else {

                            Log.d("PING_VALUE_BAD", ConnectedThread.bytesToHex(returnInstruction.convertInstructionToBytes()));
                        }
                    }
                    break;
                case BluetoothService.NEW_INSTRUCTION_CORRUPTED:
                    Log.d("INST_CORRUPTED", "Received a corrupted instruction!");
                    // TODO
                    break;
            }
        }
    }

    /* HANDLING BLUETOOTH STATUS CHANGES */

    private void updateBluetoothStatus(int status, BluetoothDevice device) {
        // updates bluetooth status info in BT Service
        mBluetoothService.setConnectionStatus(status);
        if(device != null) mBluetoothService.connectedDevice = device; // only update if non-null
        // now update the text on screen
        onBluetoothStatusChange();
    }

    @SuppressLint("SetTextI18n")
    private void onBluetoothStatusChange() {
        // When we change the bluetooth status (in service), make these changes to UI
        switch(mBluetoothService.getConnectionStatus()) {
            case BluetoothService.STATUS_OFF:
                mBluetoothStatusText.setText("Bluetooth Disabled");
                mBluetoothStatusText.setTextColor(
                        getApplicationContext().getColor(R.color.red_disconnected));
                // Disable "disconnect" button
                mDisconnectButton.setEnabled(false);
                mDisconnectButton.getBackground().setAlpha(127);
                // Make sure ListView and title are visible
                mDevicesListViewTitle.setVisibility(View.VISIBLE);
                mDevicesListView.setEnabled(true);
                mDevicesListView.setVisibility(View.VISIBLE);
                break;
            case BluetoothService.STATUS_DISCONNECTED:
                mBluetoothStatusText.setText("Disconnected");
                mBluetoothStatusText.setTextColor(
                        getApplicationContext().getColor(R.color.red_disconnected));
                // Disable "disconnect" button
                mDisconnectButton.setEnabled(false);
                mDisconnectButton.getBackground().setAlpha(127);
                // Make sure ListView and title are visible
                mDevicesListViewTitle.setVisibility(View.VISIBLE);
                mDevicesListView.setEnabled(true);
                mDevicesListView.setVisibility(View.VISIBLE);
                break;
            case BluetoothService.STATUS_CONNECTING:
                mBluetoothStatusText.setText(
                        "Connecting to " + mBluetoothService.connectedDevice.getName());
                mBluetoothStatusText.setTextColor(
                        getApplicationContext().getColor(R.color.orange_connecting));
                // don't worry about other UI for now
                break;
            case BluetoothService.STATUS_CONNECTED:
                // Make text say "connected"
                mBluetoothStatusText.setText(
                        "Connected to " + mBluetoothService.connectedDevice.getName());
                mBluetoothStatusText.setTextColor(
                        getApplicationContext().getColor(R.color.green_accent));
                // Enable "disconnect" button
                mDisconnectButton.setEnabled(true);
                mDisconnectButton.getBackground().setAlpha(255);
                // Vanish ListView and title TODO grey out instead
                mDevicesListViewTitle.setVisibility(View.INVISIBLE);
                mDevicesListView.setEnabled(false);
                mDevicesListView.setVisibility(View.INVISIBLE);
                break;
        }
    }

    /* WHAT DO WHEN RETURNING FROM OTHER ACTIVITIES */
    /* unnecessary due to how we set up onResume
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent Data) {
        super.onActivityResult(requestCode, resultCode, Data);
        // Determine what activity just returned using requestCode we used to launch it
        if (requestCode == REQUEST_ENABLE_BT) {
            // We are returning from the built-in "turn on bluetooth?" activity
            if (resultCode == RESULT_OK) {
                // The user selected "ok" so bluetooth should now be enabled
                mBluetoothStatusText.setText("Bluetooth Enabled");
                // DO ANYTHING HERE IF YOU NEED TO IN THE FUTURE
            }
            else {
                // User did not select 'ok' so bluetooth should still be off.
                mBluetoothStatusText.setText("Bluetooth Disabled");
                // DO ANYTHING HERE IF YOU NEED TO IN THE FUTURE
            }
        }
    }*/

}