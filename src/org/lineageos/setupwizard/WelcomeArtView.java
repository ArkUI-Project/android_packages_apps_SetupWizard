/*
 * SPDX-FileCopyrightText: 2026 The ArkUI Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.setupwizard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

import java.util.Arrays;

/** Resolution-independent welcome artwork, rendered without a video or per-frame bitmaps. */
final class WelcomeArtView extends View {
    private static final int STRANDS = 12;
    private static final int SAMPLES = 80;
    private static final int[] STAGES = {0, 6000, 8000, 9500, 14000, 16500, 19500};
    private static final int POINTS = STRANDS * (SAMPLES + 1) * 2;
    private static final int COLOR = POINTS, DOTS = POINTS + 1, WIDTH = POINTS + 2;
    private static final int HALO = POINTS + 3;
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path mPath = new Path();
    private final float[] mFrom = new float[2];
    private final float[] mPosition = new float[POINTS + 4];
    private final float[] mVelocity = new float[POINTS + 4];
    private final float[] mTarget = new float[POINTS + 4];
    private final float[] mMark = new float[(SAMPLES + 1) * 2];
    private final float[] mSin = new float[SAMPLES + 1];
    private final float[] mCos = new float[SAMPLES + 1];
    private final float[] mWave = new float[SAMPLES + 1];
    private final float[] mSpokeX = new float[STRANDS];
    private final float[] mSpokeY = new float[STRANDS];
    private final float[] mRotationCos = new float[STRANDS];
    private final float[] mRotationSin = new float[STRANDS];
    private final int mSurface, mInk, mPrimary, mTertiary;
    private RadialGradient mPrimaryGlow, mTertiaryGlow, mIntroGlow, mHaloGlow;
    private LinearGradient mSpectrum;
    private float mCenterX, mCenterY, mRadius;
    private long mElapsed;
    private long mGeometryElapsed = -1;
    private float mFanSpread, mRingTilt, mCoilHeight, mCoilOffset;
    private boolean mAnimated = true;

    WelcomeArtView(Context context) {
        super(context);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        mSurface = context.getColor(com.android.internal.R.color.materialColorSurface);
        mInk = context.getColor(com.android.internal.R.color.materialColorOnSurface);
        mPrimary = context.getColor(com.android.internal.R.color.materialColorPrimary);
        mTertiary = context.getColor(com.android.internal.R.color.materialColorTertiary);
        mPaint.setStrokeCap(Paint.Cap.ROUND);
        mPaint.setStrokeJoin(Paint.Join.ROUND);
        // The first letter follows the system's ArkUI wordmark, rather than another OS's symbol.
        Path mark = new Path();
        mark.moveTo(0, 80);
        mark.lineTo(41, 10);
        mark.cubicTo(45, 3, 50, 0, 56, 0);
        mark.lineTo(74, 0);
        mark.lineTo(74, 80);
        mark.lineTo(59, 80);
        mark.lineTo(59, 62);
        mark.lineTo(34, 62);
        mark.lineTo(42, 48);
        mark.lineTo(59, 48);
        mark.lineTo(59, 15);
        mark.lineTo(52, 15);
        mark.lineTo(18, 80);
        mark.close();
        PathMeasure measure = new PathMeasure(mark, true);
        for (int j = 0; j <= SAMPLES; j++) {
            measure.getPosTan(measure.getLength() * j / SAMPLES, mFrom, null);
            mMark[2 * j] = (mFrom[0] - 37) / 80;
            mMark[2 * j + 1] = (mFrom[1] - 40) / 80;
            mSin[j] = (float) Math.sin(2 * Math.PI * j / SAMPLES);
            mCos[j] = (float) Math.cos(2 * Math.PI * j / SAMPLES);
            mWave[j] = (float) Math.sin(Math.PI * j / SAMPLES);
        }
    }

    void setScene(float centerX, float centerY, float radius) {
        mCenterX = centerX;
        mCenterY = centerY;
        mRadius = radius;
    }

    void setFrame(long elapsed, boolean animated) {
        mElapsed = elapsed;
        mAnimated = animated;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        if (width == 0 || height == 0) return;
        mPrimaryGlow = new RadialGradient(0, 0, width * .75f,
                alpha(mPrimary, 42), alpha(mPrimary, 0), Shader.TileMode.CLAMP);
        mTertiaryGlow = new RadialGradient(0, 0, width * .65f,
                alpha(mTertiary, 32), alpha(mTertiary, 0), Shader.TileMode.CLAMP);
        mIntroGlow = new RadialGradient(0, 0, width * .13f,
                new int[]{Color.WHITE, alpha(mPrimary, 180), alpha(mPrimary, 0)},
                new float[]{0, .24f, 1}, Shader.TileMode.CLAMP);
        mHaloGlow = new RadialGradient(0, 0, width * .33f,
                new int[]{alpha(mPrimary, 0), alpha(mTertiary, 30), alpha(mPrimary, 150),
                        alpha(mTertiary, 50), alpha(mPrimary, 0)},
                new float[]{0, .4f, .58f, .74f, 1}, Shader.TileMode.CLAMP);
        mSpectrum = new LinearGradient(0, 0, width, width * .15f,
                new int[]{mPrimary, mTertiary, mPrimary}, null, Shader.TileMode.MIRROR);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(mSurface);
        if (mPrimaryGlow == null) return;
        float drift = mAnimated ? (float) Math.sin(mElapsed / 6000.0) * getWidth() * .06f : 0;
        glow(canvas, mPrimaryGlow, getWidth() * .28f + drift, getHeight() * .10f, 1, 255);
        glow(canvas, mTertiaryGlow, getWidth() * .80f - drift, getHeight() * .25f, 1, 255);
        glow(canvas, mPrimaryGlow, getWidth() * .85f, getHeight() * 1.03f, .7f, 105);
        if (mElapsed < WelcomeLayout.MAIN_START && mAnimated) {
            drawIntro(canvas);
        } else {
            drawGeometry(canvas);
        }
    }

    private void drawIntro(Canvas canvas) {
        float reveal = WelcomeMotion.effect((mElapsed - 5000) / 800f);
        mPaint.setShader(null);
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(alpha(Color.BLACK, Math.round(255 * (1 - reveal))));
        canvas.drawRect(0, 0, getWidth(), getHeight(), mPaint);
        if (mElapsed > 5800) return;
        float cx = getWidth() * .5f;
        float cy = getHeight() * .5f;
        float dot = dp(15);
        if (mElapsed > 4600) {
            float scale = 1 + WelcomeMotion.entrance((mElapsed - 4600) / 1000f) * 5;
            glow(canvas, mIntroGlow, cx, cy, scale, Math.round(255 * (1 - reveal)));
        }
        mPaint.setShader(null);
        mPaint.setColor(alpha(Color.WHITE, Math.round(255 * (1 - reveal))));
        if (mElapsed < 3000) {
            float spread = dp(33) * pulse(mElapsed, 0, 2800);
            float size = dot * (.35f + .65f * WelcomeMotion.entrance(mElapsed / 1000f));
            canvas.drawCircle(cx - spread, cy, size, mPaint);
            canvas.drawCircle(cx + spread, cy, size, mPaint);
        } else {
            float spread = dp(37) * pulse(mElapsed, 3000, 1700);
            double rotation = (mElapsed - 3000) / 1900.0;
            for (int i = 0; i < 4; i++) {
                double angle = rotation + i * Math.PI / 2;
                canvas.drawCircle(cx + spread * (float) Math.cos(angle),
                        cy + spread * (float) Math.sin(angle), dot, mPaint);
            }
        }
    }

    private void drawGeometry(Canvas canvas) {
        updateGeometry();
        float enter = mAnimated ? WelcomeMotion.effect(
                (mElapsed - WelcomeLayout.MAIN_START) / 420f) : 1;
        float bloom = clamp(mPosition[HALO]);
        if (bloom > .005f) {
            glow(canvas, mHaloGlow, mCenterX, mCenterY,
                    mRadius / (getWidth() * .33f) * (1 + bloom * .35f),
                    Math.round(bloom * enter * 230));
        }
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(dp(mPosition[WIDTH]));
        float color = clamp(mPosition[COLOR]);
        float dots = clamp(mPosition[DOTS]);
        float opacity = enter * (.78f - bloom * .5f);
        for (int i = 0; i < STRANDS; i++) {
            mPath.rewind();
            for (int j = 0; j <= SAMPLES; j++) {
                int index = (i * (SAMPLES + 1) + j) * 2;
                float x = mCenterX + mRadius * mPosition[index];
                float y = mCenterY + mRadius * mPosition[index + 1];
                if (j == 0) mPath.moveTo(x, y);
                else mPath.lineTo(x, y);
            }
            if (color < .995f) {
                mPaint.setShader(null);
                mPaint.setColor(mInk);
                mPaint.setAlpha(Math.round(255 * opacity * (1 - color)));
                canvas.drawPath(mPath, mPaint);
            }
            if (color > .005f) {
                mPaint.setShader(mSpectrum);
                mPaint.setAlpha(Math.round(255 * opacity * color));
                canvas.drawPath(mPath, mPaint);
            }
            if (dots > .005f) {
                mPaint.setStyle(Paint.Style.FILL);
                mPaint.setShader(null);
                mPaint.setColor(mInk);
                mPaint.setAlpha(Math.round(255 * enter * dots));
                int end = (i * (SAMPLES + 1) + SAMPLES) * 2;
                canvas.drawCircle(mCenterX + mRadius * mPosition[end],
                        mCenterY + mRadius * mPosition[end + 1],
                        dp(1.5f + (i % 4) * .7f) * dots, mPaint);
                mPaint.setStyle(Paint.Style.STROKE);
            }
        }
        mPaint.setShader(null);
        mPaint.setAlpha(255);
    }

    private void updateGeometry() {
        long elapsed = Math.max(0, mElapsed - WelcomeLayout.MAIN_START);
        if (!mAnimated) {
            targetGeometry(STAGES[4] + 600);
            System.arraycopy(mTarget, 0, mPosition, 0, mPosition.length);
            Arrays.fill(mVelocity, 0);
            mGeometryElapsed = -1;
            return;
        }
        if (mGeometryElapsed < 0 || elapsed < mGeometryElapsed
                || elapsed - mGeometryElapsed > 500) {
            // Warm the same spring on restoration, so recreation preserves the visible pose.
            mGeometryElapsed = Math.max(0, elapsed - 1000);
            targetGeometry(mGeometryElapsed);
            System.arraycopy(mTarget, 0, mPosition, 0, mPosition.length);
            Arrays.fill(mVelocity, 0);
        }
        while (mGeometryElapsed < elapsed) {
            long next = Math.min(elapsed, mGeometryElapsed + 32);
            targetGeometry(next);
            WelcomeMotion.advance(mPosition, mVelocity, mTarget,
                    (next - mGeometryElapsed) / 1000f);
            mGeometryElapsed = next;
        }
    }

    private void targetGeometry(long elapsed) {
        int time = (int) (elapsed % STAGES[STAGES.length - 1]);
        int stage = 0;
        while (stage < STAGES.length - 2 && time >= STAGES[stage + 1]) stage++;
        float progress = (time - STAGES[stage])
                / (float) (STAGES[stage + 1] - STAGES[stage]);
        float spread = .025f + .16f * pulse(time, 0, 2700)
                + .78f * pulse(time, 2750, 2850);
        if (elapsed >= STAGES[STAGES.length - 1]) {
            spread += .74f * pulse(time, 0, 2700);
        }
        mFanSpread = .08f + 1.25f * WelcomeMotion.effect(progress / .85f);
        mRingTilt = .45f + .55f * (float) Math.sin(progress * Math.PI);
        mCoilHeight = .56f * (1 - WelcomeMotion.effect((progress - .48f) / .37f));
        mCoilOffset = (float) Math.sin(progress * Math.PI * 2) * .1f;
        for (int i = 0; i < STRANDS; i++) {
            double angle = i * Math.PI * 2 / STRANDS + progress * .55;
            float length = spread * (.6f + .4f * (i % 3) / 2);
            mSpokeX[i] = length * (float) Math.cos(angle);
            mSpokeY[i] = length * (float) Math.sin(angle);
            double rotation = (progress - .5f) * .9f + (i - 5.5f) * .018f;
            mRotationCos[i] = (float) Math.cos(rotation);
            mRotationSin[i] = (float) Math.sin(rotation);
            for (int j = 0; j <= SAMPLES; j++) {
                point(stage, i, j, progress, mFrom);
                int index = (i * (SAMPLES + 1) + j) * 2;
                mTarget[index] = mFrom[0];
                mTarget[index + 1] = mFrom[1];
            }
        }
        mTarget[COLOR] = stage == 0 ? 0 : 1;
        mTarget[DOTS] = stage == 0 ? 1 : 0;
        mTarget[WIDTH] = stage == 4 ? 1.6f : 1.05f;
        mTarget[HALO] = stage == 5 ? pulse(time, STAGES[5], 2000) : 0;
    }

    private void point(int stage, int strand, int sample, float progress, float[] result) {
        float t = sample / (float) SAMPLES;
        float offset = (strand - (STRANDS - 1) / 2f) / STRANDS;
        switch (stage) {
            case 0: {
                result[0] = t * mSpokeX[strand];
                result[1] = t * mSpokeY[strand];
                break;
            }
            case 1:
                result[0] = -1.45f + t * 2.9f;
                result[1] = offset * mFanSpread * mWave[sample];
                break;
            case 2: {
                float radius = .47f + strand * .044f;
                result[0] = mCos[sample] * radius;
                result[1] = mSin[sample] * radius * mRingTilt + offset * .52f;
                break;
            }
            case 3:
                result[0] = offset * 3.6f + mCos[sample] * .26f + mCoilOffset;
                result[1] = mSin[sample] * mCoilHeight;
                break;
            case 4: {
                float scale = .32f + strand * .007f;
                float x = mMark[sample * 2] * scale + offset * .04f;
                float y = mMark[sample * 2 + 1] * scale - offset * .04f;
                result[0] = x * mRotationCos[strand] - y * mRotationSin[strand];
                result[1] = x * mRotationSin[strand] + y * mRotationCos[strand];
                break;
            }
            default: {
                float radius = (.3f + strand * .036f)
                        * (1 - WelcomeMotion.effect(progress / .75f));
                result[0] = mCos[sample] * radius;
                result[1] = mSin[sample] * radius;
                break;
            }
        }
    }

    private void glow(Canvas canvas, Shader shader, float x, float y, float scale, int opacity) {
        canvas.save();
        canvas.translate(x, y);
        canvas.scale(scale, scale);
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setShader(shader);
        mPaint.setAlpha(opacity);
        canvas.drawRect(-getWidth(), -getWidth(), getWidth(), getWidth(), mPaint);
        mPaint.setShader(null);
        mPaint.setAlpha(255);
        canvas.restore();
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private static float clamp(float value) {
        return Math.max(0, Math.min(1, value));
    }

    private static float pulse(long elapsed, long start, long length) {
        float progress = clamp((elapsed - start) / (float) length);
        float sine = (float) Math.sin(progress * Math.PI);
        return sine * sine;
    }

    private static int alpha(int color, int alpha) {
        return (color & 0x00ffffff) | (alpha << 24);
    }
}
