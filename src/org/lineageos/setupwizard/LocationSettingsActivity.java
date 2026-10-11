/*
 * SPDX-FileCopyrightText: 2016 The CyanogenMod Project
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-FileCopyrightText: 2026 The ArkUI Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.setupwizard;

import android.location.LocationManager;
import android.os.Bundle;
import android.os.Process;
import android.os.UserManager;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.preference.SwitchPreferenceCompat;

import com.android.settingslib.utils.ThreadUtils;

public class LocationSettingsActivity extends BaseSetupWizardActivity {

    private SwitchPreferenceCompat mLocationAccess;
    private SwitchPreferenceCompat mLocationAgpsAccess;

    private LocationManager mLocationManager;

    private UserManager mUserManager;
    private boolean mSaving;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setNextText(R.string.next);

        mLocationAccess = findSetupPreference("location");
        mLocationAgpsAccess = findSetupPreference("location_agps");
        mLocationManager = getSystemService(LocationManager.class);
        mUserManager = getSystemService(UserManager.class);
        mLocationAgpsAccess.setVisible(mUserManager.isMainUser());
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mSaving) return;
        boolean checked = mLocationManager.isLocationEnabled();
        if (mUserManager.isManagedProfile()) {
            checked &= mUserManager.hasUserRestriction(UserManager.DISALLOW_SHARE_LOCATION);
        }
        mLocationAccess.setChecked(checked);
    }

    @Override
    protected void onNextPressed() {
        if (mSaving) return;
        final boolean location = mLocationAccess.isChecked();
        final boolean agps = mLocationAgpsAccess.isChecked();
        setSaving(true);
        ThreadUtils.postOnBackgroundThread(() -> {
            boolean success = false;
            try {
                if (mLocationManager.isLocationEnabled() != location) {
                    mLocationManager.setLocationEnabledForUser(location, Process.myUserHandle());
                }
                if (mUserManager.isManagedProfile()
                        && mUserManager.hasUserRestriction(UserManager.DISALLOW_SHARE_LOCATION)
                                == location) {
                    mUserManager.setUserRestriction(UserManager.DISALLOW_SHARE_LOCATION, !location);
                }
                final int assistedGps = agps ? 1 : 0;
                success = Settings.Global.getInt(getContentResolver(),
                        Settings.Global.ASSISTED_GPS_ENABLED, 1) == assistedGps
                        || Settings.Global.putInt(getContentResolver(),
                                Settings.Global.ASSISTED_GPS_ENABLED, assistedGps);
            } catch (RuntimeException e) {
                Log.e(TAG, "Unable to save location preferences", e);
            }
            final boolean saved = success;
            ThreadUtils.postOnMainThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                getGlifLayout().setProgressBarShown(false);
                if (saved) {
                    super.onNextPressed();
                } else {
                    setSaving(false);
                    Toast.makeText(this, R.string.setup_settings_save_failed,
                            Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    private void setSaving(boolean saving) {
        mSaving = saving;
        setNextAllowed(!saving);
        mLocationAccess.setEnabled(!saving);
        mLocationAgpsAccess.setEnabled(!saving);
        getGlifLayout().setProgressBarShown(saving);
    }

    @Override
    protected void onNextIntentResult(ActivityResult result) {
        super.onNextIntentResult(result);
        setSaving(false);
    }

    @Override
    protected int getLayoutResId() {
        return R.layout.setup_preferences;
    }

    @Override
    protected int getPreferencesResId() {
        return R.xml.setup_location;
    }

    @Override
    protected int getTitleResId() {
        return R.string.setup_location;
    }

    @Override
    protected int getIconResId() {
        return R.drawable.ic_location;
    }

}
