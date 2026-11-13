package com.example.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * Custom JARVIS central holographic AI orb featuring:
 * - Soft radial cyan / blue / purple aura glow
 * - Dual counter-rotating circular scanner rings & orbital telemetry arcs
 * - Smooth listening, thinking, and speaking sound-wave animations
 * - Battery-friendly reduced-motion mode
 */
public class JarvisOrbView extends View {

    public enum AssistantState {
        IDLE,
        LISTENING,
        THINKING,
        SPEAKING,
        SLEEPING
    }

    private AssistantState currentState = AssistantState.IDLE;
    private float phase = 0f;
    private float smoothedRms = 0f;
    private boolean batterySaverMode = false;

    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint scannerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outerRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint innerArcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint corePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint wavePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arcBounds = new RectF();
    private ValueAnimator animator;
    private float cachedGlowRadius = -1f;
    private AssistantState cachedGlowState = null;

    public JarvisOrbView(Context context) {
        super(context);
        init();
    }

    public JarvisOrbView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public JarvisOrbView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        glowPaint.setStyle(Paint.Style.FILL);
        scannerPaint.setStyle(Paint.Style.STROKE);
        scannerPaint.setStrokeCap(Paint.Cap.ROUND);
        tickPaint.setStyle(Paint.Style.STROKE);
        tickPaint.setStrokeCap(Paint.Cap.ROUND);
        outerRingPaint.setStyle(Paint.Style.STROKE);
        innerArcPaint.setStyle(Paint.Style.STROKE);
        innerArcPaint.setStrokeCap(Paint.Cap.ROUND);
        corePaint.setStyle(Paint.Style.FILL);
        wavePaint.setStyle(Paint.Style.STROKE);
        wavePaint.setStrokeCap(Paint.Cap.ROUND);

        startAnimator(2000L);
    }

    private void startAnimator(long durationMs) {
        if (animator != null) {
            animator.cancel();
        }
        animator = ValueAnimator.ofFloat(0f, (float) (Math.PI * 2.0));
        animator.setDuration(durationMs);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(animation -> {
            phase = (float) animation.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    public void setBatterySaverMode(boolean enabled) {
        if (this.batterySaverMode != enabled) {
            this.batterySaverMode = enabled;
            startAnimator(enabled ? 3800L : 2000L);
        }
    }

    public void setState(AssistantState state) {
        if (this.currentState != state) {
            this.currentState = state;
            if (state != AssistantState.LISTENING) {
                smoothedRms = 0f;
            }
            cachedGlowState = null;
            invalidate();
        }
    }

    public AssistantState getState() {
        return currentState;
    }

    public void updateAudioRms(float rmsDb) {
        float normalized = Math.max(0f, Math.min(1f, (rmsDb + 2f) / 12f));
        smoothedRms = smoothedRms * 0.6f + normalized * 0.4f;
        invalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (animator != null) {
            animator.cancel();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        float cx = w / 2f;
        float cy = h / 2f;
        float maxRadius = Math.min(w, h) * 0.47f;
        if (maxRadius <= 0f) return;

        float density = getResources().getDisplayMetrics().density;
        drawAmbientAura(canvas, cx, cy, maxRadius);
        drawRotatingScannerRing(canvas, cx, cy, maxRadius * 0.94f, density);

        switch (currentState) {
            case LISTENING:
                drawListening(canvas, cx, cy, maxRadius * 0.80f, density);
                break;
            case THINKING:
                drawThinking(canvas, cx, cy, maxRadius * 0.80f, density);
                break;
            case SPEAKING:
                drawSpeaking(canvas, cx, cy, maxRadius * 0.80f, density);
                break;
            case SLEEPING:
                drawSleeping(canvas, cx, cy, maxRadius * 0.80f, density);
                break;
            case IDLE:
            default:
                drawIdle(canvas, cx, cy, maxRadius * 0.80f, density);
                break;
        }
    }

    private void drawAmbientAura(Canvas canvas, float cx, float cy, float maxRadius) {
        if (cachedGlowRadius != maxRadius || cachedGlowState != currentState) {
            cachedGlowRadius = maxRadius;
            cachedGlowState = currentState;
            int centerColor;
            int midColor;
            if (currentState == AssistantState.SPEAKING) {
                centerColor = Color.argb(110, 168, 85, 247);
                midColor = Color.argb(55, 0, 212, 255);
            } else if (currentState == AssistantState.THINKING) {
                centerColor = Color.argb(110, 124, 58, 237);
                midColor = Color.argb(55, 37, 99, 235);
            } else if (currentState == AssistantState.SLEEPING) {
                centerColor = Color.argb(45, 79, 70, 229);
                midColor = Color.argb(18, 30, 41, 82);
            } else {
                centerColor = Color.argb(105, 0, 212, 255);
                midColor = Color.argb(45, 124, 58, 237);
            }
            glowPaint.setShader(new RadialGradient(
                    cx, cy, maxRadius,
                    new int[]{centerColor, midColor, Color.TRANSPARENT},
                    new float[]{0.15f, 0.62f, 1.0f},
                    Shader.TileMode.CLAMP
            ));
        }
        canvas.drawCircle(cx, cy, maxRadius, glowPaint);
    }

    private void drawRotatingScannerRing(Canvas canvas, float cx, float cy, float radius, float density) {
        float angleDeg = (float) Math.toDegrees(phase);
        arcBounds.set(cx - radius, cy - radius, cx + radius, cy + radius);

        int alpha = currentState == AssistantState.SLEEPING ? 45 : 165;
        scannerPaint.setStrokeWidth(2.0f * density);
        scannerPaint.setColor(Color.argb(alpha, 0, 212, 255));
        canvas.drawArc(arcBounds, angleDeg, 64f, false, scannerPaint);
        canvas.drawArc(arcBounds, angleDeg + 180f, 64f, false, scannerPaint);

        if (!batterySaverMode) {
            float innerR = radius * 0.89f;
            arcBounds.set(cx - innerR, cy - innerR, cx + innerR, cy + innerR);
            scannerPaint.setStrokeWidth(1.5f * density);
            scannerPaint.setColor(Color.argb(alpha, 168, 85, 247));
            canvas.drawArc(arcBounds, -angleDeg * 0.85f + 45f, 48f, false, scannerPaint);
            canvas.drawArc(arcBounds, -angleDeg * 0.85f + 225f, 48f, false, scannerPaint);

            // Subtle scanner radar tick dashes
            tickPaint.setStrokeWidth(1.2f * density);
            tickPaint.setColor(Color.argb(currentState == AssistantState.SLEEPING ? 35 : 90, 56, 189, 248));
            int ticks = 12;
            for (int i = 0; i < ticks; i++) {
                double rad = phase * 0.4 + (i * Math.PI * 2.0 / ticks);
                float rIn = radius * 0.96f;
                float rOut = radius * 1.01f;
                canvas.drawLine(
                        cx + (float) Math.cos(rad) * rIn,
                        cy + (float) Math.sin(rad) * rIn,
                        cx + (float) Math.cos(rad) * rOut,
                        cy + (float) Math.sin(rad) * rOut,
                        tickPaint
                );
            }
        }
    }

    private void drawIdle(Canvas canvas, float cx, float cy, float maxRadius, float density) {
        float pulse = (float) (0.5 + 0.5 * Math.sin(phase));
        outerRingPaint.setColor(Color.argb(120, 0, 212, 255));
        outerRingPaint.setStrokeWidth(2.2f * density);
        canvas.drawCircle(cx, cy, maxRadius * (0.74f + 0.06f * pulse), outerRingPaint);

        outerRingPaint.setColor(Color.argb(95, 168, 85, 247));
        outerRingPaint.setStrokeWidth(1.4f * density);
        canvas.drawCircle(cx, cy, maxRadius * 0.56f, outerRingPaint);

        corePaint.setColor(Color.argb(190, 37, 99, 235));
        canvas.drawCircle(cx, cy, maxRadius * (0.40f + 0.03f * pulse), corePaint);

        corePaint.setColor(Color.argb(240, 0, 212, 255));
        canvas.drawCircle(cx, cy, maxRadius * 0.21f, corePaint);
    }

    private void drawListening(Canvas canvas, float cx, float cy, float maxRadius, float density) {
        float pulse = (float) (0.5 + 0.5 * Math.sin(phase * 2f));
        float rmsBoost = 0.24f * smoothedRms;

        outerRingPaint.setColor(Color.argb(115, 124, 58, 237));
        outerRingPaint.setStrokeWidth(2.6f * density);
        canvas.drawCircle(cx, cy, maxRadius * Math.min(0.95f, 0.72f + rmsBoost + 0.09f * pulse), outerRingPaint);

        outerRingPaint.setColor(Color.argb(220, 0, 212, 255));
        outerRingPaint.setStrokeWidth(2.4f * density);
        canvas.drawCircle(cx, cy, maxRadius * (0.58f + rmsBoost * 0.7f), outerRingPaint);

        int bars = 7;
        float spacing = maxRadius * 0.16f;
        float startX = cx - ((bars - 1) * spacing) / 2f;
        wavePaint.setStrokeWidth(2.6f * density);
        for (int i = 0; i < bars; i++) {
            float mod = (float) Math.abs(Math.sin(phase * 2.5f + i * 0.75f));
            float halfH = maxRadius * (0.10f + (0.20f + 0.24f * smoothedRms) * mod);
            float x = startX + i * spacing;
            wavePaint.setColor(i % 2 == 0 ? Color.rgb(0, 212, 255) : Color.rgb(168, 85, 247));
            canvas.drawLine(x, cy - halfH, x, cy + halfH, wavePaint);
        }
    }

    private void drawThinking(Canvas canvas, float cx, float cy, float maxRadius, float density) {
        float angleDeg = (float) Math.toDegrees(phase);
        float r1 = maxRadius * 0.78f;
        arcBounds.set(cx - r1, cy - r1, cx + r1, cy + r1);

        innerArcPaint.setColor(Color.rgb(0, 212, 255));
        innerArcPaint.setStrokeWidth(3.2f * density);
        canvas.drawArc(arcBounds, angleDeg * 1.3f, 115f, false, innerArcPaint);
        canvas.drawArc(arcBounds, angleDeg * 1.3f + 180f, 80f, false, innerArcPaint);

        float r2 = maxRadius * 0.56f;
        arcBounds.set(cx - r2, cy - r2, cx + r2, cy + r2);
        innerArcPaint.setColor(Color.rgb(168, 85, 247));
        innerArcPaint.setStrokeWidth(2.6f * density);
        canvas.drawArc(arcBounds, -angleDeg * 1.7f, 130f, false, innerArcPaint);

        float corePulse = (float) (0.5 + 0.5 * Math.sin(phase * 3f));
        corePaint.setColor(Color.argb(235, 124, 58, 237));
        canvas.drawCircle(cx, cy, maxRadius * (0.23f + 0.08f * corePulse), corePaint);
        corePaint.setColor(Color.argb(220, 0, 212, 255));
        canvas.drawCircle(cx, cy, maxRadius * 0.11f, corePaint);
    }

    private void drawSpeaking(Canvas canvas, float cx, float cy, float maxRadius, float density) {
        float pulse = (float) (0.5 + 0.5 * Math.sin(phase * 3f));
        outerRingPaint.setColor(Color.argb(145, 168, 85, 247));
        outerRingPaint.setStrokeWidth(2.0f * density);
        canvas.drawCircle(cx, cy, maxRadius * (0.76f + 0.07f * pulse), outerRingPaint);

        int barCount = 9;
        float spacing = maxRadius * 0.15f;
        float startX = cx - ((barCount - 1) * spacing) / 2f;
        wavePaint.setStrokeWidth(2.8f * density);

        for (int i = 0; i < barCount; i++) {
            float envelope = 1.0f - Math.abs(i - (barCount - 1) / 2f) / (barCount / 1.7f);
            float barWave = (float) Math.abs(Math.sin(phase * 3.2f + i * 0.72f));
            float halfHeight = maxRadius * (0.11f + 0.40f * envelope * barWave);
            float x = startX + i * spacing;
            wavePaint.setColor(i % 2 == 0 ? Color.rgb(0, 212, 255) : Color.rgb(168, 85, 247));
            canvas.drawLine(x, cy - halfHeight, x, cy + halfHeight, wavePaint);
        }
    }

    private void drawSleeping(Canvas canvas, float cx, float cy, float maxRadius, float density) {
        float pulse = (float) (0.5 + 0.5 * Math.sin(phase * 0.5f));
        outerRingPaint.setColor(Color.argb((int) (55 + 45 * pulse), 99, 102, 241));
        outerRingPaint.setStrokeWidth(1.5f * density);
        canvas.drawCircle(cx, cy, maxRadius * 0.68f, outerRingPaint);

        corePaint.setColor(Color.argb(110, 67, 56, 202));
        canvas.drawCircle(cx, cy, maxRadius * 0.24f, corePaint);
    }
}
