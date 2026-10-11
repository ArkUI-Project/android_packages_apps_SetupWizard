/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.setupwizard;

import static android.view.WindowManagerPolicyConstants.NAV_BAR_MODE_3BUTTON_OVERLAY;
import static android.view.WindowManagerPolicyConstants.NAV_BAR_MODE_GESTURAL_OVERLAY;

import static org.lineageos.internal.util.DeviceKeysConstants.KEY_MASK_APP_SWITCH;
import static org.lineageos.setupwizard.SetupWizardApp.DISABLE_NAV_KEYS;
import static org.lineageos.setupwizard.SetupWizardApp.NAVIGATION_OPTION_KEY;

import android.os.Bundle;
import android.os.UserHandle;

import androidx.preference.SwitchPreferenceCompat;

import com.android.settingslib.widget.IllustrationPreference;
import com.android.settingslib.widget.SelectorWithWidgetPreference;

import lineageos.providers.LineageSettings;

import org.lineageos.setupwizard.util.SetupWizardUtils;

public class NavigationSettingsActivity extends BaseSetupWizardActivity {

    private SetupWizardApp mSetupWizardApp;

    private String mSelection = NAV_BAR_MODE_GESTURAL_OVERLAY;

    private SwitchPreferenceCompat mHideGesturalHint;
    private SelectorWithWidgetPreference mGesture;
    private SelectorWithWidgetPreference mThreeButton;
    private IllustrationPreference mIllustration;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mSetupWizardApp = (SetupWizardApp) getApplication();
        boolean navBarEnabled = false;
        if (mSetupWizardApp.getSettingsBundle().containsKey(DISABLE_NAV_KEYS)) {
            navBarEnabled = mSetupWizardApp.getSettingsBundle().getBoolean(DISABLE_NAV_KEYS);
        }

        int deviceKeys = getResources().getInteger(
                org.lineageos.platform.internal.R.integer.config_deviceHardwareKeys);
        boolean hasHomeKey = (deviceKeys & KEY_MASK_APP_SWITCH) != 0;

        getGlifLayout().setDescriptionText(getString(R.string.navigation_summary));
        setNextText(R.string.next);

        mGesture = findSetupPreference("gesture");
        mThreeButton = findSetupPreference("three_button");
        mHideGesturalHint = findSetupPreference("hide_navigation_hint");
        mIllustration = findSetupPreference("navigation_illustration");
        mGesture.setVisible(SetupWizardUtils.isPackageInstalled(
                this, NAV_BAR_MODE_GESTURAL_OVERLAY));
        mThreeButton.setVisible(SetupWizardUtils.isPackageInstalled(
                this, NAV_BAR_MODE_3BUTTON_OVERLAY));
        int available = (mGesture.isVisible() ? 1 : 0) + (mThreeButton.isVisible() ? 1 : 0);

        // Hide this page if the device has hardware keys but didn't enable navbar
        // or if there's <= 1 available navigation modes
        if ((!navBarEnabled && hasHomeKey) || available <= 1) {
            mSetupWizardApp.getSettingsBundle().putString(NAVIGATION_OPTION_KEY,
                    mGesture.isVisible() && (navBarEnabled || !hasHomeKey)
                            ? NAV_BAR_MODE_GESTURAL_OVERLAY
                            : NAV_BAR_MODE_3BUTTON_OVERLAY);
            finishAction(RESULT_OK);
            return;
        }

        mGesture.setOnClickListener(preference -> selectNavigation(NAV_BAR_MODE_GESTURAL_OVERLAY));
        mThreeButton.setOnClickListener(preference -> selectNavigation(NAV_BAR_MODE_3BUTTON_OVERLAY));
        selectNavigation(savedInstanceState == null
                ? mSetupWizardApp.getSettingsBundle().getString(NAVIGATION_OPTION_KEY,
                        NAV_BAR_MODE_GESTURAL_OVERLAY)
                : savedInstanceState.getString(NAVIGATION_OPTION_KEY, NAV_BAR_MODE_GESTURAL_OVERLAY));
    }

    private void selectNavigation(String selection) {
        mSelection = selection;
        boolean gesture = NAV_BAR_MODE_GESTURAL_OVERLAY.equals(selection);
        mGesture.setChecked(gesture);
        mThreeButton.setChecked(!gesture);
        mHideGesturalHint.setVisible(gesture);
        mIllustration.setLottieAnimationResId(gesture
                ? R.raw.lottie_system_nav_fully_gestural : R.raw.lottie_system_nav_3_button);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putString(NAVIGATION_OPTION_KEY, mSelection);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onNextPressed() {
        mSetupWizardApp.getSettingsBundle().putString(NAVIGATION_OPTION_KEY, mSelection);
        boolean hideHint = mHideGesturalHint.isChecked();
        LineageSettings.System.putIntForUser(getContentResolver(),
                LineageSettings.System.NAVIGATION_BAR_HINT, hideHint ? 0 : 1,
                UserHandle.USER_CURRENT);
        super.onNextPressed();
    }

    @Override
    protected int getLayoutResId() {
        return R.layout.setup_preferences;
    }

    @Override
    protected int getPreferencesResId() {
        return R.xml.setup_navigation;
    }

    @Override
    protected int getTitleResId() {
        return R.string.setup_navigation;
    }

    @Override
    protected int getIconResId() {
        return R.drawable.ic_navigation;
    }
}
