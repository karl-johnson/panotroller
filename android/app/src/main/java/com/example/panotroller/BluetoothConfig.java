package com.example.panotroller;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.NavUtils;
import androidx.core.content.ContextCompat;

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
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BluetoothConfig extends AppCompatActivity {

    /* CONSTANTS */
    // request code used to identify that the bluetooth on/off activity was launched
    private final static int REQUEST_ENABLE_BT = 1;

    /* DECLARE UI OBJECTS */
    private TextView mBluetoothStatusText;
    //private Button mDiscoverButton;
    private Button mDisconnectButton;
    private ListView mDevicesListView;

    /* BLUETOOTH RELATED OBJECTS */

    private BluetoothService mBluetoothService; //
    private BluetoothAdapter mBTAdapter = BluetoothAdapter.getDefaultAdapter();
    private Handler mHandler = new BluetoothConfigHandler(); // Handler to deal with information coming back over BT connection
    private boolean mShouldUnbind = false; // Tracks whether unbinding is necessary on act. exit

    /* OTHER MEMBERS */
    private Set<BluetoothDevice> mPairedDevices; // set to keep track of all paired devices
    private List<String> mPairedDeviceNames = new ArrayList<String>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // apply layout
        setContentView(R.layout.activity_bluetooth_config);

        /* ASSIGN UI OBJECTS */
        mBluetoothStatusText = (TextView) findViewById(R.id.bluetoothStatus);
        //mDiscoverButton = (Button) findViewById(R.id.discover);
        mDisconnectButton = (Button) findViewById(R.id.disconnect);
        mDevicesListView = (ListView)findViewById(R.id.devicesListView);

        /* CONFIGURE ACTION BAR */
        setTitle("Bluetooth Config");
        Toolbar thisToolbar = (Toolbar) findViewById(R.id.bt_setup_toolbar);
        setSupportActionBar(thisToolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true); // add back button to action bar


    }

    public void onStart() {
        super.onStart();
        Intent BTServiceIntent = new Intent(this, BluetoothService.class);
        getApplicationContext().bindService(
                BTServiceIntent, mServiceConnection, Context.BIND_AUTO_CREATE);
        mShouldUnbind = true;
        if(mBluetoothService != null) {
            Log.d("BT_SERVICE_EXISTS", "BT Service exists");
            Toast.makeText(getApplicationContext(),
                    "Bluetooth Service Exists", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onResume() {
        // this is the last thing called before the activity is active

        /* FUNDAMENTAL BLUETOOTH TASKS */
        // turn on bluetooth if the user has it turned off
        super.onResume();
        mBluetoothStatusText.setText("");
        if(!mBTAdapter.isEnabled()) {
            // if it is off, launch built-in activity to turn on bluetooth
            // onResume is called upon returning from ACTION_REQUEST_ENABLE so this will keep
            // appearing until bluetooth is enabled!
            mBluetoothStatusText.setText("Bluetooth Disabled");
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            // this is non-blocking!
        }
        if(mBTAdapter.isEnabled()) {// things to do once BT adapter is enabled
            // set proper text
            mBluetoothStatusText.setText("Bluetooth Enabled");
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
        // if we are not currently connected to a device, disable "disconnect" button

    }

    protected void onDestroy() {
        super.onDestroy();
        if (mShouldUnbind) {
            // Release information about the service's state.
            getApplicationContext().unbindService(mServiceConnection);
            mShouldUnbind = false;
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
        }
        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            // don't need to do anything but apparently we need to say this anyways
        }
    };

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
                // there was an issue and we didn't find the clicked device??
                Toast.makeText(getBaseContext(),
                        "Couldn't find clicked device!", Toast.LENGTH_SHORT).show();
            }
            // Update GUI to say we're connecting
            mBluetoothStatusText.setText("Connecting to " + clickedDevice.getName());
            mBluetoothStatusText.setTextColor(
                    getApplicationContext().getColor(R.color.orange_connecting));

            // Spawn a thread to handle the blocking bluetooth connection process
            new Thread() {
                public void run() {
                    boolean fail = false;
                    try {
                        mBluetoothService.createBluetoothSocket(clickedDevice);
                    } catch (IOException e) {
                        fail = true;
                        Toast.makeText(getBaseContext(), "Socket creation failed", Toast.LENGTH_SHORT).show();
                    }
                    // Establish the Bluetooth socket connection.
                    try {
                        mBluetoothService.getBluetoothSocket().connect();
                    } catch (IOException e) {
                        try {
                            // this exception means the socket creation failed
                            fail = true;
                            mBluetoothService.getBluetoothSocket().close();
                            // send message out of this thread using same handler as the service
                            mHandler.obtainMessage(
                                    BluetoothService.CONN_STATUS_UPDATED,
                                    -1, -1).sendToTarget();
                        } catch (IOException e2) {
                            //insert code to deal with this
                            Toast.makeText(getBaseContext(),
                                    "Socket creation failed", Toast.LENGTH_SHORT).show();
                        }
                    }
                    if(!fail) {
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
                        mHandler.obtainMessage(
                                BluetoothService.CONN_STATUS_UPDATED,
                                1, -1, clickedDevice.getName()).sendToTarget();
                    }
                }
            }.start();
        }
    };

    /* HANDLER - DETERMINES WHAT WE DO WITH AN INCOMING MESSAGE FROM BLUETOOTH SERVICE */
    class BluetoothConfigHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch(msg.what) {
                case BluetoothService.CONN_STATUS_UPDATED:
                    switch(msg.arg1) {
                        case BluetoothService.STATUS_DISCONNECTED:
                            mBluetoothStatusText.setText("Disconnected");
                            mBluetoothStatusText.setTextColor(
                                    getApplicationContext().getColor(R.color.red_disconnected));
                            break;
                        case BluetoothService.STATUS_CONNECTED:
                            mBluetoothStatusText.setText("Connected to " + msg.obj);
                            mBluetoothStatusText.setTextColor(
                                    getApplicationContext().getColor(R.color.green_accent));
                            break;
                    }
                    break;
                case BluetoothService.NEW_INSTRUCTION_IN:
                    // TODO
                    break;
                case BluetoothService.NEW_INSTRUCTION_CORRUPTED:
                    // TODO
                    break;
            }
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