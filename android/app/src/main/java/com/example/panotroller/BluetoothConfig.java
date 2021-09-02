package com.example.panotroller;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.NavUtils;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

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

    /* OTHER OBJECTS */
    Set<BluetoothDevice> mPairedDevices; // set to keep track of all paired devices
    List<String> mPairedDeviceNames = new ArrayList<String>();

    // object which lets us do most Bluetooth-related tasks
    BluetoothAdapter mBTAdapter = BluetoothAdapter.getDefaultAdapter();

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
            for(BluetoothDevice device : mPairedDevices)
                mPairedDeviceNames.add(device.getName());
            if(mPairedDevices.size() == 0) {
                // no paired devices, oh no!
                mPairedDeviceNames.add("No paired devices!");
            }
            // use a simple ArrayAdapter with this list to populate our listView with device names
            mDevicesListView.setAdapter(new ArrayAdapter<String>(
                    this, R.layout.device_item, R.id.name, mPairedDeviceNames));
            //Log.i("BT_DEVICE_LIST", "mPairedDevices size: " + mPairedDeviceNames.size());
        }
        // if we are not currently connected to a device, disable "disconnect" button

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