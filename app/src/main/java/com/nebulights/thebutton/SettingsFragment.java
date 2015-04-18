package com.nebulights.thebutton;


import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;


public class SettingsFragment extends PreferenceFragment {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        boolean isDreamServiceAvailable = true;

        addPreferencesFromResource(R.xml.fragment_settings);

        Intent intent = new Intent();
        intent.setAction("android.settings.DREAM_SETTINGS");

        //Some users didn't have the dream activity available, so a check for that in addition to version is required.
        if (intent.resolveActivity(getActivity().getPackageManager()) == null) {
            isDreamServiceAvailable = false;
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1 || !isDreamServiceAvailable) {
            PreferenceScreen screen = getPreferenceScreen();
            Preference pref = findPreference("daydream");
            screen.removePreference(pref);
        }

    }
}

