/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.setupwizard;

import android.app.UiModeManager;
import android.content.res.Configuration;
import android.os.Bundle;

import androidx.annotation.Nullable;

import com.android.settingslib.widget.SelectorWithWidgetPreference;

public class ThemeSettingsActivity extends BaseSetupWizardActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getGlifLayout().setDescriptionText(getString(R.string.theme_summary));

        UiModeManager uiModeManager = getSystemService(UiModeManager.class);
        SelectorWithWidgetPreference light = findSetupPreference("light");
        SelectorWithWidgetPreference dark = findSetupPreference("dark");
        boolean night = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        light.setChecked(!night);
        dark.setChecked(night);
        light.setOnClickListener(preference -> {
            light.setChecked(true);
            dark.setChecked(false);
            uiModeManager.setNightModeActivated(false);
        });
        dark.setOnClickListener(preference -> {
            light.setChecked(false);
            dark.setChecked(true);
            uiModeManager.setNightModeActivated(true);
        });
    }

    @Override
    protected int getLayoutResId() {
        return R.layout.setup_preferences;
    }

    @Override
    protected int getPreferencesResId() {
        return R.xml.setup_theme;
    }

    @Override
    protected int getTitleResId() {
        return R.string.setup_theme;
    }

    @Override
    protected int getIconResId() {
        return R.drawable.ic_theme;
    }
}
