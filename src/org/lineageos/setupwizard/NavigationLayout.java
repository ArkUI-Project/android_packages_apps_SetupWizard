/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-FileCopyrightText: 2026 The ArkUI Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.setupwizard;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;
import android.widget.RelativeLayout;

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

    public Button getNextButton() {
        return mNextButton;
    }

    public void setNavigationBarListener(NavigationBarListener listener) {
        mSkipButton.setOnClickListener(view -> listener.onSkip());
        mNextButton.setOnClickListener(view -> listener.onNavigateNext());
    }
}
