/*
 * SPDX-FileCopyrightText: 2026 The ArkUI Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.setupwizard;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/** The Updater's glass ribbon artwork, with cached geometry and optional gentle motion. */
final class ArkuiRibbonDrawable extends Drawable {
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mClip = new RectF();
    private final Path mOutline = new Path();
    private final Path[] mRibbons = new Path[8];
    private final Shader[] mFolds = new Shader[8];
    private final int mPrimaryContainer, mSecondaryContainer, mSurface, mPrimary, mInk, mBright;
    private final boolean mDark;
    private final float mCornerRadius;
    private Shader mBase, mGlow;
    private float mDriftX, mDriftY;
    private int mAlpha = 255;

    ArkuiRibbonDrawable(Context context, float cornerRadius) {
        mCornerRadius = cornerRadius;
        mPrimaryContainer = context.getColor(
                com.android.internal.R.color.materialColorPrimaryContainer);
        mSecondaryContainer = context.getColor(
                com.android.internal.R.color.materialColorSecondaryContainer);
        mSurface = context.getColor(com.android.internal.R.color.materialColorSurfaceContainerHigh);
        mPrimary = context.getColor(com.android.internal.R.color.materialColorPrimary);
        mInk = context.getColor(com.android.internal.R.color.materialColorOnPrimaryContainer);
        mBright = context.getColor(com.android.internal.R.color.materialColorSurfaceBright);
        mDark = (context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        for (int i = 0; i < mRibbons.length; i++) mRibbons[i] = new Path();
    }

    void setElapsed(long elapsed, boolean animated) {
        // The loop meets at the same position and velocity, without rebuilding paths.
        double phase = animated ? elapsed % 24000 / 24000.0 * Math.PI * 2 : 0;
        mDriftX = (float) Math.sin(phase) * .035f;
        mDriftY = (float) Math.sin(phase * 2) * .012f;
        invalidateSelf();
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        float w = bounds.width(), h = bounds.height();
        if (w <= 0 || h <= 0) return;
        mClip.set(0, 0, w, h);
        mOutline.rewind();
        mOutline.addRoundRect(mClip, mCornerRadius, mCornerRadius, Path.Direction.CW);
        mBase = new LinearGradient(0, 0, w, h,
                new int[]{mPrimaryContainer, mSecondaryContainer, mSurface}, null,
                Shader.TileMode.CLAMP);
        for (int i = 0; i < mRibbons.length; i++) {
            float x = w * (-.25f + i * .22f);
            Path ribbon = mRibbons[i];
            ribbon.rewind();
            ribbon.moveTo(x, -h * .2f);
            ribbon.cubicTo(x + w * .36f, h * .1f, x - w * .22f, h * .75f,
                    x + w * .16f, h * 1.2f);
            ribbon.lineTo(x + w * .38f, h * 1.2f);
            ribbon.cubicTo(x, h * .75f, x + w * .58f, h * .1f, x + w * .22f, -h * .2f);
            ribbon.close();
            mFolds[i] = new LinearGradient(x, 0, x + w * .22f, h * .3f,
                    new int[]{alpha(mPrimary, mDark ? .14f : .07f), alpha(mInk, .025f),
                            alpha(mBright, mDark ? .10f : .38f)}, null, Shader.TileMode.CLAMP);
        }
        mGlow = new RadialGradient(w * .5f, h * .5f, Math.max(w, h) * .6f,
                alpha(mPrimaryContainer, .7f), alpha(mPrimaryContainer, 0),
                Shader.TileMode.CLAMP);
    }

    @Override
    public void draw(Canvas canvas) {
        if (mBase == null) return;
        Rect bounds = getBounds();
        canvas.save();
        canvas.translate(bounds.left, bounds.top);
        canvas.clipPath(mOutline);
        mPaint.setAlpha(mAlpha);
        mPaint.setShader(mBase);
        canvas.drawRect(mClip, mPaint);
        canvas.save();
        canvas.translate(bounds.width() * mDriftX, bounds.height() * mDriftY);
        for (int i = 0; i < mRibbons.length; i++) {
            mPaint.setShader(mFolds[i]);
            canvas.drawPath(mRibbons[i], mPaint);
        }
        canvas.restore();
        mPaint.setShader(mGlow);
        canvas.drawRect(mClip, mPaint);
        mPaint.setShader(null);
        canvas.restore();
    }

    @Override
    public void setAlpha(int alpha) {
        mAlpha = alpha;
        invalidateSelf();
    }

    @Override
    public void setColorFilter(ColorFilter filter) {
        mPaint.setColorFilter(filter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    private static int alpha(int color, float opacity) {
        return (color & 0x00ffffff) | (Math.round(255 * opacity) << 24);
    }
}
