package com.amti.vela.bluetoothlegatt;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.support.v4.content.LocalBroadcastManager;

public class NotificationService extends NotificationListenerService {

    Context context;

    @Override
    public void onCreate() {
        super.onCreate();
        context = getApplicationContext();

        ContentResolver contentResolver = context.getContentResolver();
        String enabledNotificationListeners = Settings.Secure.getString(contentResolver, "enabled_notification_listeners");
        String packageName = context.getPackageName();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        Intent msgrcv = new Intent("Msg");
        if(!sbn.getPackageName().equals("com.amti.vela"))
            LocalBroadcastManager.getInstance(context).sendBroadcast(msgrcv);
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {

    }

    @Override
    public IBinder onBind(Intent intent) {
        IBinder mIBinder = super.onBind(intent);
        return mIBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        boolean mOnUnbind = super.onUnbind(intent);
        return mOnUnbind;
    }
}