package com.example.panotroller;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.DragEvent;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.constraintlayout.widget.ConstraintLayout;

import com.google.android.material.slider.Slider;

public class SliderSettingsModule extends ConstraintLayout {

    /* UI OBJECTS */
    private EditText valueText;
    private TextView titleText;
    private ImageButton incButton;
    private ImageButton decButton;
    private Slider slider;

    /* MEMBERS */
    private TypedArray attributes; // attributes from AttributeSet

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
        View thisView = li.inflate (R.layout.slider_settings_module, this, true);
        // assign UI objects
        valueText = thisView.findViewById(R.id.slider_mod_value);
        titleText = thisView.findViewById(R.id.slider_mod_title);
        incButton = thisView.findViewById(R.id.slider_mod_inc);
        decButton = thisView.findViewById(R.id.slider_mod_dec);
        slider = thisView.findViewById(R.id.slider_mod_slider);
        // get attributes from AttributeSet
        attributes=context.obtainStyledAttributes(attrs, R.styleable.SliderSettingsModule);
        // apply attributes
        titleText.setText(attributes.getString(R.styleable.SliderSettingsModule_titleText));
        slider.setValueFrom(attributes.getFloat(R.styleable.SliderSettingsModule_valueFrom, 0));
        slider.setValueTo(attributes.getFloat(R.styleable.SliderSettingsModule_valueTo, 100));
        slider.setValue(attributes.getFloat(R.styleable.SliderSettingsModule_value, 50));
        valueText.setText(String.valueOf(slider.getValue()));
        slider.setStepSize(attributes.getFloat(R.styleable.SliderSettingsModule_stepSize, 1));
        // set up listeners for slider and buttons
        incButton.setOnTouchListener(this::onIncButton);
        decButton.setOnTouchListener(this::onDecButton);
        slider.setOnTouchListener(this::onSliderTouch);
        attributes.recycle();
        // we recycle this in onFinishInflate!
    }

    private boolean onSliderTouch(View view, MotionEvent motionEvent) {
        //slider.dispatchTouchEvent(motionEvent); // ensures normal touch listener actions are called
        switch(motionEvent.getAction()) {
            case MotionEvent.ACTION_DOWN:
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            case MotionEvent.ACTION_UP:
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                // slider was released, update EditText now
                valueText.setText(String.valueOf(slider.getValue()));
        }
        return false;
    }


    private boolean onIncButton(View view, MotionEvent motionEvent) {
        if(motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
            // always do haptic feedback, even if value isn't actually incremented
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            // increment value by step size, if there's room
            float newVal = slider.getValue() + slider.getStepSize();
            setValueTo(newVal);
            return true;
        }
        return false;
    }

    private boolean onDecButton(View view, MotionEvent motionEvent) {
        if(motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
            // always do haptic feedback, even if value isn't actually incremented
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            // decrement value by step size, if there's room
            float newVal = slider.getValue() - slider.getStepSize();
            setValueTo(newVal);
            return true;
        }
        return false;
    }

    public void setValueTo(float newVal) {
        // sets slider value and text, with checks + rounding to step size
        float roundedNewVal = roundToNearest(newVal, slider.getStepSize());
        if(roundedNewVal >= slider.getValueFrom() && roundedNewVal <= slider.getValueTo()) {
            slider.setValue(roundedNewVal);
            valueText.setText(String.valueOf(roundedNewVal));
        }
    }

    private float roundToNearest(float in, float roundTo) {
        return roundTo*Math.round(in/roundTo);
    }

    public float getValue() {
        return slider.getValue();
    }


}
