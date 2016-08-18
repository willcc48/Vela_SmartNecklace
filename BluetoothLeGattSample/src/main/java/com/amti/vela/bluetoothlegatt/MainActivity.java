package com.amti.vela.bluetoothlegatt;

import android.Manifest;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.design.widget.TabLayout;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v4.app.NotificationCompat;
import android.support.v7.widget.Toolbar;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * For a given BLE device, this Activity provides the user interface to connect, display data,
 * and display GATT services and characteristics supported by the device.  The Activity
 * communicates with {@code BluetoothLeService}, which in turn interacts with the
 * Bluetooth LE API.
 */
public class MainActivity extends AppCompatActivity implements DialogInterface.OnCancelListener {
    private final static String TAG = MainActivity.class.getSimpleName();

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";
    public static final String ANALOG_OUT_UUID = "866ad1ee-05c4-4f4e-9ef4-548790668ad1";
    public static final String VBAT_UUID = "982754c4-fbde-4d57-a01b-6c81f2f0499e";
    public static final String NOTIFICATION_UUID = "982754c4-fbde-4d57-a01b-6c81f2f05353";

    public static final int VBAT_TIME_LENGTH = 5000;
    public static final int CONNECT_TIME_LENGTH = 15000;
    public static final int DEVICE_INFO_TIME_LENGTH = 5000;
    public static final int RSSI_TIME_LENGTH = 5000;

    public static final String NOTIFICATION_SETTINGS_PACKAGE = "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS";

    public static final int SEND_COLOR_VALUES = 0;
    public static final int SEND_DEP_VALUE = 1;

    private String mDeviceAddress;
    String mDeviceName;
    private BluetoothLeService mBluetoothLeService;
    private ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics = new ArrayList<ArrayList<BluetoothGattCharacteristic>>();
    private boolean mConnected = false;
    private BluetoothGattCharacteristic mNotifyCharacteristic;
    public BluetoothGattCharacteristic colorCharacteristic;
    public BluetoothGattCharacteristic vbatCharacteristic;

    ProgressDialog connectingDialog;

    private TabLayout tabLayout;
    private ViewPager viewPager;
    private ViewPagerAdapter viewPagerAdapter;
    LinearLayout linearLayout;
    DeviceFragment deviceFragment;
    ColorPickerFragment colorPickerFragment;
    boolean initColors = true;

    int[] mVbatArray = new int[8];

    int[] mRssiArray = new int[10];

    AlertDialog.Builder saveAutoConnectDialog;
    boolean mNeverAskChecked;

    boolean mAutoConnecting;
    boolean mNeverAsking;

    int notificationBuzzerCount = 0;

    boolean manualDisconnect = false;

    public static final int mRssiNotificationId = 1;
    NotificationManager mNotifyMgr;
    NotificationCompat.Builder notificationBuilder;
    boolean mPauseNotifications = true;
    public static boolean mInSettings = false;

    boolean mNotificationExists = false;

    FrameLayout fragmentLayout;

    RelativeLayout messageBar;
    List<String> permissionList;
    TextView messageBarText;
    Button messageBarButton;
    Animation slideDownIn;
    Animation slideUpOut;
    Animation fadeinAnim;
    Animation fadeoutAnim;

    public static String MESSAGEBAR_NOTIFICATION_ACCESS = "This app needs notification access";
    public static String MESSAGEBAR_PERMISSIONS = "This app need permissions";

    DialogInterface.OnClickListener saveAutoConnectListener = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            switch (which){
                case DialogInterface.BUTTON_POSITIVE:
                    prefs.edit().putBoolean(Preferences.PREFS_AUTO_CONNECT_KEY, true).apply();
                    prefs.edit().putString(Preferences.PREFS_DEVICE_NAME_KEY, mDeviceName).apply();
                    prefs.edit().putString(Preferences.PREFS_DEVICE_ADDRESS_KEY, mDeviceAddress).apply();
                    break;

                case DialogInterface.BUTTON_NEGATIVE:
                    prefs.edit().putBoolean(Preferences.PREFS_NEVER_ASK_KEY, mNeverAskChecked).apply();
                    break;
            }
            notificationListenerInit();
        }
    };

    Handler mHandler;
    public Handler getBtHandler()
    {
        return mHandler;
    }

    private BroadcastReceiver onNotice = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (mConnected)
            {
                mHandler.postDelayed(notificationRunnable, 0);
            }
        }
    };


    Runnable notificationRunnable = new Runnable() {

        @Override
        public void run() {
            if(mBluetoothLeService != null)
            {
                if(notificationBuzzerCount < 4)
                {
                    if(notificationBuzzerCount % 2 == 0)
                        mBluetoothLeService.writeCharacteristic(colorCharacteristic,"V" );
                    else
                        mBluetoothLeService.writeCharacteristic(colorCharacteristic,"v" );

                    notificationBuzzerCount++;
                    mHandler.postDelayed(notificationRunnable, 500);
                }
                else
                {
                    notificationBuzzerCount = 0;
                }
            /*
            mBluetoothLeService.writeCharacteristic(colorCharacteristic,"255000000" );
            Thread.sleep(500);
            mBluetoothLeService.writeCharacteristic(colorCharacteristic,"000000000");
            Thread.sleep(500);
            mBluetoothLeService.writeCharacteristic(colorCharacteristic,"255000000");
            Thread.sleep(500);
            mBluetoothLeService.writeCharacteristic(colorCharacteristic,"000000000" );
            Thread.sleep(500);
            */
            }
        }
    };


    Runnable readRssiRunnable = new Runnable() {

        @Override
        public void run() {
            mBluetoothLeService.readRemoteRssi();
            mHandler.postDelayed(this, RSSI_TIME_LENGTH);
        }
    };

    Runnable connectRunnable = new Runnable() {

        @Override
        public void run() {
            if(mBluetoothLeService.getServiceBound())
                unregisterReceiver(mGattUpdateReceiver);
            unbindService(mServiceConnection);
            stopService(new Intent(MainActivity.this, NotificationService.class));
            finish();
            Toast.makeText(getApplicationContext(), "Could not connect to your necklace", Toast.LENGTH_SHORT).show();
        }
    };

    Runnable getDeviceInfoRunnable = new Runnable() {

        @Override
        public void run() {
            if(mBluetoothLeService.getServiceBound())
                unregisterReceiver(mGattUpdateReceiver);
            unbindService(mServiceConnection);
            stopService(new Intent(MainActivity.this, NotificationService.class));
            finish();
            Toast.makeText(getApplicationContext(), "Could not get your necklace info", Toast.LENGTH_SHORT).show();
        }
    };

    Runnable readVbatRunnable = new Runnable() {

        @Override
        public void run() {
            mBluetoothLeService.readCharacteristic(vbatCharacteristic);
            mHandler.postDelayed(readVbatRunnable, VBAT_TIME_LENGTH);
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        overridePendingTransition(R.anim.slidein, R.anim.fadeout);

        final Intent intent = getIntent();
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);

        initGui();

        mHandler = new Handler() {
            @Override
            public void handleMessage(final Message msg) {
                // TODO Auto-generated method stub
                super.handleMessage(msg);
                switch (msg.what) {
                    case SEND_COLOR_VALUES:
                        mBluetoothLeService.writeCharacteristic(colorCharacteristic, colorPickerFragment.getColorString());
                        mBluetoothLeService.readCharacteristic(colorCharacteristic);
                        Log.v(TAG, "Wrote rgb values");
                        break;
                    case SEND_DEP_VALUE:
                        sendDEPValue((String)msg.obj);
                        Log.v(TAG, "Wrote" + (String)msg.obj + "value");
                        break;
                }
            }
        };

        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());

        //get bt le going
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
        LocalBroadcastManager.getInstance(this).registerReceiver(onNotice, new IntentFilter("Msg"));

        connectingDialog = new ProgressDialog(this);
        connectingDialog.setTitle("Connecting");
        connectingDialog.setMessage("Connecting to " + mDeviceName + "...");
        connectingDialog.setOnCancelListener(this);
        connectingDialog.setCanceledOnTouchOutside(false);
        connectingDialog.setButton(DialogInterface.BUTTON_NEGATIVE, "Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                stopService(new Intent(MainActivity.this, NotificationService.class));
                finish();
            }
        });

        connectingDialog.show();

        mNotifyMgr = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    }

    BluetoothGattCharacteristic fetchCharacteristic(String uuid)
    {
        if(mBluetoothLeService == null)
            return null;
        BluetoothGattCharacteristic my_characteristic = null;
        List<BluetoothGattService> gattServices = mBluetoothLeService.getSupportedGattServices();
        for (BluetoothGattService service : gattServices) {
            List<BluetoothGattCharacteristic> gattCharacteristics = service.getCharacteristics();
            for (BluetoothGattCharacteristic characteristic : gattCharacteristics) {
                if (characteristic.getUuid().toString().equals(uuid)) {
                    my_characteristic = characteristic;
                    break;
                }
            }
        }

        return my_characteristic;
    }

    void notificationListenerInit()
    {
        //start listening to notifications
        startService(new Intent(MainActivity.this, NotificationService.class));
        checkMessageBarState();
    }

    void initGui()
    {
        tabLayout = (TabLayout) findViewById(R.id.tabs);
        viewPager = (ViewPager) findViewById(R.id.viewpager);
        fragmentLayout = (FrameLayout)findViewById(R.id.fragment_layout);

        fadeinAnim = AnimationUtils.loadAnimation(this, R.anim.fadein_slow);
        fadeoutAnim = AnimationUtils.loadAnimation(getApplicationContext(), R.anim.fadeout_slow);

        fadeoutAnim.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {

            }

            @Override
            public void onAnimationEnd(Animation animation) {
                fragmentLayout.setVisibility(View.INVISIBLE);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });

        fragmentLayout.setVisibility(View.INVISIBLE);

        messageBarText = (TextView) findViewById(R.id.text_enable);
        messageBar = (RelativeLayout) findViewById(R.id.message_bar);
        messageBarButton = (Button) findViewById(R.id.button_enable);
        messageBar.setVisibility(View.GONE);

        slideDownIn = AnimationUtils.loadAnimation(this, R.anim.slide_down_in);
        slideUpOut = AnimationUtils.loadAnimation(this, R.anim.slide_up_out);

        messageBarButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(messageBarText.getText().toString().equals(MESSAGEBAR_NOTIFICATION_ACCESS))
                {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1)
                        startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
                    else
                        startActivity(new Intent(NOTIFICATION_SETTINGS_PACKAGE));
                }
                else if(messageBarText.getText().toString().equals(MESSAGEBAR_PERMISSIONS))
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        String[] permissionArray = new String[permissionList.size()];
                        permissionArray = permissionList.toArray(permissionArray);
                        requestPermissions(permissionArray, 1);
                    }

                checkMessageBarState();
            }
        });

        slideUpOut.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {

            }

            @Override
            public void onAnimationEnd(Animation animation) {
                messageBar.setVisibility(View.GONE);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });

        //action bar
        final Toolbar actionBarToolBar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(actionBarToolBar);
        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBarToolBar.setTitleTextColor(ContextCompat.getColor(this, R.color.colorActionBarWhite));

        //status bar color
        Window window = getWindow();
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.setStatusBarColor(ContextCompat.getColor(this, R.color.colorStatusBar));
        }

        linearLayout = new LinearLayout(getApplicationContext());

        //set up fragment_device and fragments
        viewPagerAdapter = new ViewPagerAdapter(getSupportFragmentManager());
        viewPager.setAdapter(viewPagerAdapter);

        final TabLayout.Tab color = tabLayout.newTab();
        final TabLayout.Tab device = tabLayout.newTab();
        final TabLayout.Tab dev = tabLayout.newTab();

        View colorView = getLayoutInflater().inflate(R.layout.tab_view, null);
        final ImageView iconColor = (ImageView) colorView.findViewById(R.id.imageView);
        iconColor.setImageResource(R.mipmap.ic_color_palette_white);

        View editView = getLayoutInflater().inflate(R.layout.tab_view, null);
        final ImageView iconEdit = (ImageView) editView.findViewById(R.id.imageView);
        iconEdit.setImageResource(R.mipmap.ic_necklace);

        View devView = getLayoutInflater().inflate(R.layout.tab_view, null);
        final ImageView iconDev = (ImageView) devView.findViewById(R.id.imageView);
        iconDev.setImageResource(R.mipmap.ic_developer_white);

        color.setCustomView(iconColor);
        device.setCustomView(iconEdit);
        dev.setCustomView(iconDev);

        tabLayout.addTab(device, 0);
        tabLayout.addTab(color, 1);
        tabLayout.addTab(dev, 2);

        iconDev.setImageAlpha(130);
        iconEdit.setImageAlpha(255);
        iconColor.setImageAlpha(130);

        tabLayout.setSelectedTabIndicatorColor(ContextCompat.getColor(this, R.color.colorTabIndicator));
        viewPager.addOnPageChangeListener(new TabLayout.TabLayoutOnPageChangeListener(tabLayout));

        viewPager.setCurrentItem(0);

        tabLayout.setOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                actionBarToolBar.setTitle(viewPagerAdapter.getPageTitle(tabLayout.getSelectedTabPosition()));
                viewPager.setCurrentItem(tab.getPosition());
                switch(viewPager.getCurrentItem())
                {
                    case 0:
                        iconDev.setImageAlpha(130);
                        iconEdit.setImageAlpha(255);
                        iconColor.setImageAlpha(130);
                        break;
                    case 1:
                        iconDev.setImageAlpha(130);
                        iconEdit.setImageAlpha(130);
                        iconColor.setImageAlpha(255);
                        break;
                    case 2:
                        iconDev.setImageAlpha(255);
                        iconEdit.setImageAlpha(130);
                        iconColor.setImageAlpha(130);
                        break;
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {

            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {

            }
        });

        colorPickerFragment = viewPagerAdapter.getColorPickerFragment();
        deviceFragment = viewPagerAdapter.getDeviceFragment();

        saveAutoConnectDialog = new AlertDialog.Builder(this);
        saveAutoConnectDialog.setMessage("Would you like to auto connect to " + mDeviceName + " from now on?")
                .setPositiveButton("Yes", saveAutoConnectListener).setNegativeButton("No", saveAutoConnectListener).setCancelable(false);
        View checkBoxView = View.inflate(this, R.layout.alert_dialog_checkbox, null);
        CheckBox checkBox = (CheckBox) checkBoxView.findViewById(R.id.checkbox);
        checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mNeverAskChecked = isChecked;
            }
        });
        checkBox.setText("Never ask again");
        saveAutoConnectDialog.setView(checkBoxView);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case 1:
                checkMessageBarState();
                break;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    void checkMessageBarState()
    {
        boolean showPermissionRequest = false;

        if(Build.VERSION.SDK_INT >= 23)
        {
            permissionList = new ArrayList<>();

            if (checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED)
            {
                permissionList.add(Manifest.permission.SEND_SMS);
                showPermissionRequest = true;
            }

            if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED)
            {
                permissionList.add(Manifest.permission.READ_CONTACTS);
                showPermissionRequest = true;
            }

            if(showPermissionRequest)
                showMessageBarIfNeeded(MESSAGEBAR_PERMISSIONS);
            else
                hideMessageBarIfNeeded(MESSAGEBAR_PERMISSIONS);
        }

        if(!showPermissionRequest)
        {
            boolean notificationAccessEnabled = Settings.Secure.getString(this.getContentResolver(),
                    "enabled_notification_listeners").contains(getApplicationContext().getPackageName());
            if(!notificationAccessEnabled)
                showMessageBarIfNeeded(MESSAGEBAR_NOTIFICATION_ACCESS);
            else
            {
                hideMessageBarIfNeeded(MESSAGEBAR_NOTIFICATION_ACCESS);

            }
        }
    }

    void showMessageBarIfNeeded(String text)
    {
        if(!messageBarText.getText().toString().equals(text))
            showMessageBar(text);
    }

    void showMessageBar(String text)
    {
        messageBarText.setText(text);
        messageBar.clearAnimation();
        messageBar.startAnimation(slideDownIn);
        messageBar.setVisibility(View.VISIBLE);

        if(fragmentLayout.getVisibility() == View.VISIBLE)
        {
            fragmentLayout.startAnimation(fadeoutAnim);
        }

    }

    void hideMessageBarIfNeeded(String text)
    {
        if(messageBar.getVisibility() == View.VISIBLE && messageBarText.getText().toString().equals(text))
            hideMessageBar();
        else if(messageBar.getVisibility() == View.GONE && fragmentLayout.getVisibility() == View.INVISIBLE && mConnected)
        {
            fragmentLayout.startAnimation(fadeinAnim);
            fragmentLayout.setVisibility(View.VISIBLE);
        }
    }

    void hideMessageBar()
    {
        messageBar.clearAnimation();
        messageBar.startAnimation(slideUpOut);

        fragmentLayout.startAnimation(fadeinAnim);
        fragmentLayout.setVisibility(View.VISIBLE);
    }

    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                stopService(new Intent(MainActivity.this, NotificationService.class));
                mHandler.removeCallbacksAndMessages(null);
                finish();
            }
            // Automatically connects to the device upon successful start-up initialization.
            mBluetoothLeService.connect(mDeviceAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };


    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    // ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
    //                        or notification operations.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {

                mConnected = true;
                mHandler.removeCallbacks(connectRunnable);
                mHandler.postDelayed(getDeviceInfoRunnable, DEVICE_INFO_TIME_LENGTH);
                connectingDialog.setMessage("Getting necklace information...");

                mHandler.postDelayed(readRssiRunnable, RSSI_TIME_LENGTH);

                deviceFragment.setDevice(mDeviceName);
            }
            else if(BluetoothLeService.ACTION_RSSI_DATA_AVAILABLE.equals(action))
            {
                int newRssi = intent.getIntExtra(BluetoothLeService.EXTRA_RSSI, 0);
                //shift elements in array to right
                for (int i = (mRssiArray.length - 1); i > 0; i--) {
                    mRssiArray[i] = mRssiArray[i-1];
                }
                mRssiArray[0] = newRssi;
                int avgRssi = 0;
                //calculate avg
                int divideBy = 0;
                for(int i = 0; i < mRssiArray.length - 1; i++)
                {
                    if(mRssiArray[i] != 0)
                    {
                        avgRssi += mRssiArray[i];
                        divideBy++;
                    }
                }

                avgRssi /= divideBy;

                int a;

                if (avgRssi <= -88)
                {
                    if(mPauseNotifications && !mInSettings && !mNotificationExists)
                    {
                        showNotification("Your necklace is almost out of range");
                        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                        if(mPauseNotifications && !mInSettings)
                            v.vibrate(500);
                    }
                }
                else
                {
                    mNotifyMgr.cancel(mRssiNotificationId);
                    mNotificationExists = false;
                }

            }
            else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                if(!connectingDialog.isShowing())
                {
                    if(!manualDisconnect) {
                        Toast.makeText(getApplicationContext(), "You have lost connection to the device", Toast.LENGTH_SHORT).show();
                        connectingDialog.setTitle("Connecting");
                        connectingDialog.setMessage("Reconnecting to " + mDeviceName + "...");
                        connectingDialog.setCanceledOnTouchOutside(false);
                        connectingDialog.setButton(DialogInterface.BUTTON_NEGATIVE, "Cancel", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                                stopService(new Intent(MainActivity.this, NotificationService.class));
                                finish();
                            }
                        });
                        connectingDialog.show();
                        mBluetoothLeService.connect(mDeviceAddress);
                        mHandler.postDelayed(connectRunnable, CONNECT_TIME_LENGTH);
                    }
                }

                manualDisconnect = false;
            }
            else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                //read initial rgb color
                colorCharacteristic = fetchCharacteristic(ANALOG_OUT_UUID);
                mBluetoothLeService.readCharacteristic(colorCharacteristic);

                //kick off reading battery
                vbatCharacteristic = fetchCharacteristic(VBAT_UUID);
                mHandler.postDelayed(readVbatRunnable, VBAT_TIME_LENGTH);

                //subscribe to notification characteristic
                BluetoothGattCharacteristic characteristic = fetchCharacteristic(NOTIFICATION_UUID);
                if (mGattCharacteristics != null) {
                    final int charaProp = characteristic.getProperties();
                    if ((charaProp | BluetoothGattCharacteristic.PROPERTY_READ) > 0) {
                        // If there is an active notification on a characteristic, clear
                        // it first so it doesn't update the data field on the user interface.
                        if (mNotifyCharacteristic != null) {
                            mBluetoothLeService.setCharacteristicNotification(
                                    mNotifyCharacteristic, false);
                            mNotifyCharacteristic = null;
                        }
                        mBluetoothLeService.readCharacteristic(characteristic);
                    }
                    if ((charaProp | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                        mNotifyCharacteristic = characteristic;
                        mBluetoothLeService.setCharacteristicNotification(characteristic, true);
                    }
                }
            }

            else if(BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action))
            {
                String uuid = intent.getStringExtra(BluetoothLeService.EXTRA_UUID);
                String rxString = intent.getStringExtra(BluetoothLeService.EXTRA_DATA).split("\n")[0];
                if(rxString == null || uuid == null)
                    return;
                switch(uuid)
                {
                    case ANALOG_OUT_UUID:
                        if(initColors)
                            processColorDataAndInitDialog(rxString);
                        break;
                    case VBAT_UUID:
                        processVbatData(rxString);
                        break;
                    case NOTIFICATION_UUID:
                        sendMessage();
                        break;
                }
            }
        }
    };

    void showNotification(String message)
    {
        if(notificationBuilder == null)
        {
            Intent intent = new Intent(this, DeviceScanActivity.class);
            // use System.currentTimeMillis() to have a unique ID for the pending intent
            PendingIntent pIntent = PendingIntent.getActivity(this, (int) System.currentTimeMillis(), intent, 0);

            Bitmap bm = BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher);
            notificationBuilder = new NotificationCompat.Builder(getApplicationContext())
                    .setSmallIcon(R.drawable.ic_launcher_small)
                    .setContentTitle("Vela SmartNecklace")
                    .setContentText(message)
                    .setContentIntent(pIntent)
                    .setLargeIcon(bm);
        }
        else
        {
            notificationBuilder.setContentText(message);
        }

        // Builds the notification and issues it.
        mNotifyMgr.notify(mRssiNotificationId, notificationBuilder.build());
        mNotificationExists = true;
    }

    void sendMessage()
    {
        if(Build.VERSION.SDK_INT >= 23)
        {
            if (checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED)
                requestPermissions(new String[]{Manifest.permission.SEND_SMS}, 1);
            else
            {
                String msg = deviceFragment.getMsgText();
                String number = deviceFragment.getContactNumber();

                if(!msg.trim().equals(""))
                {
                    SmsManager smsManager = SmsManager.getDefault();
                    smsManager.sendTextMessage(number, null, msg, null, null);
                    Log.d(TAG, "Sent SMS message to " + number + " " + msg);
                }
            }
        }
        else
        {
            String msg = deviceFragment.getMsgText();
            String number = deviceFragment.getContactNumber();

            if(!msg.trim().equals(""))
            {
                SmsManager smsManager = SmsManager.getDefault();
                smsManager.sendTextMessage(number, null, msg, null, null);
                Log.d(TAG, "Sent SMS message to " + number + " " + msg);
            }
        }
    }

    void processColorDataAndInitDialog(String rxString)
    {
        String colorString = rxString;
        try {
            int r = Integer.parseInt(colorString.substring(0, 3));
            int g = Integer.parseInt(colorString.substring(3, 6));
            int b = Integer.parseInt(colorString.substring(6, 9));
            int colorValue = b;
            colorValue += (g << 8);
            colorValue += (r << 16);
            colorPickerFragment.initColors(colorValue);

            connectingDialog.dismiss();
            mHandler.removeCallbacks(getDeviceInfoRunnable);
            checkMessageBarState();
            initColors = false;

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            mNeverAsking = prefs.getBoolean(Preferences.PREFS_NEVER_ASK_KEY, false);
            mAutoConnecting = prefs.getBoolean(Preferences.PREFS_AUTO_CONNECT_KEY, false);
            if(!mNeverAsking && !mAutoConnecting)
                saveAutoConnectDialog.show();
            else
                notificationListenerInit();
            //read first vbat value here so we don't need to wait 10 seconds
            mBluetoothLeService.readCharacteristic(vbatCharacteristic);
        } catch (NumberFormatException e) {
            Log.d(TAG, "Caught NumberFormatException while parsing rgb values");
            mBluetoothLeService.writeCharacteristic(colorCharacteristic, "000000000");
            mBluetoothLeService.readCharacteristic(colorCharacteristic);
        }
    }

    void processVbatData(String rxString)
    {
        String vbatString = rxString;
        //shift elements in array to right
        for (int i = (mVbatArray.length - 1); i > 0; i--) {
            mVbatArray[i] = mVbatArray[i-1];
        }
        //parse vbat
        char vbatChar = vbatString.charAt(0);
        mVbatArray[0] = (int)vbatChar;
        int mAvgVbat = 0;
        //calculate avg
        int divideBy = 0;
        for(int i = 0; i < mVbatArray.length - 1; i++)
        {
            if(!(mVbatArray[0] > mVbatArray[i] + 5) && !(mVbatArray[0] < mVbatArray[i]))
            {
                mAvgVbat += mVbatArray[i];
                divideBy++;
            }
        }
        mAvgVbat /= divideBy;

        deviceFragment.updateBattery(mAvgVbat);
    }

    void sendDEPValue(String val)
    {
        mBluetoothLeService.writeCharacteristic(colorCharacteristic, val);
    }

    @Override
    public void onBackPressed() {
        disconnectFromDevice();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mBluetoothLeService != null && !mConnected) {
            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
            Log.d(TAG, "Connect request result=" + result);
        }

        if(mInSettings)
            mInSettings = false;

        if(connectingDialog.isShowing() && mConnected)
            mHandler.postDelayed(getDeviceInfoRunnable, DEVICE_INFO_TIME_LENGTH);
        else if(connectingDialog.isShowing() && !mConnected)
            mHandler.postDelayed(connectRunnable, CONNECT_TIME_LENGTH);

        mPauseNotifications = false;
        mNotifyMgr.cancel(MainActivity.mRssiNotificationId);

        checkMessageBarState();
    }

    @Override
    protected void onPause() {
        super.onPause();
        overridePendingTransition(R.anim.fadein, R.anim.fade_and_drop_out);
        mPauseNotifications = true;
        mNotificationExists = false;

        if(connectingDialog.isShowing() && mConnected)
            mHandler.removeCallbacks(getDeviceInfoRunnable);
        else if(connectingDialog.isShowing() && !mConnected)
            mHandler.removeCallbacks(connectRunnable);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopService(new Intent(MainActivity.this, NotificationService.class));
        try
        {
            unregisterReceiver(mGattUpdateReceiver);
            unbindService(mServiceConnection);
        } catch (IllegalArgumentException e) { }
        mBluetoothLeService = null;
        mHandler.removeCallbacksAndMessages(null);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_activity_main, menu);
        return true;
    }


    DialogInterface.OnClickListener disconnectClickListener = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            switch (which){
                case DialogInterface.BUTTON_POSITIVE:
                    stopService(new Intent(MainActivity.this, NotificationService.class));
                    finish();
                    mBluetoothLeService.disconnect();
                    mHandler.removeCallbacksAndMessages(null);
                    break;

                case DialogInterface.BUTTON_NEGATIVE:
                    break;
            }
        }
    };


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.menu_settings:
                Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
                startActivity(intent);
                mInSettings = true;
                return true;
            case android.R.id.home:
                if(mConnected)
                {
                    disconnectFromDevice();
                }
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    void disconnectFromDevice()
    {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("Are you sure you want to disconnect from the device?").setPositiveButton("Yes", disconnectClickListener).setNegativeButton("Cancel", disconnectClickListener).show();
        manualDisconnect = true;
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        intentFilter.addAction(BluetoothLeService.ACTION_RSSI_DATA_AVAILABLE);
        return intentFilter;
    }

    //called when cancelling the reconnecting dialog
    @Override
    public void onCancel(DialogInterface dialog) {
        stopService(new Intent(MainActivity.this, NotificationService.class));
        finish();
    }

}