/*
 * SPDX-FileCopyrightText: 2026 The ArkUI Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.setupwizard;

import android.content.Context;
import android.util.AttributeSet;

import com.google.android.setupdesign.GlifLayout;

/** Uses the app's Settings-style spacing and surfaces throughout the setup flow. */
public final class ArkuiSetupLayout extends GlifLayout {
    public ArkuiSetupLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected boolean enablePartnerResourceLoading() {
        // A first-run intent forces partner resources on even when the theme opts out.
        // They would otherwise replace our margins and neutral surfaces after inflation.
        return false;
    }

    @Override
    public boolean shouldApplyDynamicColor() {
        return false;
    }
}
