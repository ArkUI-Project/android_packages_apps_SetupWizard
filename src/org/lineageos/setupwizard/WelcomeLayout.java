/*
 * SPDX-FileCopyrightText: 2026 The ArkUI Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.setupwizard;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
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

import java.util.ArrayList;
import java.util.List;

/** A single persistent welcome scene: intro, type, artwork and actions share one timeline. */
public final class WelcomeLayout extends ViewGroup implements Choreographer.FrameCallback {
    static final long MAIN_START = 13000;
    private static final long GREETING_LENGTH = 4700;
    private final WelcomeArtView mArt;
    private final TextView mIntro, mTagline, mGreeting;
    private final ImageView mLogo;
    private final Button mNext, mSkipIntro;
    private final Button[] mUtilities;
    private final AccessibilityManager mAccessibility;
    private final AccessibilityManager.TouchExplorationStateChangeListener mExplorationListener =
            enabled -> updateAnimationState();
    private final ValueAnimator.DurationScaleChangeListener mDurationListener =
            this::onDurationScaleChanged;
    private final int mInk, mUtilitySurface;
    private final String mIntroText, mTaglineText;
    private final List<String> mGreetings = new ArrayList<>();
    private CharSequence mWelcomeMessage;
    private long mElapsed, mLastFrameNanos;
    private double mFractionalMillis;
    private int mPhase = -1;
    private int mBarAppearance = -1;
    private boolean mResumed, mRunning, mMotionEnabled = true, mManaged;

    public WelcomeLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        setClipChildren(false);
        setClipToPadding(false);
        mInk = context.getColor(com.android.internal.R.color.materialColorOnSurface);
        mUtilitySurface = context.getColor(
                com.android.internal.R.color.materialColorSurfaceContainer);
        mAccessibility = context.getSystemService(AccessibilityManager.class);
        mIntroText = context.getString(R.string.welcome_intro);
        mTaglineText = context.getString(R.string.welcome_tagline);
        mWelcomeMessage = context.getString(R.string.setup_welcome_message,
                context.getString(R.string.os_name));
        mGreetings.add(context.getString(R.string.welcome_hello));
        for (String greeting : new String[]{"你好", "Hello", "Bonjour", "Hola", "안녕하세요",
                "こんにちは", "Hallo"}) {
            if (!mGreetings.contains(greeting)) mGreetings.add(greeting);
        }

        mArt = new WelcomeArtView(context);
        addView(mArt);
        mIntro = text(32, Gravity.START);
        mIntro.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(mIntro);
        mTagline = text(32, Gravity.START);
        mTagline.setTextColor(context.getColor(com.android.internal.R.color.materialColorPrimary));
        mTagline.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
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
        mNext = new ArrowButton(context);
        mNext.setId(R.id.start);
        mNext.setContentDescription(context.getString(R.string.start));
        int container = context.getColor(com.android.internal.R.color.materialColorPrimaryContainer);
        int onContainer = context.getColor(
                com.android.internal.R.color.materialColorOnPrimaryContainer);
        mNext.setBackground(new WelcomeButtonDrawable(container, onContainer, dp(28)));
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
        int phase = mElapsed < 6000 ? 0 : mElapsed < 9500 ? 1
                : mElapsed < MAIN_START ? 2 : 3;
        if (phase != mPhase) {
            mPhase = phase;
            mIntro.setVisibility(phase == 1 ? VISIBLE : INVISIBLE);
            mTagline.setVisibility(phase == 2 ? VISIBLE : INVISIBLE);
            mLogo.setVisibility(phase >= 2 ? VISIBLE : INVISIBLE);
            mGreeting.setVisibility(phase == 3 ? VISIBLE : INVISIBLE);
            mNext.setVisibility(phase == 3 ? VISIBLE : INVISIBLE);
            mSkipIntro.setVisibility(phase == 3 ? GONE : VISIBLE);
            requestLayout();
        }
        float background = WelcomeMotion.effect((mElapsed - 5000) / 800f);
        int utilityInk = blend(Color.WHITE, mInk, background);
        int utilityFill = blend(0x22ffffff, mUtilitySurface, background);
        for (Button button : mUtilities) updateUtility(button, utilityFill, utilityInk);
        updateUtility(mSkipIntro, utilityFill, utilityInk);
        updateSystemBarAppearance(background);
        if (phase == 1) {
            typed(mIntro, mIntroText, mElapsed - 6200, 160);
            mIntro.setAlpha(1 - WelcomeMotion.effect((mElapsed - 9250) / 250f));
        } else if (phase == 2) {
            float opacity = 1 - WelcomeMotion.effect((mElapsed - 12650) / 350f);
            mLogo.setAlpha(WelcomeMotion.effect((mElapsed - 9500) / 300f) * opacity);
            typed(mTagline, mTaglineText, mElapsed - 10200, 160);
            mTagline.setAlpha(opacity);
        } else if (phase == 3) {
            float enter = mMotionEnabled
                    ? WelcomeMotion.effect((mElapsed - MAIN_START) / 380f) : 1;
            float spatial = mMotionEnabled
                    ? WelcomeMotion.entrance((mElapsed - MAIN_START) / 1000f) : 1;
            mLogo.setAlpha(enter * .65f);
            mNext.setAlpha(enter);
            mNext.setTranslationY(dp(16) * (1 - spatial));
            mNext.setScaleX(.94f + .06f * spatial);
            mNext.setScaleY(.94f + .06f * spatial);
            mGreeting.setAlpha(enter);
            if (mManaged) setText(mGreeting, mWelcomeMessage);
            else if (!mMotionEnabled) setText(mGreeting, mGreetings.get(0));
            else {
                long cycle = mElapsed - MAIN_START;
                String greeting = mGreetings.get((int) (cycle / GREETING_LENGTH
                        % mGreetings.size()));
                long local = cycle % GREETING_LENGTH;
                typed(mGreeting, greeting, local - 120, 90);
                mGreeting.setAlpha(enter * (1 - WelcomeMotion.effect(
                        (local - GREETING_LENGTH + 180) / 180f)));
            }
        }
        mArt.setFrame(mElapsed, mMotionEnabled);
    }

    private void updateUtility(Button button, int fill, int ink) {
        button.setTextColor(ink);
        ((WelcomeButtonDrawable) button.getBackground()).setColors(fill, ink);
    }

    private void updateSystemBarAppearance(float reveal) {
        WindowInsetsController controller = getWindowInsetsController();
        if (controller == null) return;
        int mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
        int appearance = reveal > .5f && Color.luminance(mInk) < .5f ? mask : 0;
        if (mBarAppearance != appearance) {
            controller.setSystemBarsAppearance(appearance, mask);
            mBarAppearance = appearance;
        }
    }

    private void typed(TextView view, String text, long elapsed, int interval) {
        int count = text.codePointCount(0, text.length());
        int visible = (int) Math.max(0, Math.min(count, elapsed / interval));
        String result = text.substring(0, text.offsetByCodePoints(0, visible));
        boolean cursor = elapsed >= 0 && elapsed < count * interval + 700
                && elapsed % 700 < 420;
        setText(view, cursor ? result + "\u2009|" : result);
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
        int contentWidth = Math.min(dp(600), width - getPaddingLeft() - getPaddingRight() - dp(48));
        measureText(mIntro, contentWidth);
        measureText(mTagline, contentWidth);
        measureText(mGreeting, landscape ? Math.min(contentWidth, width / 2 - dp(32)) : contentWidth);
        int logoWidth = dp(mPhase == 2 ? 144 : 80);
        mLogo.measure(MeasureSpec.makeMeasureSpec(logoWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(logoWidth * 82 / 250, MeasureSpec.EXACTLY));
        mNext.measure(MeasureSpec.makeMeasureSpec(dp(136), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(dp(56), MeasureSpec.EXACTLY));
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
        float contentLeft = getPaddingLeft() + (safeWidth - mIntro.getMeasuredWidth()) * .5f;
        float introY = getPaddingTop() + safeHeight * .145f;
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
        place(mIntro, contentLeft, introY);
        place(mTagline, contentLeft, introY + mLogo.getMeasuredHeight() + dp(16));
        if (mPhase == 2) place(mLogo, rtl
                ? contentLeft + mIntro.getMeasuredWidth() - mLogo.getMeasuredWidth()
                : contentLeft, introY);
        else center(mLogo, cx, getPaddingTop() + dp(32));

        float actionX = landscape ? getPaddingLeft() + safeWidth * .74f : cx;
        float nextY = Math.min(getPaddingTop() + safeHeight * (landscape ? .65f : .81f),
                utilityY - dp(24) - mNext.getMeasuredHeight() / 2f);
        float greetingY = Math.min(getPaddingTop() + safeHeight * (landscape ? .35f : .655f),
                nextY - mNext.getMeasuredHeight() / 2f - dp(28)
                        - mGreeting.getMeasuredHeight() / 2f);
        center(mGreeting, actionX, greetingY);
        center(mNext, actionX, nextY);
        center(mSkipIntro, cx, nextY);
        float artX = landscape ? getPaddingLeft() + safeWidth * .27f : cx;
        float artY = getPaddingTop() + safeHeight * (landscape ? .43f : .34f);
        float radius = Math.min(safeWidth * (landscape ? .15f : .42f), safeHeight * .22f);
        mArt.setScene(artX, artY, radius);
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

    private static int blend(int from, int to, float amount) {
        return Color.argb(Math.round(Color.alpha(from) + (Color.alpha(to) - Color.alpha(from)) * amount),
                Math.round(Color.red(from) + (Color.red(to) - Color.red(from)) * amount),
                Math.round(Color.green(from) + (Color.green(to) - Color.green(from)) * amount),
                Math.round(Color.blue(from) + (Color.blue(to) - Color.blue(from)) * amount));
    }

    private static final class ArrowButton extends Button {
        private final Drawable mArrow;

        ArrowButton(Context context) {
            super(context, null, 0);
            setMinWidth(0);
            setMinHeight(0);
            setMinimumWidth(0);
            setMinimumHeight(0);
            mArrow = context.getDrawable(R.drawable.ic_welcome_next).mutate();
            mArrow.setTint(context.getColor(com.android.internal.R.color.materialColorOnPrimaryContainer));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int size = Math.round(24 * getResources().getDisplayMetrics().density);
            int left = (getWidth() - size) / 2, top = (getHeight() - size) / 2;
            mArrow.setLayoutDirection(getLayoutDirection());
            mArrow.setBounds(left, top, left + size, top + size);
            mArrow.draw(canvas);
        }
    }
}
