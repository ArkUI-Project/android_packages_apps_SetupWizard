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
import android.view.View;
import android.widget.CheckBox;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;

import com.android.settingslib.utils.ThreadUtils;

public class LocationSettingsActivity extends BaseSetupWizardActivity {

    private CheckBox mLocationAccess;
    private CheckBox mLocationAgpsAccess;

    private LocationManager mLocationManager;

    private UserManager mUserManager;
    private boolean mSaving;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setNextText(R.string.next);

        mLocationAccess = findViewById(R.id.location_checkbox);
        mLocationAgpsAccess = findViewById(R.id.location_agps_checkbox);
        mLocationManager = getSystemService(LocationManager.class);
        mUserManager = getSystemService(UserManager.class);
        View locationAccessView = findViewById(R.id.location);
        locationAccessView.setOnClickListener(
                v -> mLocationAccess.setChecked(!mLocationAccess.isChecked()));
        View locationAgpsAccessView = findViewById(R.id.location_agps);
        if (mUserManager.isMainUser()) {
            locationAgpsAccessView.setOnClickListener(
                    v -> mLocationAgpsAccess.setChecked(!mLocationAgpsAccess.isChecked()));
        } else {
            locationAgpsAccessView.setVisibility(View.GONE);
        }
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
        findViewById(R.id.location).setEnabled(!saving);
        findViewById(R.id.location_agps).setEnabled(!saving);
        getGlifLayout().setProgressBarShown(saving);
    }

    @Override
    protected void onNextIntentResult(ActivityResult result) {
        super.onNextIntentResult(result);
        setSaving(false);
    }

    @Override
    protected int getLayoutResId() {
        return R.layout.location_settings;
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
