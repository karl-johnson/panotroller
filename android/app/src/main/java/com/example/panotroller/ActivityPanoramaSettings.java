package com.example.panotroller;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ActivityPanoramaSettings extends AppCompatActivity {

    /* UI MEMBERS */
    private SliderSettingsModule focalSlider;
    private SliderSettingsModule overlapSlider;
    private SliderSettingsModule settleSlider;
    private SliderSettingsModule exposureSlider;
    private Spinner cameraSpinner;
    private Panorama.PanoramaSettings priorSettings;
    private String selectedCameraKey = null;
    // ehhhhhhhhh not great
    private final static String DEFAULT_CAMERA_KEY = "Canon 5D Mark II";

    private final AdapterView.OnItemSelectedListener itemSelectedListener = new AdapterView.OnItemSelectedListener() {
        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            selectedCameraKey = (String) parent.getItemAtPosition(position);
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {
            cameraSpinner.setSelection(0);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // apply layout
        setContentView(R.layout.activity_pano_settings);
        // assign UI members
        focalSlider = findViewById(R.id.focalSlider);
        overlapSlider = findViewById(R.id.overlapSlider);
        settleSlider = findViewById(R.id.settleSlider);
        exposureSlider = findViewById(R.id.exposureSlider);
        cameraSpinner = findViewById(R.id.cameraSpinner);
        // set up camera spinner
        ArrayList<String> cameraList = new ArrayList<>(Panorama.builtInCameras.keySet());
        ArrayAdapter<String> cameraSpinnerAdapter = new ArrayAdapter<>(
                this, R.layout.camera_spinner, cameraList);
        cameraSpinnerAdapter.setDropDownViewResource(R.layout.camera_spinner_item);
        cameraSpinner.setAdapter(cameraSpinnerAdapter);
        cameraSpinner.setOnItemSelectedListener(itemSelectedListener);
        Log.d("PANO_SETTINGS", "Default: " + Panorama.builtInCameras.get(DEFAULT_CAMERA_KEY));
        //cameraSpinner.setSelection(cameraSpinnerAdapter.getPosition(Panorama.builtInCameras.get(DEFAULT_CAMERA_KEY)));
        //selectedCamera = Panorama.builtInCameras.get(DEFAULT_CAMERA_KEY);
        // set UI members based on existing settings
        Intent intentIn = getIntent();
        priorSettings = new Panorama.PanoramaSettings(intentIn.getBundleExtra("CURRENT_SETTINGS"));
        if(priorSettings != null) {
            focalSlider.setValueTo(priorSettings.camera.focalLength);
            overlapSlider.setValueTo(priorSettings.overlap * 100);
            settleSlider.setValueTo((float) priorSettings.settleTime / 1000f);
            exposureSlider.setValueTo((float) priorSettings.exposureTime / 1000f);
            //cameraSpinner.setSelection(cameraSpinnerAdapter.getPosition(Panorama.builtInCameras.get(DEFAULT_CAMERA_KEY)));
            //selectedCamera = Panorama.builtInCameras.get(DEFAULT_CAMERA_KEY);
            // fixed issue in here where different focal length changes camera object, so it's not
            // in builtInCameras. Did this with a hack job where the key string is the same as the
            // display name - the correct way is to have the camera body being a separate object
            // from the lens that's on it (or nested)
            Log.d("PANO_SETTINGS", "Loaded: " + cameraSpinnerAdapter.getPosition(priorSettings.camera.toString()));
            cameraSpinner.setSelection(cameraSpinnerAdapter.getPosition(priorSettings.camera.toString()), true);
            // need to put this in because Android drawing bug or something
            // cameraSpinnerAdapter.notifyDataSetChanged();
            selectedCameraKey = priorSettings.camera.toString();
        }
        else {
            //cameraSpinner.setSelection(cameraSpinnerAdapter.getPosition(Panorama.builtInCameras.get(DEFAULT_CAMERA_KEY)));
            //selectedCamera = Panorama.builtInCameras.get(DEFAULT_CAMERA_KEY);
        }

        // setup action bar
        setTitle("Panorama Settings");
        Toolbar thisToolbar = findViewById(R.id.pano_settings_toolbar);
        setSupportActionBar(thisToolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true); // add back button to action bar
    }

    @Override
    public void onBackPressed() {
        setResult(RESULT_CANCELED, null);
        super.onBackPressed();
    }

    private void acceptSettings() {
        // safest place to set result IMO
        // first built panorama settings object from UI state
        Panorama.PanoramaSettings settings = new Panorama.PanoramaSettings();
        // TODO allow camera selection
        settings.camera = Panorama.builtInCameras.get(selectedCameraKey);
        settings.camera.focalLength = focalSlider.getValue();
        settings.overlap = overlapSlider.getValue()/100; // convert from % to decimal
        // both times are in float seconds for user but short ms internally
        settings.settleTime = (short) (1000 * settleSlider.getValue());
        settings.exposureTime = (short) (1000 * exposureSlider.getValue());
        // TODO put camera + configuration things in here too once implemented
        Intent returnIntent = new Intent();
        returnIntent.putExtra("NEW_SETTINGS", settings.writeToBundle());
        setResult(RESULT_OK, returnIntent);
        finish();
    }

    /* TOOLBAR SETUP METHODS */
    @Override
    // what to do when buttons in toolbar are pressed
    public boolean onOptionsItemSelected(MenuItem item) {
        // make back button actually go back
        if (item.getItemId() == android.R.id.home) {
            onBackPressed(); // same behavior as back button
            return true;
        }
        else if(item.getItemId() == R.id.pano_settings_OK_button) {
            acceptSettings();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.pano_settings_toolbar_menu, menu);
        return true;
    }



}
