/*
 * SPDX-FileCopyrightText: 2016 The CyanogenMod Project
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-FileCopyrightText: 2026 The ArkUI Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.setupwizard.wizardmanager;

import static com.google.android.setupcompat.util.WizardManagerHelper.ACTION_NEXT;

import static org.lineageos.setupwizard.SetupWizardApp.ACTION_LOAD;
import static org.lineageos.setupwizard.SetupWizardApp.EXTRA_ACTION_ID;
import static org.lineageos.setupwizard.SetupWizardApp.EXTRA_RESULT_CODE;
import static org.lineageos.setupwizard.SetupWizardApp.EXTRA_SCRIPT_URI;
import static org.lineageos.setupwizard.SetupWizardApp.EXTRA_WIZARD_BUNDLE;
import static org.lineageos.setupwizard.SetupWizardApp.LOGV;

import android.annotation.Nullable;
import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;

import com.google.android.setupcompat.util.ResultCodes;

import org.lineageos.setupwizard.DeviceSpecificActivity;
import org.lineageos.setupwizard.LineageSettingsActivity;
import org.lineageos.setupwizard.ScreenLockActivity;
import org.lineageos.setupwizard.util.SetupWizardUtils;

import java.util.HashMap;

public class WizardManager extends Activity {

    private static final String TAG = WizardManager.class.getSimpleName();

    private static final HashMap<String, WizardScript> sWizardScripts = new HashMap<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (LOGV) {
            Log.v(TAG, "onCreate savedInstanceState=" + savedInstanceState);
        }
        Intent intent = this.getIntent();
        if (intent != null) {
            String action = intent.getAction();
            int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0);
            Bundle wizardBundle = intent.getBundleExtra(EXTRA_WIZARD_BUNDLE);
            String scriptUri = wizardBundle.getString(EXTRA_SCRIPT_URI);
            String actionId = wizardBundle.getString(EXTRA_ACTION_ID);
            if (LOGV) {
                Log.v(TAG, "  action=" + action + " resultCode=" + resultCode + " scriptUri="
                        + scriptUri + " actionId=" + actionId + " extras=" + intent.getExtras());
            }

            if (ACTION_LOAD.equals(action)) {
                load(scriptUri, intent);
                finish();
                return;
            }

            if (ACTION_NEXT.equals(action)) {
                next(scriptUri, actionId, resultCode, intent);
                finish();
                return;
            }

            Log.e(TAG, "ERROR: Unknown action");
        } else {
            Log.e(TAG, "ERROR: Intent not available");
        }
        finish();
    }

    private void doAction(String scriptUri, WizardAction action, Intent extras) {
        Intent intent = action.getIntent();
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if (LOGV) {
            Log.v(TAG, "doAction scriptUri=" + scriptUri + " extras=" + extras
                    + " intent=" + intent + " extras2=" + intent.getExtras() + " action=" + action);
        }

        if (extras != null) {
            intent.putExtras(extras);
        }

        Bundle wizardBundle = new Bundle();
        wizardBundle.putString(EXTRA_SCRIPT_URI, scriptUri);
        wizardBundle.putString(EXTRA_ACTION_ID, action.getId());
        intent.putExtra(EXTRA_WIZARD_BUNDLE, wizardBundle);
        intent.addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT);
        if (isRelayAction(intent)) {
            // Relays only forward to Settings; animate the actual destination once it is ready.
            startActivity(intent, ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle());
        } else {
            startActivity(intent);
        }
    }

    private void load(String scriptUri, Intent extras) {
        WizardScript wizardScript = getWizardScript(this, scriptUri);
        WizardAction wizardAction;
        for (wizardAction = wizardScript.getFirstAction();
                wizardAction != null;
                wizardAction = wizardScript.getNextAction(wizardAction.getId(),
                        ResultCodes.RESULT_ACTIVITY_NOT_FOUND)) {
            if (isActionAvailable(this, wizardAction)) {
                break;
            }

            if (LOGV) {
                Log.v(TAG, "load action not available " + wizardAction);
            }
        }

        if (wizardAction != null) {
            doAction(scriptUri, wizardAction, extras);
        } else {
            Log.e(TAG, "load could not resolve first action scriptUri=" +
                    scriptUri + " actionId=" + wizardScript.getFirstActionId());
            exit(scriptUri);
        }
    }

    private void next(String scriptUri, String actionId, int resultCode, Intent extras) {
        if (LOGV) {
            Log.v(TAG, "next actionId=" + actionId + " resultCode=" + resultCode);
        }
        WizardAction wizardAction = checkNextAction(this, scriptUri,
                actionId, resultCode);
        if (wizardAction != null) {
            doAction(scriptUri, wizardAction, extras);
        } else {
            exit(scriptUri);
        }
    }

    private void exit(String scriptUri) {
        if (LOGV) {
            Log.v(TAG, "exit scriptUri=" + scriptUri);
        }
        WizardManager.sWizardScripts.remove(scriptUri);
        SetupWizardUtils.disableComponent(this, WizardManager.class);
    }

    private static WizardAction checkNextAction(Context context, String scriptUri, String actionId,
            int resultCode) {
        if (LOGV) {
            Log.v(TAG, "checkNextAction scriptUri=" + scriptUri + " actionId="
                    + actionId + " resultCode=" + resultCode);
        }

        WizardScript wizardScript = getWizardScript(context, scriptUri);
        WizardAction wizardAction;
        for (wizardAction = wizardScript.getNextAction(actionId, resultCode);
                wizardAction != null;
                wizardAction = wizardScript.getNextAction(wizardAction.getId(),
                        ResultCodes.RESULT_ACTIVITY_NOT_FOUND)) {
            if (WizardManager.isActionAvailable(context, wizardAction)) {
                break;
            }

            if (LOGV) {
                Log.v(TAG, "checkNextAction action not available " + wizardAction);
            }
        }

        if (LOGV) {
            Log.v(TAG, "checkNextAction action=" + wizardAction);
        }

        return wizardAction;
    }

    private static boolean isActionAvailable(Context context, WizardAction action) {
        Intent intent = action.getIntent();
        if (intent == null) return false;
        String name = intent.getAction();
        if ("org.lineageos.setupwizard.DEVICE_SPECIFIC".equals(name)
                && !isIntentAvailable(context,
                        new Intent(DeviceSpecificActivity.ACTION_SETUP_DEVICE))) {
            return false;
        }
        if ("org.lineageos.setupwizard.LINEAGE_RECOVERY_UPDATE".equals(name)
                && !SetupWizardUtils.hasRecoveryUpdater(context)) {
            return false;
        }
        if ("org.lineageos.setupwizard.LINEAGE_SETTINGS".equals(name)
                && !LineageSettingsActivity.isKeyDisablerSupported(context)) {
            return false;
        }
        if ("org.lineageos.setupwizard.LINEAGE_BIOMETRIC_SETTINGS".equals(name)
                && !SetupWizardUtils.hasBiometric(context)) {
            // The biometric page normally enables this fallback before skipping itself.
            SetupWizardUtils.enableComponent(context, ScreenLockActivity.class);
            return false;
        }
        return isIntentAvailable(context, intent);
    }

    private static boolean isRelayAction(Intent intent) {
        String action = intent.getAction();
        if (action == null) return false;
        return switch (action) {
            case "org.lineageos.setupwizard.LINEAGE_BLUETOOTH_SETUP",
                    "org.lineageos.setupwizard.LINEAGE_NETWORK_SETUP",
                    "org.lineageos.setupwizard.DEVICE_SPECIFIC",
                    "org.lineageos.setupwizard.LINEAGE_BIOMETRIC_SETTINGS",
                    "org.lineageos.setupwizard.LINEAGE_LOCKSCREEN_SETTINGS" -> true;
            default -> false;
        };
    }

    private static boolean isIntentAvailable(Context context, Intent intent) {
        return !context.getPackageManager().queryIntentActivities(intent,
                PackageManager.MATCH_DEFAULT_ONLY).isEmpty();
    }

    private static WizardScript getWizardScript(Context context, String scriptUri) {
        WizardScript wizardScript = sWizardScripts.get(scriptUri);
        if (wizardScript == null) {
            wizardScript = WizardScript.loadFromUri(context, scriptUri);
            sWizardScripts.put(scriptUri, wizardScript);
        }
        return wizardScript;
    }

}
