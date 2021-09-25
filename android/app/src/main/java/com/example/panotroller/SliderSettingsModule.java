package com.example.panotroller;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.LayoutInflater;
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

        LayoutInflater li = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        li.inflate (R.layout.slider_settings_module, this, true);
        // get and apply attributes from AttributeSet
        TypedArray a=context.obtainStyledAttributes(attrs, R.styleable.SliderSettingsModule);

        //


        a.recycle();

    }




    public float getValue() {
        // returns value which this slider module is currently adjusted to
        return 0;
    }


}
