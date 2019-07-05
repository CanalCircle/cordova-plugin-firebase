package org.apache.cordova.firebase;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import androidx.core.app.NotificationCompat;
import android.util.Log;
import android.app.Notification;
import android.text.TextUtils;
import android.content.ContentResolver;
import android.graphics.Color;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;
import java.util.Random;

public class FirebasePluginMessagingService extends FirebaseMessagingService {

    private static final String TAG = "FirebasePlugin";

    static final String defaultSmallIconName = "notification_icon";
    static final String defaultLargeIconName = "notification_icon_large";


    /**
     * Called if InstanceID token is updated. This may occur if the security of
     * the previous token had been compromised. Note that this is called when the InstanceID token
     * is initially generated so this is where you would retrieve the token.
     */
    @Override
    public void onNewToken(String refreshedToken) {
        super.onNewToken(refreshedToken);
        Log.d(TAG, "Refreshed token: " + refreshedToken);
        FirebasePlugin.sendToken(refreshedToken);
    }


    /**
     * Called when message is received.
     * Called IF message is a data message (i.e. NOT sent from Firebase console)
     * OR if message is a notification message (e.g. sent from Firebase console) AND app is in foreground.
     * Notification messages received while app is in background will not be processed by this method;
     * they are handled internally by the OS.
     *
     * @param remoteMessage Object representing the message received from Firebase Cloud Messaging.
     */
    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        // [START_EXCLUDE]
        // There are two types of messages data messages and notification messages. Data messages are handled
        // here in onMessageReceived whether the app is in the foreground or background. Data messages are the type
        // traditionally used with GCM. Notification messages are only received here in onMessageReceived when the app
        // is in the foreground. When the app is in the background an automatically generated notification is displayed.
        // When the user taps on the notification they are returned to the app. Messages containing both notification
        // and data payloads are treated as notification messages. The Firebase console always sends notification
        // messages. For more see: https://firebase.google.com/docs/cloud-messaging/concept-options
        // [END_EXCLUDE]

        // Pass the message to the receiver manager so any registered receivers can decide to handle it
        boolean wasHandled = FirebasePluginMessageReceiverManager.onMessageReceived(remoteMessage);
        if (wasHandled) {
            Log.d(TAG, "Message was handled by a registered receiver");

            // Don't process the message in this method.
            return;
        }

        // TODO(developer): Handle FCM messages here.
        // Not getting messages here? See why this may be: https://goo.gl/39bRNJ
        String title = null;
        String body = null;
        String id = null;
        String sound = null;
        String vibrate = null;
        String light = null;
        String color = null;
        String icon = null;
        String channelId = null;
        String visibility = null;
        String priority = null;
        boolean foregroundNotification = false;
        Map<String, String> data = remoteMessage.getData();

        if (remoteMessage.getNotification() != null) {
            Log.i(TAG, "Received message: notification");
            id = remoteMessage.getMessageId();
            RemoteMessage.Notification notification = remoteMessage.getNotification();
            title = notification.getTitle();
            body = notification.getBody();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                channelId = notification.getChannelId();
            }
            sound = notification.getSound();
            color = notification.getColor();
            icon = notification.getIcon();
        } else if (data != null) {
            Log.i(TAG, "Received message: data");
            title = data.get("title");
            body = data.get("body");
            channelId = data.get("channel_id");
            id = data.get("id");
            sound = data.get("sound");
            vibrate = data.get("vibrate");
            light = data.get("light"); //String containing hex ARGB color, miliseconds on, miliseconds off, example: '#FFFF00FF,1000,3000'
            color = data.get("color");
            icon = data.get("icon");
            
            visibility = data.get("visibility");
            priority = data.get("priority");

            if (TextUtils.isEmpty(body)) {
                body = data.get("body");
            }
        }

        if(data.containsKey("foregroundNotification")){
            foregroundNotification = true;
        }

        if (TextUtils.isEmpty(id)) {
            Random rand = new Random();
            int n = rand.nextInt(50) + 1;
            id = Integer.toString(n);
        }

        Log.d(TAG, "From: " + remoteMessage.getFrom());
        Log.d(TAG, "Id: " + id);
        Log.d(TAG, "Title: " + title);
        Log.d(TAG, "Body: " + body);
        Log.d(TAG, "Sound: " + sound);
        Log.d(TAG, "Vibrate: " + vibrate);
        Log.d(TAG, "Light: " + light);
        Log.d(TAG, "Color: " + color);
        Log.d(TAG, "Icon: " + icon);
        Log.d(TAG, "Channel Id: " + channelId);
        Log.d(TAG, "Visibility: " + visibility);
        Log.d(TAG, "Priority: " + priority);
        

        if (!TextUtils.isEmpty(body) || !TextUtils.isEmpty(title) || (data != null && !data.isEmpty())) {
            boolean showNotification = (FirebasePlugin.inBackground() || !FirebasePlugin.hasNotificationsCallback() || foregroundNotification) && (!TextUtils.isEmpty(body) || !TextUtils.isEmpty(title));
            sendNotification(data, id, title, body, showNotification, sound, vibrate, light, color, icon, channelId, priority, visibility);
        }
    }

    private void sendNotification(Map<String, String> data, String id, String title, String body, boolean showNotification, String sound, String vibrate, String light, String color, String icon, String channelId, String priority, String visibility) {
        Log.d(TAG, "sendNotification(): showNotification="+showNotification+"; id="+id+"; title="+title+"; body="+body+"; sound="+sound+"; vibrate="+vibrate+"; light="+light+"; color="+color+"; icon="+icon+"; channel="+channelId+"; data="+data.toString());
        Bundle bundle = new Bundle();
        for (String key : data.keySet()) {
            bundle.putString(key, data.get(key));
        }

        if (showNotification) {
            Intent intent = new Intent(this, OnNotificationOpenReceiver.class);
            intent.putExtras(bundle);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(this, id.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT);

            // Channel
            if(channelId == null || FirebasePlugin.channelExists(channelId)){
                channelId = FirebasePlugin.defaultChannelId;
            }
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
                Log.d(TAG, "Channel ID: "+channelId);
            }

            NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, channelId);
            notificationBuilder
                    .setContentTitle(title)
                    .setContentText(body)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent);

            // On Android O+ the sound/lights/vibration are determined by the channel ID
            if(Build.VERSION.SDK_INT < Build.VERSION_CODES.O){
                // Sound
                if (sound.equals("default")) {
                    notificationBuilder.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));
                    Log.d(TAG, "Sound: default");
                }else if (sound != null) {
                    Uri soundPath = Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + getPackageName() + "/raw/" + sound);
                    Log.d(TAG, "Sound: custom=" + sound+"; path="+soundPath.toString());
                    notificationBuilder.setSound(soundPath);
                }else{
                    Log.d(TAG, "Sound: none");
                }

                // Light
                if (light != null) {
                    try {
                        String[] lightsComponents = color.replaceAll("\\s", "").split(",");
                        if (lightsComponents.length == 3) {
                            int lightArgb = Color.parseColor(lightsComponents[0]);
                            int lightOnMs = Integer.parseInt(lightsComponents[1]);
                            int lightOffMs = Integer.parseInt(lightsComponents[2]);
                            notificationBuilder.setLights(lightArgb, lightOnMs, lightOffMs);
                            Log.d(TAG, "Lights: color="+lightsComponents[0]+"; on(ms)="+lightsComponents[2]+"; off(ms)="+lightsComponents[3]);
                        }

                    } catch (Exception e) {}
                }

                // Vibrate
                if (vibrate != null){
                    try {
                        String[] sVibrations = vibrate.replaceAll("\\s", "").split(",");
                        long[] lVibrations = new long[sVibrations.length];
                        int i=0;
                        for(String sVibration: sVibrations){
                            lVibrations[i] = Integer.parseInt(sVibration.trim());
                            i++;
                        }
                        notificationBuilder.setVibrate(lVibrations);
                        Log.d(TAG, "Vibrate: "+vibrate);
                    } catch (Exception e) {
                        Log.e(TAG, e.getMessage());
                    }
                }
            }


            // Icon
            int defaultSmallIconResID = getResources().getIdentifier(defaultSmallIconName, "drawable", getPackageName());
            int customSmallIconResID = 0;
            if(icon != null){
                customSmallIconResID = getResources().getIdentifier(icon, "drawable", getPackageName());
            }

            if (customSmallIconResID != 0) {
                notificationBuilder.setSmallIcon(customSmallIconResID);
                Log.d(TAG, "Small icon: custom="+icon);
            }else if (defaultSmallIconResID != 0) {
                Log.d(TAG, "Small icon: default="+defaultSmallIconName);
                notificationBuilder.setSmallIcon(defaultSmallIconResID);
            } else {
                Log.d(TAG, "Small icon: application");
                notificationBuilder.setSmallIcon(getApplicationInfo().icon);
            }

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                int defaultLargeIconResID = getResources().getIdentifier(defaultLargeIconName, "drawable", getPackageName());
                int customLargeIconResID = 0;
                if(icon != null){
                    customLargeIconResID = getResources().getIdentifier(icon+"_large", "drawable", getPackageName());
                }

                int largeIconResID;
                if (customLargeIconResID != 0) {
                    largeIconResID = customLargeIconResID;
                    Log.d(TAG, "Large icon: custom="+icon);
                }else if (defaultLargeIconResID != 0) {
                    Log.d(TAG, "Large icon: default="+defaultLargeIconName);
                    largeIconResID = defaultLargeIconResID;
                } else {
                    Log.d(TAG, "Large icon: application");
                    largeIconResID = getApplicationInfo().icon;
                }
                notificationBuilder.setLargeIcon(BitmapFactory.decodeResource(getApplicationContext().getResources(), largeIconResID));
            }

            // Color
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                int defaultColor = getResources().getColor(getResources().getIdentifier("accent", "color", getPackageName()), null);
                if(color != null){
                    notificationBuilder.setColor(Color.parseColor(color));
                    Log.d(TAG, "Color: custom="+color);
                }else{
                    Log.d(TAG, "Color: default");
                    notificationBuilder.setColor(defaultColor);
                }
            }

            // Visibility
            int iVisibility = NotificationCompat.VISIBILITY_PUBLIC;
            if(visibility != null){
                iVisibility = Integer.parseInt(visibility);
            }
            Log.d(TAG, "Visibility: " + iVisibility);
            notificationBuilder.setVisibility(iVisibility);

            // Priority
            int iPriority = NotificationCompat.PRIORITY_MAX;
            if(priority != null){
                iPriority = Integer.parseInt(priority);
            }
            Log.d(TAG, "Priority: " + iPriority);
            notificationBuilder.setPriority(iPriority);


            // Build notification
            Notification notification = notificationBuilder.build();

            // Display notification
            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            Log.d(TAG, "notify: "+notification.toString());
            notificationManager.notify(id.hashCode(), notification);
        } else {
            bundle.putBoolean("tap", false);
            bundle.putString("title", title);
            bundle.putString("body", body);
            Log.d(TAG, "sendNotification: "+bundle.toString());
            FirebasePlugin.sendNotification(bundle, this.getApplicationContext());
        }
    }
}
