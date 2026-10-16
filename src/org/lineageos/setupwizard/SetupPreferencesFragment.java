/*
 * SPDX-FileCopyrightText: 2026 The ArkUI Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.setupwizard;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.preference.PreferenceScreen;
import androidx.recyclerview.widget.RecyclerView;

import com.android.settingslib.widget.SettingsBasePreferenceFragment;
import com.android.settingslib.widget.SettingsPreferenceGroupAdapter;

public class SetupPreferencesFragment extends SettingsBasePreferenceFragment {

    private static final String KEY_RESOURCE = "preference_resource";
    private Context mPreferenceContext;

    public static SetupPreferencesFragment newInstance(int resource) {
        SetupPreferencesFragment fragment = new SetupPreferencesFragment();
        Bundle arguments = new Bundle();
        arguments.putInt(KEY_RESOURCE, resource);
        fragment.setArguments(arguments);
        return fragment;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        mPreferenceContext = new ContextThemeWrapper(context,
                com.android.settingslib.widget.theme.R.style.Theme_SubSettingsBase_Expressive);
        super.onAttach(context);
    }

    @Override
    @Nullable
    public Context getContext() {
        Context context = super.getContext();
        return context == null || mPreferenceContext == null ? context : mPreferenceContext;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mPreferenceContext = null;
    }

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(requireArguments().getInt(KEY_RESOURCE), rootKey);
    }

    @Override
    @NonNull
    public RecyclerView.Adapter<?> onCreateAdapter(@NonNull PreferenceScreen preferenceScreen) {
        return new SettingsPreferenceGroupAdapter(preferenceScreen);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        view.setBackgroundColor(Color.TRANSPARENT);
        RecyclerView list = getListView();
        list.setBackgroundColor(Color.TRANSPARENT);
        list.setPaddingRelative(0, list.getPaddingTop(), 0, list.getPaddingBottom());
        list.setClipToPadding(false);
    }
}
