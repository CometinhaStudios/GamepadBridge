package com.gamepadbridge.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Custom View renderizando a ilustração exata do controle Xbox 360.
 * Suporta o deslocamento real dos analógicos (físico -> virtual),
 * brilho pulsante nos botões durante o mapeamento (ex: "Pressione A, RT, R3") e o feedback visual de botões pressionados.
 */
public class Xbox360ControllerView extends View {

    // Tint Paints
    private Paint bodyPaint;
    private Paint borderPaint;
    private Paint darkInnerPaint;
    private Paint guideGreenPaint;
    private Paint btnAPaint, btnBPaint, btnXPaint, btnYPaint;
    private Paint dpadPaint;
    private Paint stickPaint;
    private Paint activeGlowPaint;
    private Paint textPaint;

    // Analog Stick Offsets (-1.0f to 1.0f)
    private float leftStickX = 0f, leftStickY = 0f;
    private float rightStickX = 0f, rightStickY = 0f;

    // Trigger Values (0.0f to 1.0f)
    private float triggerLT = 0f, triggerRT = 0f;

    // Active Pressed / Highlighted States
    private final Map<String, Boolean> buttonPressedState = new HashMap<>();
    private String targetMappingButton = null; // Ex: "A", "RT", "R3", "LB", "DPAD_UP"
    private float pulseAlpha = 1.0f;
    private ValueAnimator pulseAnimator;

    public Xbox360ControllerView(Context context) {
        super(context);
        init();
    }

    public Xbox360ControllerView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        bodyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bodyPaint.setColor(Color.parseColor("#ECECEC")); // Shell White
        bodyPaint.setStyle(Paint.Style.FILL);

        borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setColor(Color.parseColor("#4A5255")); // Dark Edge Outline
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(6f);

        darkInnerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        darkInnerPaint.setColor(Color.parseColor("#343A40"));
        darkInnerPaint.setStyle(Paint.Style.FILL);

        guideGreenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        guideGreenPaint.setColor(Color.parseColor("#9BF00B")); // Xbox Green Guide
        guideGreenPaint.setStyle(Paint.Style.FILL);

        btnAPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        btnAPaint.setColor(Color.parseColor("#5A9E32")); // Green A
        btnAPaint.setStyle(Paint.Style.FILL);

        btnBPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        btnBPaint.setColor(Color.parseColor("#C23B22")); // Red B
        btnBPaint.setStyle(Paint.Style.FILL);

        btnXPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        btnXPaint.setColor(Color.parseColor("#2B59C3")); // Blue X
        btnXPaint.setStyle(Paint.Style.FILL);

        btnYPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        btnYPaint.setColor(Color.parseColor("#F18F01")); // Yellow Y
        btnYPaint.setStyle(Paint.Style.FILL);

        dpadPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dpadPaint.setColor(Color.parseColor("#2A3135"));
        dpadPaint.setStyle(Paint.Style.FILL);

        stickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        stickPaint.setColor(Color.parseColor("#3C4347"));
        stickPaint.setStyle(Paint.Style.FILL);

        activeGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        activeGlowPaint.setColor(Color.parseColor("#00FF66")); // Neon Glow
        activeGlowPaint.setStyle(Paint.Style.FILL);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(24f);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);

        // Pulse Animation for Mapping Mode
        pulseAnimator = ValueAnimator.ofFloat(0.3f, 1.0f);
        pulseAnimator.setDuration(600);
        pulseAnimator.setRepeatMode(ValueAnimator.REVERSE);
        pulseAnimator.setRepeatCount(ValueAnimator.INFINITE);
        pulseAnimator.addUpdateListener(animation -> {
            pulseAlpha = (float) animation.getAnimatedValue();
            invalidate();
        });
    }

    public void setTargetMappingButton(String buttonName) {
        this.targetMappingButton = buttonName;
        if (buttonName != null) {
            if (!pulseAnimator.isRunning()) pulseAnimator.start();
        } else {
            if (pulseAnimator.isRunning()) pulseAnimator.stop();
        }
        invalidate();
    }

    public void updateButtonState(String buttonName, boolean pressed) {
        buttonPressedState.put(buttonName, pressed);
        invalidate();
    }

    public void updateAnalogSticks(float lx, float ly, float rx, float ry) {
        this.leftStickX = lx;
        this.leftStickY = ly;
        this.rightStickX = rx;
        this.rightStickY = ry;
        invalidate();
    }

    public void updateTriggers(float lt, float rt) {
        this.triggerLT = lt;
        this.triggerRT = rt;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float width = getWidth();
        float height = getHeight();
        float scale = Math.min(width / 500f, height / 350f);

        canvas.save();
        canvas.translate((width - 500f * scale) / 2f, (height - 350f * scale) / 2f);
        canvas.scale(scale, scale);

        // Main Controller Body Base Path
        RectF bodyRect = new RectF(50, 40, 450, 310);
        canvas.drawRoundRect(bodyRect, 80, 80, bodyPaint);
        canvas.drawRoundRect(bodyRect, 80, 80, borderPaint);

        // Guide Button (Center Xbox Logo)
        boolean guideActive = Boolean.TRUE.equals(buttonPressedState.get("GUIDE")) || "GUIDE".equals(targetMappingButton);
        if (guideActive) {
            activeGlowPaint.setAlpha((int) (pulseAlpha * 255));
            canvas.drawCircle(250, 100, 26, activeGlowPaint);
        }
        canvas.drawCircle(250, 100, 22, guideGreenPaint);

        // Left Stick Base & Dynamic Cap Movement
        float lsCenterX = 140 + (leftStickX * 18);
        float lsCenterY = 110 + (leftStickY * 18);
        canvas.drawCircle(140, 110, 35, darkInnerPaint);
        boolean l3Active = Boolean.TRUE.equals(buttonPressedState.get("L3")) || "L3".equals(targetMappingButton);
        canvas.drawCircle(lsCenterX, lsCenterY, 30, l3Active ? activeGlowPaint : stickPaint);

        // Right Stick Base & Dynamic Cap Movement
        float rsCenterX = 310 + (rightStickX * 18);
        float rsCenterY = 190 + (rightStickY * 18);
        canvas.drawCircle(310, 190, 35, darkInnerPaint);
        boolean r3Active = Boolean.TRUE.equals(buttonPressedState.get("R3")) || "R3".equals(targetMappingButton);
        canvas.drawCircle(rsCenterX, rsCenterY, 30, r3Active ? activeGlowPaint : stickPaint);

        // D-Pad Cross
        canvas.drawRect(180, 160, 220, 220, dpadPaint);
        canvas.drawRect(170, 180, 230, 200, dpadPaint);

        // ABXY Action Buttons Rendering
        drawActionButton(canvas, 400, 130, 18, btnAPaint, "A");
        drawActionButton(canvas, 435, 95, 18, btnBPaint, "B");
        drawActionButton(canvas, 365, 95, 18, btnXPaint, "X");
        drawActionButton(canvas, 400, 60, 18, btnYPaint, "Y");

        // Top Triggers / Bumpers Visual Feedback (LT / RT / LB / RB)
        drawTopBumper(canvas, 100, 20, "LB", "LT", triggerLT);
        drawTopBumper(canvas, 400, 20, "RB", "RT", triggerRT);

        canvas.restore();
    }

    private void drawActionButton(Canvas canvas, float cx, float cy, float radius, Paint basePaint, String name) {
        boolean isPressed = Boolean.TRUE.equals(buttonPressedState.get(name));
        boolean isTarget = name.equalsIgnoreCase(targetMappingButton);

        if (isTarget) {
            activeGlowPaint.setAlpha((int) (pulseAlpha * 255));
            canvas.drawCircle(cx, cy, radius + 6, activeGlowPaint);
        } else if (isPressed) {
            canvas.drawCircle(cx, cy, radius + 4, activeGlowPaint);
        }

        canvas.drawCircle(cx, cy, radius, basePaint);
        canvas.drawText(name, cx, cy + 8, textPaint);
    }

    private void drawTopBumper(Canvas canvas, float cx, float cy, String lbRb, String ltRt, float triggerVal) {
        boolean lbPressed = Boolean.TRUE.equals(buttonPressedState.get(lbRb)) || lbRb.equalsIgnoreCase(targetMappingButton);
        boolean ltTarget = ltRt.equalsIgnoreCase(targetMappingButton);

        Paint bPaint = (lbPressed || ltTarget || triggerVal > 0.1f) ? activeGlowPaint : darkInnerPaint;
        canvas.drawRoundRect(new RectF(cx - 35, cy, cx + 35, cy + 18), 6, 6, bPaint);
        canvas.drawText(lbRb + "/" + ltRt, cx, cy + 14, textPaint);
    }
}