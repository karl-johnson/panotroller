package com.example.panotroller;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NavUtils;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BluetoothConfig extends AppCompatActivity {

    private ListView mDevicesListView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // apply layout
        setContentView(R.layout.activity_bluetooth_config);
        // configure action bar
        setTitle("Bluetooth Config");
        getSupportActionBar().setDisplayHomeAsUpEnabled(true); // add back button to action bar

        // configure bluetooth device list
        // variable for us to refer to the list view in the future
        mDevicesListView = (ListView)findViewById(R.id.devicesListView);
        // adapter which handles how to put string data into list items (including formatting!)
        // PLACEHOLDER ITEMS
        List<String> deviceList = new ArrayList<String>();
        for( int i = 1; i <= 10; i++ )
            deviceList.add(String.format( "Device %d", i ));
        // \PLACEHOLDER ITEMS

        DeviceListAdapter mAdapter = new DeviceListAdapter(this, deviceList);
        mDevicesListView.setAdapter(new ArrayAdapter<String>(
                this, R.layout.device_item, R.id.name, deviceList)); // assign adapter to our list view
        // listener to determine what should be done when an item is clicked on
        // mDevicesListView.setOnItemClickListener(mDeviceClickListener);
    }

    /*
        DEVICE LIST RENDERING
     */
    public class DeviceListAdapter extends BaseAdapter {
        private final Context context;
        private final List<String> items;
        public DeviceListAdapter(Context context, List<String> items ) {
            this.context = context;
            this.items = items;
        }
        @Override
        public int getCount() {
            return items.size();
        }
        @Override
        public Object getItem( int position ) {
            return items.get( position );
        }
        @Override
        public long getItemId( int position ) {
            return getItem( position ).hashCode();
        }
        // actual meat of the adapter - how to set formatting based on the position we're given
        public View getView(int position, View convertView, ViewGroup parent) {
            // optimization thing involves recycling an old item convertView if we're given it
            View v = convertView;
            if ( v == null ) { // if we aren't given a convertView to recycle, inflate a new one
                LayoutInflater inflater = (LayoutInflater) context
                        .getSystemService( Context.LAYOUT_INFLATER_SERVICE );
                v = inflater.inflate( R.layout.device_item, parent, false );
            }
            final String item = (String) getItem( position );
            ((TextView) v.findViewById(R.id.name)).setText(item);
            return v;
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // make back button actually go back
        if (item.getItemId() == android.R.id.home) {
            NavUtils.navigateUpFromSameTask(this);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}