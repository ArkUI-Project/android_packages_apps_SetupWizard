/*
 * SPDX-FileCopyrightText: 2026 The ArkUI Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.setupwizard;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.settingslib.widget.SettingsBasePreferenceFragment;

public class SetupPreferencesFragment extends SettingsBasePreferenceFragment {

    private static final String KEY_RESOURCE = "preference_resource";

    public static SetupPreferencesFragment newInstance(int resource) {
        SetupPreferencesFragment fragment = new SetupPreferencesFragment();
        Bundle arguments = new Bundle();
        arguments.putInt(KEY_RESOURCE, resource);
        fragment.setArguments(arguments);
        return fragment;
    }

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(requireArguments().getInt(KEY_RESOURCE), rootKey);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        getListView().setClipToPadding(false);
    }
}
