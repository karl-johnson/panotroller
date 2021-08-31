package com.example.panotroller;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;

public class PanoSetup extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pano_setup);
        // setup action bar
        setTitle("Panotroller");
        
    }
}