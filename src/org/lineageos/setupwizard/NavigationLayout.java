/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-FileCopyrightText: 2026 The ArkUI Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.setupwizard;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;
import android.widget.RelativeLayout;

import com.google.android.setupcompat.template.FooterButtonStyleUtils;

public class NavigationLayout extends RelativeLayout {
    /*
     * An interface to listen to events of the navigation bar,
     * namely when the user clicks on the back or next button.
     */
    public interface NavigationBarListener {
        void onNavigateNext();

        void onSkip();
    }

    private final Button mNextButton;
    private final Button mSkipButton;

    public NavigationLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        View.inflate(context, R.layout.navigation_layout, this);
        mNextButton = findViewById(R.id.navbar_next);
        mSkipButton = findViewById(R.id.navbar_skip);
        FooterButtonStyleUtils.applyPrimaryButtonPartnerResource(context, mNextButton, true);
        FooterButtonStyleUtils.applySecondaryButtonPartnerResource(context, mSkipButton, true);
        styleButton(mNextButton, true);
        styleButton(mSkipButton, false);

        TypedArray a = context.getTheme().obtainStyledAttributes(
                attrs, R.styleable.NavigationLayout, 0, 0);
        final boolean showSkipButton;
        try {
            showSkipButton = a.getBoolean(
                    R.styleable.NavigationLayout_showSkipButton, false);
        } finally {
            a.recycle();
        }

        if (showSkipButton) {
            mSkipButton.setVisibility(View.VISIBLE);
        }
    }

    public Button getSkipButton() {
        return mSkipButton;
    }

    private void styleButton(Button button, boolean primary) {
        Context context = getContext();
        float density = getResources().getDisplayMetrics().density;
        int ink = context.getColor(primary ? com.android.internal.R.color.materialColorOnPrimary
                : com.android.internal.R.color.materialColorPrimary);
        int fill = primary ? context.getColor(com.android.internal.R.color.materialColorPrimary)
                : Color.TRANSPARENT;
        button.setBackground(new WelcomeButtonDrawable(fill, ink, 28 * density));
        button.setBackgroundTintList(null);
        button.setTextColor(new ColorStateList(new int[][]{
                new int[]{-android.R.attr.state_enabled}, new int[]{}},
                new int[]{(ink & 0x00ffffff) | 0x61000000, ink}));
        button.setAllCaps(false);
        button.setTextSize(16);
        button.setTypeface(Typeface.create(Typeface.DEFAULT, 500, false));
        button.setMinHeight(Math.round(56 * density));
        button.setMinimumHeight(Math.round(56 * density));
        button.setMinWidth(Math.round((primary ? 136 : 0) * density));
        button.setPadding(Math.round(24 * density), Math.round(8 * density),
                Math.round(24 * density), Math.round(8 * density));
        button.setStateListAnimator(null);
    }

    public Button getNextButton() {
        return mNextButton;
    }

    public void setNavigationBarListener(NavigationBarListener listener) {
        mSkipButton.setOnClickListener(view -> listener.onSkip());
        mNextButton.setOnClickListener(view -> listener.onNavigateNext());
    }
}
