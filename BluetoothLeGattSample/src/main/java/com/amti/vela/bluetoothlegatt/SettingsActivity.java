package com.amti.vela.bluetoothlegatt;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Window;
import android.view.WindowManager;

public class SettingsActivity extends AppCompatActivity implements SharedPreferences.OnSharedPreferenceChangeListener {

    static SharedPreferences prefs;

    static Preference device;
    static CheckBoxPreference autoConnect, neverAsk;
    static Preference notificationButton;
    static boolean notificationAccessEnabled;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        overridePendingTransition(R.anim.slidein, R.anim.fadeout);

        //action bar
        final Toolbar actionBarToolBar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(actionBarToolBar);
        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);

        //status bar color
        Window window = getWindow();
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.setStatusBarColor(ContextCompat.getColor(this, R.color.colorStatusBar));
        }

        notificationAccessEnabled = Settings.Secure.getString(this.getContentResolver(),"enabled_notification_listeners").contains(getApplicationContext().getPackageName());;

        prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        prefs.registerOnSharedPreferenceChangeListener(this);
        getFragmentManager().beginTransaction().replace(R.id.settings_frame, new SettingsFragment()).commit();
        getFragmentManager().executePendingTransactions();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        setSummaries();
    }

    public static void setSummaries()
    {
        String deviceName = prefs.getString(Preferences.PREFS_DEVICE_NAME_KEY, "");
        String deviceAddress = prefs.getString(Preferences.PREFS_DEVICE_ADDRESS_KEY, "");
        if(!deviceName.isEmpty())
        {
            device.setSummary(deviceName+"\n"+deviceAddress);
            device.setEnabled(true);
            autoConnect.setEnabled(true);
        }
        else
        {
            device.setSummary("No necklace saved");
            device.setEnabled(false);
            autoConnect.setEnabled(false);
        }

        if (neverAsk.isChecked())
            autoConnect.setEnabled(false);
        else
            autoConnect.setEnabled(true);

        String title = notificationAccessEnabled ? "Enable notification access (enabled)" : "Enable notification access (disabled)";
        if(notificationButton != null)
            notificationButton.setTitle(title);
    }

    public static class SettingsFragment extends PreferenceFragment {
        @Override
        public void onCreate(final Bundle savedInstanceState){
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.settings_items);

            device = findPreference(Preferences.PREFS_DEVICE_KEY);
            autoConnect = (CheckBoxPreference)findPreference(Preferences.PREFS_AUTO_CONNECT_KEY);
            neverAsk = (CheckBoxPreference)findPreference(Preferences.PREFS_NEVER_ASK_KEY);
            notificationButton = findPreference(Preferences.PREFS_NOTIFICATION_KEY);

            device.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                    builder.setMessage("Do you want to forget your current necklace?").setPositiveButton("Yes", renameClickListener).setNegativeButton("Cancel", renameClickListener).show();
                    return true;
                }
            });

            notificationButton.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                        startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
                    }
                    else
                    {
                        startActivity(new Intent(MainActivity.NOTIFICATION_SETTINGS_PACKAGE));
                    }
                    return true;
                }
            });

            neverAsk.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    if (neverAsk.isChecked())
                    {
                        autoConnect.setEnabled(false);
                        autoConnect.setChecked(false);
                    }
                    else
                    {
                        autoConnect.setEnabled(true);
                    }
                    return true;
                }
            });
                    setSummaries();
        }

        DialogInterface.OnClickListener renameClickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which){
                    case DialogInterface.BUTTON_POSITIVE:
                        prefs.edit().putString(Preferences.PREFS_DEVICE_KEY, "").apply();
                        device.setSummary("No necklace saved");
                        device.setEnabled(false);
                        autoConnect.setEnabled(false);
                        autoConnect.setChecked(false);
                        break;

                    case DialogInterface.BUTTON_NEGATIVE:
                        break;
                }
            }
        };
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        notificationAccessEnabled = Settings.Secure.getString(this.getContentResolver(),"enabled_notification_listeners").contains(getApplicationContext().getPackageName());
        setSummaries();
        MainActivity.mInSettings = true;
    }

    @Override
    protected void onPause() {
        super.onPause();
        overridePendingTransition(R.anim.fadein, R.anim.fade_and_drop_out);
        MainActivity.mInSettings = false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}