/*
 * SPDX-FileCopyrightText: 2026 The ArkUI Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.setupwizard;

import android.content.Context;
import android.view.View;

/** Full-bleed artwork; the welcome layout owns its animation clock and lifecycle. */
final class WelcomeArtView extends View {
    private final ArkuiRibbonDrawable mRibbons;

    WelcomeArtView(Context context) {
        super(context);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        mRibbons = new ArkuiRibbonDrawable(context, 0);
        setBackground(mRibbons);
    }

    void setFrame(long elapsed, boolean animated) {
        mRibbons.setElapsed(elapsed, animated);
    }
}
