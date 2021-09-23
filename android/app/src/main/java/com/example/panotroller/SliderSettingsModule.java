package com.example.panotroller;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;

public class SliderSettingsModule extends ConstraintLayout {

    // make these two constructors reference the main one

    public SliderSettingsModule(Context context) {
        this(context, null);
    }

    public SliderSettingsModule(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs);
    }

    // constructor to create view from XML attributes

    public SliderSettingsModule(Context context, AttributeSet attrs) {
        super(context, attrs);
    }




    public float getValue() {
        // returns value which this slider module is currently adjusted to
        return 0;
    }


}
