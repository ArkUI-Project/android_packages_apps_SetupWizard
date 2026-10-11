/*
 * SPDX-FileCopyrightText: 2016 The CyanogenMod Project
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.setupwizard;

import android.app.AlarmManager;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateFormat;
import android.widget.DatePicker;
import android.widget.TimePicker;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.preference.ListPreference;
import androidx.preference.Preference;

import com.android.settingslib.datetime.ZoneGetter;

import org.lineageos.setupwizard.util.SetupWizardUtils;

import java.util.Calendar;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

public class DateTimeActivity extends BaseSetupWizardActivity implements
        TimePickerDialog.OnTimeSetListener, DatePickerDialog.OnDateSetListener {

    private static final String KEY_ID = "id"; // value: String
    private static final String KEY_DISPLAYNAME = "name"; // value: String
    private static final String KEY_GMT = "gmt"; // value: String
    private static final String KEY_OFFSET = "offset"; // value: int (Integer)

    private ListPreference mTimeZone;
    private Preference mDate;
    private Preference mTime;

    private final Handler mHandler = new Handler(Looper.getMainLooper());

    private final BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateTimeAndDateDisplay();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setNextText(R.string.next);
        getGlifLayout().setDescriptionText(getString(R.string.date_time_summary));

        mTimeZone = findSetupPreference("timezone");
        mDate = findSetupPreference("date");
        mTime = findSetupPreference("time");
        populateTimeZones();
        mTimeZone.setOnPreferenceChangeListener((preference, value) -> {
            String zoneId = (String) value;
            if (!TimeZone.getDefault().getID().equals(zoneId)) {
                getSystemService(AlarmManager.class).setTimeZone(zoneId);
            }
            return true;
        });
        mDate.setOnPreferenceClickListener(preference -> {
            showDatePicker();
            return true;
        });
        mTime.setOnPreferenceClickListener(preference -> {
            showTimePicker();
            return true;
        });
        // Pre-select current/default date if epoch
        mHandler.post(() -> {
            final Calendar calendar = Calendar.getInstance();
            final boolean isEpoch = calendar.get(Calendar.YEAR) == 1970;
            if (isEpoch) {
                // If epoch, set date to build date
                long timestamp = SetupWizardUtils.getBuildDateTimestamp();
                if (timestamp > 0) {
                    calendar.setTimeInMillis(timestamp * 1000);
                    setDate(DateTimeActivity.this, calendar.get(Calendar.YEAR),
                            calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH));
                } else {
                    // no build date available, use a sane default
                    setDate(DateTimeActivity.this, 2017, Calendar.JANUARY, 1);
                }
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        // Register for time ticks and other reasons for time change
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_TIME_TICK);
        filter.addAction(Intent.ACTION_TIME_CHANGED);
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver(mIntentReceiver, filter, null, null);

        updateTimeAndDateDisplay();
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterReceiver(mIntentReceiver);
    }

    @Override
    protected int getLayoutResId() {
        return R.layout.setup_preferences;
    }

    @Override
    protected int getPreferencesResId() {
        return R.xml.setup_datetime;
    }

    @Override
    protected int getTitleResId() {
        return R.string.setup_datetime;
    }

    @Override
    protected int getIconResId() {
        return R.drawable.ic_datetime;
    }

    @Override
    public void onDateSet(DatePicker view, int year, int month, int day) {
        setDate(this, year, month, day);
        updateTimeAndDateDisplay();
    }

    @Override
    public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
        setTime(this, hourOfDay, minute);
        updateTimeAndDateDisplay();
    }

    private void showDatePicker() {
        DatePickerFragment datePickerFragment = DatePickerFragment.newInstance();
        datePickerFragment.show(getSupportFragmentManager(), DatePickerFragment.TAG);
    }

    private void showTimePicker() {
        TimePickerFragment timePickerFragment = TimePickerFragment.newInstance();
        timePickerFragment.show(getSupportFragmentManager(), TimePickerFragment.TAG);
    }

    private void updateTimeAndDateDisplay() {
        java.text.DateFormat shortDateFormat = DateFormat.getDateFormat(this);
        final Calendar now = Calendar.getInstance();
        mTime.setSummary(DateFormat.getTimeFormat(this).format(now.getTime()));
        mDate.setSummary(shortDateFormat.format(now.getTime()));
        mTimeZone.setValue(TimeZone.getDefault().getID());
    }

    private void populateTimeZones() {
        List<Map<String, Object>> zones = ZoneGetter.getZonesList(this);
        zones.sort(Comparator.comparingInt(zone -> ((Number) zone.get(KEY_OFFSET)).intValue()));
        CharSequence[] entries = new CharSequence[zones.size()];
        CharSequence[] values = new CharSequence[zones.size()];
        for (int i = 0; i < zones.size(); i++) {
            Map<String, Object> zone = zones.get(i);
            entries[i] = zone.get(KEY_DISPLAYNAME) + " (" + zone.get(KEY_GMT) + ")";
            values[i] = (String) zone.get(KEY_ID);
        }
        mTimeZone.setEntries(entries);
        mTimeZone.setEntryValues(values);
        mTimeZone.setValue(TimeZone.getDefault().getID());
        mTimeZone.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());
    }

    private static void setDate(Context context, int year, int month, int day) {
        Calendar c = Calendar.getInstance();

        c.set(Calendar.YEAR, year);
        c.set(Calendar.MONTH, month);
        c.set(Calendar.DAY_OF_MONTH, day);
        long when = c.getTimeInMillis();

        if (when / 1000 < Integer.MAX_VALUE) {
            ((AlarmManager) context.getSystemService(Context.ALARM_SERVICE)).setTime(when);
        }
    }

    private static void setTime(Context context, int hourOfDay, int minute) {
        Calendar c = Calendar.getInstance();

        c.set(Calendar.HOUR_OF_DAY, hourOfDay);
        c.set(Calendar.MINUTE, minute);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        long when = c.getTimeInMillis();

        if (when / 1000 < Integer.MAX_VALUE) {
            ((AlarmManager) context.getSystemService(Context.ALARM_SERVICE)).setTime(when);
        }
    }

    public static class TimePickerFragment extends DialogFragment
            implements TimePickerDialog.OnTimeSetListener {

        private static final String TAG = TimePickerFragment.class.getSimpleName();

        public static TimePickerFragment newInstance() {
            return new TimePickerFragment();
        }

        @Override
        public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
            ((DateTimeActivity) getActivity()).onTimeSet(view, hourOfDay, minute);
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Calendar calendar = Calendar.getInstance();
            return new TimePickerDialog(
                    getActivity(),
                    this,
                    calendar.get(Calendar.HOUR_OF_DAY),
                    calendar.get(Calendar.MINUTE),
                    DateFormat.is24HourFormat(getActivity()));
        }
    }

    public static class DatePickerFragment extends DialogFragment
            implements DatePickerDialog.OnDateSetListener {

        private static final String TAG = DatePickerFragment.class.getSimpleName();

        public static DatePickerFragment newInstance() {
            return new DatePickerFragment();
        }

        @Override
        public void onDateSet(DatePicker view, int year, int month, int day) {
            ((DateTimeActivity) getActivity()).onDateSet(view, year, month, day);
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Calendar calendar = Calendar.getInstance();
            return new DatePickerDialog(
                    getActivity(),
                    this,
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH));
        }
    }
}
