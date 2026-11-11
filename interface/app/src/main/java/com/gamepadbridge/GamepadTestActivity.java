package com.gamepadbridge;

import android.os.Bundle;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import com.gamepadbridge.ui.Xbox360ControllerView;

public class GamepadTestActivity extends AppCompatActivity {

    private Xbox360ControllerView xboxView;
    private TextView tvPressedButton;
    private TextView tvCurrentMappingPrompt;
    private View badgeOverlay;
    private TextView tvBadgeText;
    private TextView tvLTLabel, tvRTLabel;
    private ProgressBar progressLT, progressRT;

    private String currentMappingTarget = null; // Set to "A", "RT", "R3" etc when in mapping step

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_xbox_dashboard);

        xboxView = findViewById(R.id.xboxControllerView);
        tvPressedButton = findViewById(R.id.tvPressedButton);
        tvCurrentMappingPrompt = findViewById(R.id.tvCurrentMappingPrompt);
        badgeOverlay = findViewById(R.id.badgeOverlay);
        tvBadgeText = findViewById(R.id.tvBadgeText);
        tvLTLabel = findViewById(R.id.tvLTLabel);
        tvRTLabel = findViewById(R.id.tvRTLabel);
        progressLT = findViewById(R.id.progressLT);
        progressRT = findViewById(R.id.progressRT);

        // Check if intent demands mapping mode
        if (getIntent().hasExtra("MAPPING_TARGET")) {
            startMappingMode(getIntent().getStringExtra("MAPPING_TARGET"));
        }
    }

    public void startMappingMode(String targetButtonName) {
        this.currentMappingTarget = targetButtonName;
        tvCurrentMappingPrompt.setText("Pressione o botão " + targetButtonName + " no seu gamepad físico");
        tvBadgeText.setText("Pressione " + targetButtonName);
        badgeOverlay.setVisibility(View.VISIBLE);
        xboxView.setTargetMappingButton(targetButtonName);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if ((event.getSource() & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
            (event.getSource() & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK) {

            String btnName = parseKeyCodeToXboxName(keyCode);
            tvPressedButton.setText(btnName + " (KeyCode: " + keyCode + ")");
            xboxView.updateButtonState(btnName, true);

            if (currentMappingTarget != null) {
                // Return mapped keycode back to bridge engine
                saveMappingAndFinish(btnName, keyCode);
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        String btnName = parseKeyCodeToXboxName(keyCode);
        xboxView.updateButtonState(btnName, false);
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if ((event.getSource() & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK &&
                event.getAction() == MotionEvent.ACTION_MOVE) {

            // Process Left Analog Stick
            float lsX = event.getAxisValue(MotionEvent.AXIS_X);
            float lsY = event.getAxisValue(MotionEvent.AXIS_Y);

            // Process Right Analog Stick
            float rsX = event.getAxisValue(MotionEvent.AXIS_Z);
            float rsY = event.getAxisValue(MotionEvent.AXIS_RZ);
            if (rsX == 0 && rsY == 0) {
                rsX = event.getAxisValue(MotionEvent.AXIS_RX);
                rsY = event.getAxisValue(MotionEvent.AXIS_RY);
            }

            xboxView.updateAnalogSticks(lsX, lsY, rsX, rsY);

            // Process Triggers (LT & RT)
            float lt = event.getAxisValue(MotionEvent.AXIS_LTRIGGER);
            if (lt == 0) lt = event.getAxisValue(MotionEvent.AXIS_BRAKE);

            float rt = event.getAxisValue(MotionEvent.AXIS_RTRIGGER);
            if (rt == 0) rt = event.getAxisValue(MotionEvent.AXIS_GAS);

            xboxView.updateTriggers(lt, rt);

            int ltPercent = (int) (lt * 100);
            int rtPercent = (int) (rt * 100);
            progressLT.setProgress(ltPercent);
            progressRT.setProgress(rtPercent);
            tvLTLabel.setText("Gatilho LT: " + ltPercent + "%");
            tvRTLabel.setText("Gatilho RT: " + rtPercent + "%");

            return true;
        }
        return super.onGenericMotionEvent(event);
    }

    private String parseKeyCodeToXboxName(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BUTTON_A: return "A";
            case KeyEvent.KEYCODE_BUTTON_B: return "B";
            case KeyEvent.KEYCODE_BUTTON_X: return "X";
            case KeyEvent.KEYCODE_BUTTON_Y: return "Y";
            case KeyEvent.KEYCODE_BUTTON_L1: return "LB";
            case KeyEvent.KEYCODE_BUTTON_R1: return "RB";
            case KeyEvent.KEYCODE_BUTTON_THUMBL: return "L3";
            case KeyEvent.KEYCODE_BUTTON_THUMBR: return "R3";
            case KeyEvent.KEYCODE_BUTTON_START: return "START";
            case KeyEvent.KEYCODE_BUTTON_SELECT: return "BACK";
            case KeyEvent.KEYCODE_BUTTON_MODE: return "GUIDE";
            case KeyEvent.KEYCODE_DPAD_UP: return "DPAD_UP";
            case KeyEvent.KEYCODE_DPAD_DOWN: return "DPAD_DOWN";
            case KeyEvent.KEYCODE_DPAD_LEFT: return "DPAD_LEFT";
            case KeyEvent.KEYCODE_DPAD_RIGHT: return "DPAD_RIGHT";
            default: return "BTN_" + keyCode;
        }
    }

    private void saveMappingAndFinish(String name, int keyCode) {
        // Save mapping callback logic...
        finish();
    }
}