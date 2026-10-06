/*
 * SPDX-FileCopyrightText: 2026 The ArkUI Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.arkui.animation;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.animation.PathInterpolator;

/** The opening light choreography shared by setup and the boot waiting screen. */
public final class StartupAnimation {
    public static final long DURATION_MILLIS = 5800;

    private static final PathInterpolator EFFECT = new PathInterpolator(.2f, 0, 0, 1);
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float mDensity;
    private final int mPrimary;
    private RadialGradient mGlow;
    private int mWidth;

    public StartupAnimation(float density, int primary) {
        mDensity = density;
        mPrimary = primary;
    }

    public void draw(Canvas canvas, int width, int height, long elapsed) {
        if (width <= 0 || height <= 0) return;
        if (mGlow == null || mWidth != width) {
            mWidth = width;
            mGlow = new RadialGradient(0, 0, width * .13f,
                    new int[]{Color.WHITE, alpha(mPrimary, 180), alpha(mPrimary, 0)},
                    new float[]{0, .24f, 1}, Shader.TileMode.CLAMP);
        }
        float reveal = EFFECT.getInterpolation(clamp((elapsed - 5000) / 800f));
        int opacity = Math.round(255 * (1 - reveal));
        mPaint.setShader(null);
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(alpha(Color.BLACK, opacity));
        canvas.drawRect(0, 0, width, height, mPaint);
        if (elapsed >= DURATION_MILLIS) return;

        float cx = width * .5f;
        float cy = height * .5f;
        float dot = 15 * mDensity;
        if (elapsed > 4600) {
            float scale = 1 + entrance((elapsed - 4600) / 1000f) * 5;
            canvas.save();
            canvas.translate(cx, cy);
            canvas.scale(scale, scale);
            mPaint.setShader(mGlow);
            mPaint.setAlpha(opacity);
            canvas.drawRect(-width, -width, width, width, mPaint);
            canvas.restore();
        }
        mPaint.setShader(null);
        mPaint.setColor(alpha(Color.WHITE, opacity));
        if (elapsed < 3000) {
            float spread = 33 * mDensity * pulse(elapsed, 0, 2800);
            float size = dot * (.35f + .65f * entrance(elapsed / 1000f));
            canvas.drawCircle(cx - spread, cy, size, mPaint);
            canvas.drawCircle(cx + spread, cy, size, mPaint);
        } else {
            float spread = 37 * mDensity * pulse(elapsed, 3000, 1700);
            double rotation = (elapsed - 3000) / 1900.0;
            for (int i = 0; i < 4; i++) {
                double angle = rotation + i * Math.PI / 2;
                canvas.drawCircle(cx + spread * (float) Math.cos(angle),
                        cy + spread * (float) Math.sin(angle), dot, mPaint);
            }
        }
    }

    private static float entrance(float seconds) {
        double decay = Math.sqrt(500) * .85;
        double oscillation = Math.sqrt(500 * (1 - .85 * .85));
        return (float) (1 - Math.exp(-decay * Math.max(0, seconds))
                * (Math.cos(oscillation * seconds)
                + decay / oscillation * Math.sin(oscillation * seconds)));
    }

    private static float pulse(long elapsed, long start, long length) {
        float sine = (float) Math.sin(clamp((elapsed - start) / (float) length) * Math.PI);
        return sine * sine;
    }

    private static float clamp(float value) {
        return Math.max(0, Math.min(1, value));
    }

    private static int alpha(int color, int opacity) {
        return (color & 0x00ffffff) | (opacity << 24);
    }
}
