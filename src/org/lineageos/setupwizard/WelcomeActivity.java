/*
 * SPDX-FileCopyrightText: 2016 The CyanogenMod Project
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-FileCopyrightText: 2026 The ArkUI Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.setupwizard;

import static org.lineageos.setupwizard.SetupWizardApp.ACTION_EMERGENCY_DIAL;

import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.Button;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResult;

import com.google.android.setupcompat.util.SystemBarHelper;

import org.lineageos.setupwizard.util.SetupWizardUtils;

public class WelcomeActivity extends SubBaseActivity {

    private static final String ELAPSED_STATE = "arkui_welcome_elapsed";
    private WelcomeLayout mScene;
    private boolean mContinuing;

    private static final String ACTION_ACCESSIBILITY_SETTINGS =
            "android.settings.ACCESSIBILITY_SETTINGS_FOR_SUW";

    @Override
    protected void onStartSubactivity() {
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        onSetupStart();
        SystemBarHelper.setBackButtonVisible(getWindow(), false);
        getWindow().setDecorFitsSystemWindows(false);
        getWindow().setNavigationBarContrastEnforced(false);
        getWindow().setStatusBarContrastEnforced(false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        getWindow().getInsetsController().setSystemBarsBehavior(
                WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        hideSystemBars();
        mScene = findViewById(R.id.welcome_scene);
        if (savedInstanceState != null) {
            mScene.restoreElapsed(savedInstanceState.getLong(ELAPSED_STATE));
        }
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                mScene.skipIntro();
            }
        });
        Button startButton = findViewById(R.id.start);
        Button emergButton = findViewById(R.id.emerg_dialer);
        Button skipButton = findViewById(R.id.skip);
        startButton.setOnClickListener(view -> {
            if (mContinuing) return;
            mContinuing = true;
            startButton.setEnabled(false);
            onNextPressed();
        });
        findViewById(R.id.launch_accessibility)
                .setOnClickListener(
                        view -> startSubactivity(new Intent(ACTION_ACCESSIBILITY_SETTINGS)));

        if (SetupWizardUtils.hasTelephony(this)) {
            emergButton.setOnClickListener(
                    view -> startSubactivity(new Intent(ACTION_EMERGENCY_DIAL)));
        } else {
            emergButton.setVisibility(View.GONE);
        }

        if (SetupWizardUtils.isManagedProfile(this)) {
            mScene.setWelcomeMessage(getString(R.string.setup_managed_profile_welcome_message), true);
        }

        if (Build.TYPE.equals("eng")) {
            skipButton.setVisibility(View.VISIBLE);
            skipButton.setOnClickListener(v -> {
                SetupWizardUtils.finishSetupWizard(WelcomeActivity.this);
            });
        }
    }

    @Override
    public void onBackPressed() {
        mScene.skipIntro();
    }

    @Override
    protected void onSubactivityResult(ActivityResult result) {
        // Accessibility and emergency dialing return to this step; their result is not a
        // wizard navigation result. The activity lifecycle resumes the same welcome scene.
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemBars();
        mContinuing = false;
        findViewById(R.id.start).setEnabled(true);
        mScene.resume();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemBars();
    }

    private void hideSystemBars() {
        getWindow().getInsetsController().hide(WindowInsets.Type.systemBars());
    }

    @Override
    public void onPause() {
        mScene.pause();
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putLong(ELAPSED_STATE, mScene.getElapsed());
    }

    @Override
    protected int getLayoutResId() {
        return R.layout.arkui_welcome;
    }

    @Override
    protected int getTitleResId() {
        return -1;
    }
}
