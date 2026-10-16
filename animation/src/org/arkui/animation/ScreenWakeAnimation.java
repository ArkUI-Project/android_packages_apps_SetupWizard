/*
 * SPDX-FileCopyrightText: 2026 The ArkUI Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.arkui.animation;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.PowerManager;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.accessibility.AccessibilityManager;
import android.view.animation.PathInterpolator;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

/** Restores the visible setup page after screen-on without replaying its introduction. */
public final class ScreenWakeAnimation implements Application.ActivityLifecycleCallbacks {
    private static final PathInterpolator ENTER = new PathInterpolator(.2f, 0, 0, 1);
    private final Map<Activity, Page> mPages = new HashMap<>();
    private final Predicate<Activity> mEligible;
    private final PowerManager mPowerManager;
    // ValueAnimator keeps only a weak reference to duration listeners.
    private final ValueAnimator.DurationScaleChangeListener mDurationListener = scale -> {
        if (scale == 0) cancelAnimations();
    };

    private ScreenWakeAnimation(Application application, Predicate<Activity> eligible) {
        mEligible = eligible;
        mPowerManager = application.getSystemService(PowerManager.class);
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        application.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                boolean screenOff = Intent.ACTION_SCREEN_OFF.equals(intent.getAction());
                for (Page page : mPages.values()) {
                    if (screenOff && page.mStarted) {
                        page.mPendingWake = true;
                        page.cancel();
                    } else if (!screenOff) {
                        page.animateIfReady();
                    }
                }
            }
        }, filter, Context.RECEIVER_NOT_EXPORTED);
        ValueAnimator.registerDurationScaleChangeListener(mDurationListener);
        AccessibilityManager accessibility =
                application.getSystemService(AccessibilityManager.class);
        if (accessibility != null) {
            accessibility.addTouchExplorationStateChangeListener(enabled -> {
                if (enabled) cancelAnimations();
            });
        }
    }

    /** Register once per process, selecting only the setup and boot-waiting activities. */
    public static void register(Application application, Predicate<Activity> eligible) {
        application.registerActivityLifecycleCallbacks(
                new ScreenWakeAnimation(application, eligible));
    }

    public static boolean isMotionEnabled(Context context) {
        AccessibilityManager accessibility = context.getSystemService(AccessibilityManager.class);
        return ValueAnimator.areAnimatorsEnabled()
                && (accessibility == null || !accessibility.isTouchExplorationEnabled());
    }

    private void cancelAnimations() {
        for (Page page : mPages.values()) page.cancel();
    }

    @Override
    public void onActivityCreated(Activity activity, Bundle state) {
        if (mEligible.test(activity)) mPages.put(activity, new Page(activity));
    }

    @Override
    public void onActivityStarted(Activity activity) {
        Page page = mPages.get(activity);
        if (page == null) return;
        page.mStarted = true;
        page.mPendingWake |= !mPowerManager.isInteractive();
        ViewTreeObserver observer = activity.getWindow().getDecorView().getViewTreeObserver();
        observer.addOnPreDrawListener(page);
        observer.addOnWindowFocusChangeListener(page);
    }

    @Override
    public void onActivityResumed(Activity activity) {
        Page page = mPages.get(activity);
        if (page != null) {
            page.mResumed = true;
            page.animateIfReady();
        }
    }

    @Override
    public void onActivityPaused(Activity activity) {
        Page page = mPages.get(activity);
        if (page != null) {
            page.mPendingWake |= !mPowerManager.isInteractive();
            page.mResumed = false;
            page.cancel();
        }
    }

    @Override
    public void onActivityStopped(Activity activity) {
        Page page = mPages.get(activity);
        if (page == null) return;
        page.mPendingWake |= !mPowerManager.isInteractive();
        page.mStarted = false;
        page.cancel();
        ViewTreeObserver observer = activity.getWindow().getDecorView().getViewTreeObserver();
        if (observer.isAlive()) {
            observer.removeOnPreDrawListener(page);
            observer.removeOnWindowFocusChangeListener(page);
        }
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
        Page page = mPages.remove(activity);
        if (page != null) page.cancel();
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle state) {}

    private final class Page implements ViewTreeObserver.OnPreDrawListener,
            ViewTreeObserver.OnWindowFocusChangeListener {
        private final Activity mActivity;
        private boolean mStarted, mResumed, mPendingWake;
        private ValueAnimator mAnimator;
        private View mContent;
        private float mAlpha, mTranslationY;

        Page(Activity activity) {
            mActivity = activity;
        }

        @Override
        public boolean onPreDraw() {
            animateIfReady();
            return true;
        }

        @Override
        public void onWindowFocusChanged(boolean hasFocus) {
            if (hasFocus) animateIfReady();
            else cancel();
        }

        void animateIfReady() {
            if (!mPendingWake || !mResumed || !mPowerManager.isInteractive()
                    || !mActivity.hasWindowFocus() || mActivity.isFinishing()) return;
            View content = mActivity.findViewById(android.R.id.content);
            if (content == null || !content.isShown() || content.getWidth() == 0
                    || content.getHeight() == 0) return;
            mPendingWake = false;
            if (!isMotionEnabled(mActivity)) return;
            cancel();
            mContent = content;
            mAlpha = content.getAlpha();
            mTranslationY = content.getTranslationY();
            float distance = 8 * content.getResources().getDisplayMetrics().density;
            content.setAlpha(mAlpha * .7f);
            content.setTranslationY(mTranslationY + distance);
            mAnimator = ValueAnimator.ofFloat(0, 1);
            mAnimator.setDuration(360);
            mAnimator.setInterpolator(ENTER);
            mAnimator.addUpdateListener(animation -> {
                float progress = (float) animation.getAnimatedValue();
                content.setAlpha(mAlpha * (.7f + .3f * progress));
                content.setTranslationY(mTranslationY + distance * (1 - progress));
            });
            mAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    cancel();
                }
            });
            mAnimator.start();
        }

        void cancel() {
            if (mAnimator != null) {
                mAnimator.removeAllUpdateListeners();
                mAnimator.removeAllListeners();
                mAnimator.cancel();
                mAnimator = null;
            }
            if (mContent != null) {
                mContent.setAlpha(mAlpha);
                mContent.setTranslationY(mTranslationY);
                mContent = null;
            }
        }
    }
}
