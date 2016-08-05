/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.amti.vela.bluetoothlegatt;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.NotificationManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.AnimationUtils;
import android.view.animation.LayoutAnimationController;
import android.view.animation.TranslateAnimation;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;


import java.util.ArrayList;
import java.util.List;

/**
 * Activity for scanning and displaying available Bluetooth LE devices.
 */
public class DeviceScanActivity extends AppCompatActivity {
    private final static String TAG = DeviceScanActivity.class.getSimpleName();

    private static final int REQUEST_ENABLE_BT = 1;
    private static final long SCAN_PERIOD = 10000; // Stops scanning after 10 seconds.

    private BluetoothAdapter mBluetoothAdapter;
    private boolean mScanning;
    private Handler leScanHandler;

    private List<BluetoothDevice> mLeDevicesList;
    CustomListAdapter devicesAdapter;
    ListView deviceListView;
    SwipeRefreshLayout swipeContainer;
    TextView hintText;
    RelativeLayout messageBar;
    TextView messageBarText;
    Button messageBarButton;

    Animation fadeinAnim;
    Animation fadeoutAnim;
    Animation slideDownIn;
    Animation slideUpOut;

    boolean btReceiverRegistered = false;

    private final BroadcastReceiver mBtReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                switch (state) {
                    case BluetoothAdapter.STATE_OFF:
                        break;
                    case BluetoothAdapter.STATE_TURNING_OFF:
                        showMessageBarIfNeeded();
                        break;
                    case BluetoothAdapter.STATE_ON:
                        hideMessageBarIfNeeded();
                        autoConnectIfEnabled();
                        scanLeDevice(true);
                        break;
                    case BluetoothAdapter.STATE_TURNING_ON:
                        break;
                }
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_search);

        // Whenever we start the app, we cancel any rssi notification waiting in the status bar
        NotificationManager mNotifyMgr = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        mNotifyMgr.cancel(MainActivity.mRssiNotificationId);

        initGui();

        if(btInit())
            autoConnectIfEnabled();

        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(mBtReceiver, filter);
        btReceiverRegistered = true;

        //scanLeDevice(true);
    }


    void autoConnectIfEnabled()
    {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean isAutoConnect = prefs.getBoolean(Preferences.PREFS_AUTO_CONNECT_KEY, false);
        String nameString = prefs.getString(Preferences.PREFS_DEVICE_NAME_KEY, "");
        String addrString = prefs.getString(Preferences.PREFS_DEVICE_ADDRESS_KEY, "");
        if(isAutoConnect && !nameString.equals("") && mBluetoothAdapter.isEnabled())
        {
            final Intent intent = new Intent(DeviceScanActivity.this, MainActivity.class);
            intent.putExtra(MainActivity.EXTRAS_DEVICE_NAME, nameString);
            intent.putExtra(MainActivity.EXTRAS_DEVICE_ADDRESS, addrString);
            if (mScanning) {
                scanLeDevice(false);
                mScanning = false;
            }
            startActivity(intent);
        }
    }


    boolean isBtCompat()
    {
        // Use this check to determine whether BLE is supported on the device.  Then you can
        // selectively disable BLE-related features.
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "This device does not support bluetooth", Toast.LENGTH_SHORT).show();
            finish();
            return false;
        }

        // Checks if Bluetooth is supported on the device.
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "This device does not support bluetooth", Toast.LENGTH_SHORT).show();
            finish();
            return false;
        }

        return true;
    }

    void initGui()
    {
        ActivityManager.TaskDescription taskDescription = null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            Bitmap bm = BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher);
            taskDescription = new ActivityManager.TaskDescription(getString(R.string.app_name), bm, ContextCompat.getColor(this, R.color.colorPrimary));
            setTaskDescription(taskDescription);
        }

        leScanHandler = new Handler();

        // Initialize widgets
        deviceListView = (ListView)findViewById(R.id.listView);
        swipeContainer = (SwipeRefreshLayout) findViewById(R.id.swipeContainer);
        hintText = (TextView) findViewById(R.id.hint_text);
        messageBarText = (TextView) findViewById(R.id.text_enable);
        messageBar = (RelativeLayout) findViewById(R.id.message_bar);
        messageBarButton = (Button) findViewById(R.id.button_enable);

        messageBar.setVisibility(View.GONE);
        hintText.setVisibility(View.GONE);

        fadeinAnim = AnimationUtils.loadAnimation(this, R.anim.fadein);
        fadeoutAnim = AnimationUtils.loadAnimation(getApplicationContext(), R.anim.fadeout);
        slideDownIn = AnimationUtils.loadAnimation(this, R.anim.slide_down_in);
        slideUpOut = AnimationUtils.loadAnimation(this, R.anim.slide_up_out);

        slideUpOut.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {

            }

            @Override
            public void onAnimationEnd(Animation animation) {
                deviceListView.setVisibility(View.VISIBLE);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });

        slideDownIn.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {

            }

            @Override
            public void onAnimationEnd(Animation animation) {
                deviceListView.setVisibility(View.INVISIBLE);
                stopScan();
                if(hintText.getVisibility() == View.VISIBLE)
                    hintText.startAnimation(fadeoutAnim);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });

        fadeoutAnim.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {

            }

            @Override
            public void onAnimationEnd(Animation animation) {
                hintText.setVisibility(View.GONE);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });

        RelativeLayout.LayoutParams relativeParams = new RelativeLayout.LayoutParams
                (Toolbar.LayoutParams.WRAP_CONTENT, Toolbar.LayoutParams.WRAP_CONTENT);
        relativeParams.addRule(RelativeLayout.CENTER_IN_PARENT);
        hintText.setLayoutParams(relativeParams);

        // Setup device list and ListView adapter
        mLeDevicesList = new ArrayList<BluetoothDevice>();
        devicesAdapter = new CustomListAdapter(this, R.layout.custom_listview_item, new ArrayList<BluetoothDevice>());
        deviceListView.setAdapter(devicesAdapter);

        // Configure the swipe to refresh refreshing colors
        swipeContainer.setColorSchemeResources(R.color.colorPrimary,
                R.color.colorSwipeRefreshGreen,
                android.R.color.holo_orange_light,
                android.R.color.holo_red_light);

        // Action bar
        Toolbar actionBarToolBar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(actionBarToolBar);
        actionBarToolBar.setTitle("Choose a Device");
        actionBarToolBar.setTitleTextColor(ContextCompat.getColor(this, R.color.colorActionBarWhite));

        // Set status bar color
        Window window = getWindow();
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.setStatusBarColor(ContextCompat.getColor(this, R.color.colorStatusBar));
        }

        // Add item click callback event for our device ListView
        deviceListView.setOnItemClickListener(new AdapterView.OnItemClickListener()
        {
            @Override
            public void onItemClick(AdapterView<?> adapter, View v, int position, long arg3)
            {
                if(isBtEnabled())
                {
                    // Get the device name and address from the ListView item
                    BluetoothDevice device = devicesAdapter.getItem(position);
                    // Pack it away in the intent
                    final Intent intent = new Intent(DeviceScanActivity.this, MainActivity.class);
                    String deviceName = device.getName() == null ? "Unknown Device" : device.getName();
                    String deviceAddress = device.getAddress();
                    intent.putExtra(MainActivity.EXTRAS_DEVICE_NAME,  deviceName);
                    intent.putExtra(MainActivity.EXTRAS_DEVICE_ADDRESS,  deviceAddress);

                    // Move on the the main activity
                    startActivity(intent);
                }
            }
        });

        // This is called when the user swipes down to refresh the btle device list
        swipeContainer.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                // Clear list and start scanning
                clearDevices();
                scanLeDevice(true);
            }
        });

        messageBarButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                //startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);

                mBluetoothAdapter.enable();
            }
        });
    }


    boolean btInit()
    {
        // Initializes a Bluetooth adapter
        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        // Runtime permissions for bt needed in marshmallow
        if(Build.VERSION.SDK_INT >= 23)
        {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            {
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 1);
                return false;
            }
        }
        return true;
    }

    void setSwipeContainerRefresh(final boolean refresh)
    {
        swipeContainer.post(new Runnable() {
            @Override
            public void run() {
                swipeContainer.setRefreshing(refresh);
            }
        });
    }

    void clearDevices()
    {
        devicesAdapter.clear();
        mLeDevicesList.clear();
        devicesAdapter.notifyDataSetChanged();
    }

    Runnable scanRunnable = new Runnable() {
        // This event is called after SCAN_PERIOD (in ms)
        @Override
        public void run() {
            stopScan();
            if(hintText.getVisibility() == View.GONE)
            {
                repositionHintText();
                hintText.startAnimation(fadeinAnim);
                hintText.setVisibility(View.VISIBLE);
            }
        }
    };

    // This is our main device scan function
    private void scanLeDevice(final boolean enable) {
        // Bluetooth is on
        if(!askEnableBtIfNeeded())
        {
            //start scanning
            if (enable)
            {
                startScan();

                if (hintText.getVisibility() == View.VISIBLE)
                    hintText.startAnimation(fadeoutAnim);
            }
            //stop scanning
            else
            {
                stopScan();
                if(hintText.getVisibility() == View.GONE)
                {
                    repositionHintText();
                    hintText.startAnimation(fadeinAnim);
                    hintText.setVisibility(View.VISIBLE);
                }
            }
        }
        // Bluetooth is off
        else {
            stopScan();

            if (hintText.getVisibility() == View.VISIBLE)
                hintText.startAnimation(fadeoutAnim);
        }
    }

    void repositionHintText()
    {
        RelativeLayout.LayoutParams relativeParams = new RelativeLayout.LayoutParams
                (Toolbar.LayoutParams.WRAP_CONTENT, Toolbar.LayoutParams.WRAP_CONTENT);
        if(mLeDevicesList.size() > 0)
        {
            relativeParams.addRule(RelativeLayout.CENTER_HORIZONTAL);

            int extraMargin = (int) getResources().getDimension(R.dimen.extraMargin);
            int listViewHeight = getListViewHeight(deviceListView);

            hintText.setY(listViewHeight + extraMargin);
            hintText.setLayoutParams(relativeParams);
        }
        else
        {
            relativeParams.addRule(RelativeLayout.CENTER_HORIZONTAL);
            relativeParams.addRule(RelativeLayout.CENTER_VERTICAL);
            hintText.setLayoutParams(relativeParams);
        }
    }

    private int getListViewHeight(ListView list) {
        ListAdapter adapter = deviceListView.getAdapter();
        int listviewHeight = 0;
        list.measure(View.MeasureSpec.makeMeasureSpec(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        listviewHeight = list.getMeasuredHeight() * adapter.getCount() + (adapter.getCount() * list.getDividerHeight());
        return listviewHeight;
    }

    /***************
     * These functions are to be used inside of the scanLeDevice method only
     */
    void startScan()
    {
        if(mBluetoothAdapter != null && !mScanning)
        {
            mScanning = true;
            setSwipeContainerRefresh(true);
            mBluetoothAdapter.startLeScan(mLeScanCallback);
            Log.e(TAG, "Started scan");

            // this handler stops the scan after a period of time.
            leScanHandler.removeCallbacks(null);
            leScanHandler.postDelayed(scanRunnable, SCAN_PERIOD);
        }
    }

    void stopScan()
    {
        if(mScanning)
        {
            mScanning = false;
            setSwipeContainerRefresh(false);
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
            Log.e(TAG, "Stopped scan");
        }
    }

    // Device scan callback.
    private BluetoothAdapter.LeScanCallback mLeScanCallback =
        new BluetoothAdapter.LeScanCallback() {

            @Override
            public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // Do not display the device if it's already displayed.
                        // The bt adapter will find the same device multiple times in one scan
                        boolean exists = false;
                        for (BluetoothDevice btDevice : mLeDevicesList)
                        {
                            if(device.getAddress().equals(btDevice.getAddress()))
                            {
                                exists = true;
                                break;
                            }
                        }
                        if(!exists)
                        {
                            // Let's add it to our global device list variable. We also add it to
                            // our devicesAdapter, which in turn is used for the contents of the ListView.
                            if(device.getAddress().equals(""))
                                return;
                            mLeDevicesList.add(device);
                            devicesAdapter.add(device);
                            devicesAdapter.notifyDataSetChanged();
                        }
                    }
                });
            }
        };


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_device_search, menu);
        if(!mScanning)
        {
            swipeContainer.setRefreshing(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.menu_settings:
                Intent intent = new Intent(DeviceScanActivity.this, SettingsActivity.class);
                startActivity(intent);
                return true;
        }
        return true;
    }

    @Override
    protected void onPause() {
        super.onPause();
        scanLeDevice(false);
        unregisterReceiver(mBtReceiver);
        leScanHandler.removeCallbacks(scanRunnable);
        btReceiverRegistered = false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        clearDevices();

        scanLeDevice(true);

        if(!btReceiverRegistered)
        {
            IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
            registerReceiver(mBtReceiver, filter);
        }

        if(!mScanning && hintText.getVisibility() == View.GONE)
            hintText.setVisibility(View.VISIBLE);
        if(!mScanning && deviceListView.getVisibility() == View.INVISIBLE)
            deviceListView.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // User chose not to enable Bluetooth.
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) { }
        else if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_OK) {
            autoConnectIfEnabled();
            scanLeDevice(true);
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        scanLeDevice(false);
        if(btReceiverRegistered)
            unregisterReceiver(mBtReceiver);
    }

    boolean isBtEnabled()
    {
        return mBluetoothAdapter.isEnabled();
    }

    boolean askEnableBtIfNeeded()
    {
        // Ensures Bluetooth is enabled on the device.  If Bluetooth is not currently enabled,
        // fire an intent to display a dialog asking the user to grant permission to enable it.
        if (!isBtEnabled()) {
            //have to request location permissions for marshmallow to do bluetooth
            if(Build.VERSION.SDK_INT >= 23)
            {
                if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                    requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 1);
                else
                    showMessageBarIfNeeded();
            }
            else
                showMessageBarIfNeeded();

            return true;
        }



        return false;
    }

    void showMessageBarIfNeeded()
    {
        if(messageBar.getVisibility() != View.VISIBLE)
        {
            if(isBtCompat())
                showMessageBar("Bluetooth is not enabled", true);
            else
                showMessageBar("Bluetooth is not supported on this device", false);
        }
    }

    void showMessageBar(String text, boolean enableButtonVisible)
    {
        // Other stuff like showing the hint text and setting listview visibility are done in the
        // onAnimationEnd messageBar

        messageBarText.setText(text);
        messageBar.startAnimation(slideDownIn);
        messageBar.setVisibility(View.VISIBLE);

        if(enableButtonVisible)
            messageBarButton.setVisibility(View.VISIBLE);
        else
            messageBarButton.setVisibility(View.INVISIBLE);
    }

    void hideMessageBarIfNeeded()
    {
        if(messageBar.getVisibility() == View.VISIBLE)
            hideMessageBar();
    }

    void hideMessageBar()
    {
        // Other stuff like showing the hint text and setting listview visibility are done in the
        // onAnimationEnd messageBar

        messageBar.startAnimation(slideUpOut);
        messageBar.setVisibility(View.GONE);
    }

}