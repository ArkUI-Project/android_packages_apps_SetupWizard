/*
 * SPDX-FileCopyrightText: 2016 The CyanogenMod Project
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-FileCopyrightText: 2026 The ArkUI Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.setupwizard;

import static com.google.android.setupcompat.util.ResultCodes.RESULT_SKIP;

import static org.lineageos.setupwizard.SetupWizardApp.DISABLE_NAV_KEYS;

import android.content.Context;
import android.os.Bundle;
import android.os.UserHandle;

import androidx.preference.SwitchPreferenceCompat;

import lineageos.hardware.LineageHardwareManager;
import lineageos.providers.LineageSettings;

public class LineageSettingsActivity extends BaseSetupWizardActivity {

    private SetupWizardApp mSetupWizardApp;

    private SwitchPreferenceCompat mNavKeys;

    private boolean mSupportsKeyDisabler = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mSetupWizardApp = (SetupWizardApp) getApplication();
        mSupportsKeyDisabler = isKeyDisablerSupported(this);
        if (!mSupportsKeyDisabler) {
            finishAction(RESULT_SKIP);
            return;
        }

        setNextText(R.string.next);

        mNavKeys = findSetupPreference("nav_keys");
        mNavKeys.setOnPreferenceChangeListener((preference, value) -> {
            mSetupWizardApp.getSettingsBundle().putBoolean(DISABLE_NAV_KEYS, (boolean) value);
            return true;
        });
        mNavKeys.setChecked(LineageSettings.System.getIntForUser(getContentResolver(),
                LineageSettings.System.FORCE_SHOW_NAVBAR, 0, UserHandle.USER_CURRENT) != 0);
    }

    @Override
    public void onResume() {
        super.onResume();
        updateDisableNavkeysOption();
    }

    @Override
    protected int getLayoutResId() {
        return R.layout.setup_preferences;
    }

    @Override
    protected int getPreferencesResId() {
        return R.xml.setup_lineage;
    }

    @Override
    protected int getTitleResId() {
        return R.string.setup_services;
    }

    @Override
    protected int getIconResId() {
        return R.drawable.ic_features;
    }

    private void updateDisableNavkeysOption() {
        if (mSupportsKeyDisabler) {
            final Bundle myPageBundle = mSetupWizardApp.getSettingsBundle();
            boolean enabled = LineageSettings.System.getIntForUser(getContentResolver(),
                    LineageSettings.System.FORCE_SHOW_NAVBAR, 0, UserHandle.USER_CURRENT) != 0;
            boolean checked = myPageBundle.containsKey(DISABLE_NAV_KEYS) ?
                    myPageBundle.getBoolean(DISABLE_NAV_KEYS) :
                    enabled;
            mNavKeys.setChecked(checked);
            myPageBundle.putBoolean(DISABLE_NAV_KEYS, checked);
        }
    }

    public static boolean isKeyDisablerSupported(Context context) {
        final LineageHardwareManager hardware = LineageHardwareManager.getInstance(context);
        return hardware.isSupported(LineageHardwareManager.FEATURE_KEY_DISABLE);
    }
}
