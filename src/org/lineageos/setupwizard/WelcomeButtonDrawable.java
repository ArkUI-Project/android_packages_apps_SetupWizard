/*
 * SPDX-FileCopyrightText: 2026 The ArkUI Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.setupwizard;

import android.animation.ValueAnimator;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;

import androidx.dynamicanimation.animation.FloatValueHolder;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

/** The welcome action changes from a capsule to a soft rectangle while pressed. */
final class WelcomeButtonDrawable extends RippleDrawable {
    private final GradientDrawable mFill;
    private final GradientDrawable mMask;
    private final float mRadius;
    private float mCurrentRadius;
    private boolean mPressed;
    private int mFillColor, mInkColor;
    private SpringAnimation mSpring;

    WelcomeButtonDrawable(int fill, int ink, float radius) {
        this(shape(fill, radius), shape(Color.WHITE, radius), ink, radius);
    }

    private WelcomeButtonDrawable(GradientDrawable fill, GradientDrawable mask, int ink,
            float radius) {
        super(ColorStateList.valueOf((ink & 0x00ffffff) | 0x24000000), fill, mask);
        mFill = fill;
        mMask = mask;
        mRadius = mCurrentRadius = radius;
    }

    void setColors(int fill, int ink) {
        if (mFillColor != fill) {
            mFill.setColor(fill);
            mFillColor = fill;
        }
        if (mInkColor != ink) {
            setColor(ColorStateList.valueOf((ink & 0x00ffffff) | 0x24000000));
            mInkColor = ink;
        }
    }

    private static GradientDrawable shape(int color, float radius) {
        GradientDrawable result = new GradientDrawable();
        result.setColor(color);
        result.setCornerRadius(radius);
        return result;
    }

    @Override
    protected boolean onStateChange(int[] states) {
        boolean changed = super.onStateChange(states);
        if (mFill == null) return changed;
        boolean pressed = false;
        for (int state : states) {
            if (state == android.R.attr.state_pressed) pressed = true;
        }
        if (pressed == mPressed) return changed;
        mPressed = pressed;
        float target = mPressed ? mRadius * .42f : mRadius;
        if (!ValueAnimator.areAnimatorsEnabled() || getCallback() == null) {
            if (mSpring != null) mSpring.cancel();
            setRadius(target);
        } else {
            if (mSpring == null) {
                mSpring = new SpringAnimation(new FloatValueHolder(mCurrentRadius));
                mSpring.setMinimumVisibleChange(.1f);
                mSpring.setSpring(new SpringForce(target)
                        .setStiffness(800).setDampingRatio(.8f));
                mSpring.addUpdateListener((animation, value, velocity) -> setRadius(value));
            }
            if (!mSpring.isRunning()) mSpring.setStartValue(mCurrentRadius);
            mSpring.animateToFinalPosition(target);
        }
        return true;
    }

    private void setRadius(float radius) {
        mCurrentRadius = Math.max(0, radius);
        mFill.setCornerRadius(mCurrentRadius);
        mMask.setCornerRadius(mCurrentRadius);
    }

    @Override
    public void jumpToCurrentState() {
        super.jumpToCurrentState();
        if (mSpring != null) mSpring.cancel();
        if (mFill != null) setRadius(mPressed ? mRadius * .42f : mRadius);
    }

    @Override
    public boolean setVisible(boolean visible, boolean restart) {
        if (!visible && mSpring != null) mSpring.cancel();
        return super.setVisible(visible, restart);
    }
}
