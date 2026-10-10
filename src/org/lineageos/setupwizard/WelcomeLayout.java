/*
 * SPDX-FileCopyrightText: 2026 The ArkUI Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.setupwizard;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.Choreographer;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

/** A single welcome scene: glass ribbons, wordmark and actions share one timeline. */
public final class WelcomeLayout extends ViewGroup implements Choreographer.FrameCallback {
    static final long MAIN_START = 1800;
    private final WelcomeArtView mArt;
    private final TextView mTagline, mGreeting;
    private final ImageView mLogo;
    private final Button mNext, mSkipIntro;
    private final Button[] mUtilities;
    private final AccessibilityManager mAccessibility;
    private final AccessibilityManager.TouchExplorationStateChangeListener mExplorationListener =
            enabled -> updateAnimationState();
    private final ValueAnimator.DurationScaleChangeListener mDurationListener =
            this::onDurationScaleChanged;
    private final int mInk, mUtilitySurface;
    private final String mTaglineText;
    private CharSequence mWelcomeMessage;
    private long mElapsed, mLastFrameNanos;
    private double mFractionalMillis;
    private int mBarAppearance = -1;
    private boolean mResumed, mRunning, mMotionEnabled = true, mManaged;

    public WelcomeLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        setClipChildren(false);
        setClipToPadding(false);
        mInk = context.getColor(com.android.internal.R.color.materialColorOnPrimaryContainer);
        mUtilitySurface = (context.getColor(
                com.android.internal.R.color.materialColorSurface) & 0x00ffffff) | 0xcc000000;
        mAccessibility = context.getSystemService(AccessibilityManager.class);
        mTaglineText = context.getString(R.string.welcome_tagline);
        mWelcomeMessage = context.getString(R.string.setup_welcome_message,
                context.getString(R.string.os_name));
        mArt = new WelcomeArtView(context);
        addView(mArt);
        mTagline = text(18, Gravity.CENTER);
        mTagline.setTypeface(Typeface.create(Typeface.DEFAULT, 400, false));
        addView(mTagline);
        mLogo = new ImageView(context);
        mLogo.setId(R.id.brand_logo);
        mLogo.setImageResource(R.drawable.arkui_logo);
        mLogo.setImageTintList(ColorStateList.valueOf(mInk));
        mLogo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        mLogo.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(mLogo);
        mGreeting = text(36, Gravity.CENTER);
        mGreeting.setId(R.id.welcome_title);
        mGreeting.setAccessibilityHeading(true);
        mGreeting.setContentDescription(mWelcomeMessage);
        addView(mGreeting);
        mNext = new Button(context, null, 0);
        mNext.setId(R.id.start);
        mNext.setText(R.string.start);
        mNext.setTextSize(16);
        mNext.setAllCaps(false);
        mNext.setMinWidth(0);
        mNext.setMinimumWidth(0);
        mNext.setMinHeight(dp(56));
        mNext.setMaxLines(2);
        mNext.setContentDescription(context.getString(R.string.start));
        int primary = context.getColor(com.android.internal.R.color.materialColorPrimary);
        int onPrimary = context.getColor(com.android.internal.R.color.materialColorOnPrimary);
        mNext.setTextColor(onPrimary);
        mNext.setPadding(dp(24), 0, dp(24), 0);
        mNext.setBackground(new WelcomeButtonDrawable(primary, onPrimary, dp(28)));
        mNext.setStateListAnimator(null);
        addView(mNext);
        mSkipIntro = utility(R.id.skip_intro, R.string.welcome_skip_animation);
        mSkipIntro.setOnClickListener(view -> skipIntro());
        addView(mSkipIntro);
        mUtilities = new Button[]{
                utility(R.id.launch_accessibility, R.string.accessibility_settings),
                utility(R.id.emerg_dialer, R.string.emergency_call),
                utility(R.id.skip, R.string.skip)};
        for (Button button : mUtilities) addView(button);
        mUtilities[2].setVisibility(GONE);
        setOnApplyWindowInsetsListener((view, insets) -> {
            Insets safe = insets.getInsetsIgnoringVisibility(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            setPadding(safe.left, safe.top, safe.right, safe.bottom);
            return insets;
        });
        render();
    }

    void setWelcomeMessage(CharSequence message, boolean managed) {
        mWelcomeMessage = message;
        mManaged = managed;
        mGreeting.setContentDescription(message);
        updateAnimationState();
    }

    void restoreElapsed(long elapsed) {
        mElapsed = Math.max(0, elapsed);
        render();
    }

    long getElapsed() {
        return mElapsed;
    }

    boolean isAnimating() {
        return mRunning;
    }

    void resume() {
        mResumed = true;
        updateAnimationState();
    }

    void pause() {
        mResumed = false;
        stopFrames();
        mNext.getBackground().jumpToCurrentState();
    }

    void skipIntro() {
        if (mElapsed < MAIN_START) {
            mElapsed = MAIN_START;
            mFractionalMillis = 0;
            render();
        }
    }

    private void updateAnimationState() {
        stopFrames();
        mMotionEnabled = ValueAnimator.areAnimatorsEnabled() && !mManaged
                && (mAccessibility == null || !mAccessibility.isTouchExplorationEnabled());
        if (!mMotionEnabled) mElapsed = Math.max(MAIN_START, mElapsed);
        render();
        if (mResumed && mMotionEnabled && isAttachedToWindow()
                && getWindowVisibility() == VISIBLE) {
            mRunning = true;
            Choreographer.getInstance().postFrameCallback(this);
        }
    }

    private void onDurationScaleChanged(float scale) {
        boolean enabled = scale > 0 && !mManaged && (mAccessibility == null
                || !mAccessibility.isTouchExplorationEnabled());
        if (enabled != mMotionEnabled) updateAnimationState();
    }

    private void stopFrames() {
        mRunning = false;
        mLastFrameNanos = 0;
        Choreographer.getInstance().removeFrameCallback(this);
    }

    @Override
    public void doFrame(long frameTimeNanos) {
        if (!mRunning) return;
        if (!ValueAnimator.areAnimatorsEnabled()) {
            updateAnimationState();
            return;
        }
        if (mLastFrameNanos != 0) {
            double elapsed = (frameTimeNanos - mLastFrameNanos) / 1000000.0
                    / Math.max(.01f, ValueAnimator.getDurationScale()) + mFractionalMillis;
            long whole = (long) elapsed;
            mElapsed += whole;
            mFractionalMillis = elapsed - whole;
        }
        mLastFrameNanos = frameTimeNanos;
        render();
        Choreographer.getInstance().postFrameCallback(this);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (mAccessibility != null) {
            mAccessibility.addTouchExplorationStateChangeListener(mExplorationListener);
        }
        ValueAnimator.registerDurationScaleChangeListener(mDurationListener);
        updateAnimationState();
    }

    @Override
    protected void onDetachedFromWindow() {
        stopFrames();
        if (mAccessibility != null) {
            mAccessibility.removeTouchExplorationStateChangeListener(mExplorationListener);
        }
        ValueAnimator.unregisterDurationScaleChangeListener(mDurationListener);
        mNext.getBackground().jumpToCurrentState();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        // View construction may deliver this before the scene's children exist.
        if (mArt != null) updateAnimationState();
    }

    private void render() {
        mSkipIntro.setVisibility(mMotionEnabled && mElapsed < MAIN_START ? VISIBLE : GONE);
        for (Button button : mUtilities) updateUtility(button, mUtilitySurface, mInk);
        updateUtility(mSkipIntro, mUtilitySurface, mInk);
        updateSystemBarAppearance();
        float logo = mMotionEnabled ? WelcomeMotion.effect(mElapsed / 1000f) : 1;
        float title = mMotionEnabled ? WelcomeMotion.effect((mElapsed - 180) / 850f) : 1;
        float subtitle = mMotionEnabled ? WelcomeMotion.effect((mElapsed - 350) / 850f) : 1;
        mLogo.setAlpha(logo);
        mLogo.setTranslationY(dp(20) * (1 - logo));
        mLogo.setScaleX(.94f + .06f * logo);
        mLogo.setScaleY(.94f + .06f * logo);
        setText(mGreeting, mWelcomeMessage);
        mGreeting.setAlpha(title);
        mGreeting.setTranslationY(dp(16) * (1 - title));
        setText(mTagline, mManaged ? "" : mTaglineText);
        mTagline.setAlpha(subtitle);
        // Start is usable immediately; the introduction never gates setup navigation.
        mNext.setAlpha(1);
        mArt.setFrame(mElapsed, mMotionEnabled);
    }

    private void updateUtility(Button button, int fill, int ink) {
        button.setTextColor(ink);
        ((WelcomeButtonDrawable) button.getBackground()).setColors(fill, ink);
    }

    private void updateSystemBarAppearance() {
        WindowInsetsController controller = getWindowInsetsController();
        if (controller == null) return;
        int mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
        int appearance = Color.luminance(mInk) < .5f ? mask : 0;
        if (mBarAppearance != appearance) {
            controller.setSystemBarsAppearance(appearance, mask);
            mBarAppearance = appearance;
        }
    }

    private static void setText(TextView view, CharSequence text) {
        if (!TextUtils.equals(view.getText(), text)) view.setText(text);
    }

    private TextView text(int sp, int gravity) {
        TextView view = new TextView(getContext());
        view.setTextColor(mInk);
        view.setTextSize(sp);
        view.setTypeface(Typeface.create(Typeface.DEFAULT, 600, false));
        view.setGravity(gravity);
        view.setIncludeFontPadding(false);
        view.setFallbackLineSpacing(true);
        view.setMaxLines(3);
        return view;
    }

    private Button utility(int id, int text) {
        Button button = new Button(getContext(), null, 0);
        button.setId(id);
        button.setText(text);
        button.setTextSize(13);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setDefaultFocusHighlightEnabled(false);
        button.setTypeface(Typeface.create(Typeface.DEFAULT, 500, false));
        button.setMinWidth(0);
        button.setMinHeight(dp(48));
        button.setMinimumWidth(0);
        button.setMinimumHeight(dp(48));
        button.setMaxLines(3);
        button.setPadding(dp(8), dp(8), dp(8), dp(8));
        button.setBackground(new WelcomeButtonDrawable(Color.TRANSPARENT, mInk, dp(24)));
        button.setStateListAnimator(null);
        return button;
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec), height = MeasureSpec.getSize(heightSpec);
        setMeasuredDimension(width, height);
        mArt.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
        boolean landscape = width - getPaddingLeft() - getPaddingRight()
                > height - getPaddingTop() - getPaddingBottom();
        int contentWidth = Math.max(0,
                Math.min(dp(600), width - getPaddingLeft() - getPaddingRight() - dp(48)));
        int textWidth = landscape ? Math.min(contentWidth, width / 2 - dp(32)) : contentWidth;
        measureText(mTagline, textWidth);
        measureText(mGreeting, textWidth);
        int logoWidth = Math.min(dp(168), contentWidth);
        mLogo.measure(MeasureSpec.makeMeasureSpec(logoWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(logoWidth * 82 / 250, MeasureSpec.EXACTLY));
        mNext.measure(MeasureSpec.makeMeasureSpec(Math.max(0, textWidth), MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        mNext.measure(MeasureSpec.makeMeasureSpec(
                        Math.min(Math.max(0, textWidth), Math.max(dp(136), mNext.getMeasuredWidth())),
                        MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(Math.max(dp(56), mNext.getMeasuredHeight()),
                        MeasureSpec.EXACTLY));
        measureText(mSkipIntro, Math.min(contentWidth, dp(224)));
        int count = 0;
        for (Button button : mUtilities) if (button.getVisibility() != GONE) count++;
        int utilityWidth = (width - getPaddingLeft() - getPaddingRight() - dp(32)
                - dp(8) * Math.max(0, count - 1)) / Math.max(1, count);
        int utilityHeight = dp(48);
        for (Button button : mUtilities) {
            if (button.getVisibility() == GONE) continue;
            measureText(button, utilityWidth);
            utilityHeight = Math.max(utilityHeight, button.getMeasuredHeight());
        }
        for (Button button : mUtilities) {
            if (button.getVisibility() == GONE) continue;
            button.measure(MeasureSpec.makeMeasureSpec(utilityWidth, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(utilityHeight, MeasureSpec.EXACTLY));
        }
    }

    private void measureText(View view, int width) {
        view.measure(MeasureSpec.makeMeasureSpec(Math.max(0, width), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int width = right - left, height = bottom - top;
        mArt.layout(0, 0, width, height);
        int safeWidth = width - getPaddingLeft() - getPaddingRight();
        int safeHeight = height - getPaddingTop() - getPaddingBottom();
        boolean landscape = safeWidth > safeHeight;
        float cx = getPaddingLeft() + safeWidth * .5f;
        int footerHeight = 0, count = 0;
        for (Button button : mUtilities) {
            if (button.getVisibility() == GONE) continue;
            footerHeight = Math.max(footerHeight, button.getMeasuredHeight());
            count++;
        }
        float utilityWidth = (safeWidth - dp(32) - dp(8) * Math.max(0, count - 1))
                / (float) Math.max(1, count);
        float utilityStep = utilityWidth + dp(8);
        boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        float utilityX = rtl ? width - getPaddingRight() - dp(16) - utilityWidth
                : getPaddingLeft() + dp(16);
        float utilityY = height - getPaddingBottom() - footerHeight - dp(16);
        for (Button button : mUtilities) {
            if (button.getVisibility() == GONE) continue;
            place(button, utilityX, utilityY);
            utilityX += rtl ? -utilityStep : utilityStep;
        }
        float actionX = landscape ? getPaddingLeft() + safeWidth * .74f : cx;
        float nextY = Math.min(getPaddingTop() + safeHeight * (landscape ? .65f : .81f),
                utilityY - dp(24) - mNext.getMeasuredHeight() / 2f);
        float greetingY = Math.min(getPaddingTop() + safeHeight * (landscape ? .3f : .48f),
                nextY - mNext.getMeasuredHeight() / 2f - dp(28)
                        - mTagline.getMeasuredHeight() - dp(16)
                        - mGreeting.getMeasuredHeight() / 2f);
        center(mGreeting, actionX, greetingY);
        center(mTagline, actionX, greetingY + mGreeting.getMeasuredHeight() / 2f
                + dp(16) + mTagline.getMeasuredHeight() / 2f);
        center(mNext, actionX, nextY);
        center(mSkipIntro, cx, getPaddingTop() + mSkipIntro.getMeasuredHeight() / 2f + dp(16));
        float logoX = landscape ? getPaddingLeft() + safeWidth * .27f : cx;
        float logoY = landscape ? getPaddingTop() + safeHeight * .42f
                : Math.min(getPaddingTop() + safeHeight * .28f,
                        greetingY - mGreeting.getMeasuredHeight() / 2f - dp(32)
                                - mLogo.getMeasuredHeight() / 2f);
        center(mLogo, logoX, logoY);
    }

    private void center(View view, float x, float y) {
        place(view, x - view.getMeasuredWidth() / 2f, y - view.getMeasuredHeight() / 2f);
    }

    private void place(View view, float x, float y) {
        int left = Math.round(x), top = Math.round(y);
        view.layout(left, top, left + view.getMeasuredWidth(), top + view.getMeasuredHeight());
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

}
