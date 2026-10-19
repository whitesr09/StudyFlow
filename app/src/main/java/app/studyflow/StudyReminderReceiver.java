package app.studyflow;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import org.json.JSONObject;

import java.time.LocalDate;

public final class StudyReminderReceiver extends BroadcastReceiver {
    private static final String BOOT = "android.intent.action.BOOT_COMPLETED";
    private static final String TIME_SET = "android.intent.action.TIME_SET";
    private static final String TIMEZONE_CHANGED = "android.intent.action.TIMEZONE_CHANGED";

    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        try {
            Store store = new Store(context);
            JSONObject root = store.root;
            if (BOOT.equals(intent.getAction()) || TIME_SET.equals(intent.getAction()) || TIMEZONE_CHANGED.equals(intent.getAction())) {
                StudyReminder.schedule(context, root);
                return;
            }
            if (!StudyReminder.ACTION_DAILY.equals(intent.getAction()) || !root.optBoolean("remindersEnabled")) return;
            StudyReminder.schedule(context, root);
            int[] counts = StudyReminder.dueCounts(root, LocalDate.now());
            if (counts[0] + counts[1] == 0) return;
            if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;

            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager == null) return;
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationChannel channel = new NotificationChannel(StudyReminder.CHANNEL_ID,"Study reminders",NotificationManager.IMPORTANCE_DEFAULT);
                channel.setDescription("Optional reminders for due review cards and assignments.");
                manager.createNotificationChannel(channel);
            }
            Intent open = new Intent(context, MainActivity.class).setAction(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
            PendingIntent content = PendingIntent.getActivity(context, StudyReminder.NOTIFICATION_ID, open, flags);
            Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                    ? new Notification.Builder(context, StudyReminder.CHANNEL_ID)
                    : new Notification.Builder(context);
            Notification notification = builder.setSmallIcon(R.drawable.notification_icon)
                    .setContentTitle("StudyFlow · a gentle nudge")
                    .setContentText(StudyReminder.summary(counts))
                    .setStyle(new Notification.BigTextStyle().bigText(StudyReminder.summary(counts)))
                    .setContentIntent(content)
                    .setAutoCancel(true)
                    .build();
            manager.notify(StudyReminder.NOTIFICATION_ID, notification);
        } catch (Exception ignored) {
            // A reminder must never interrupt device boot or other system broadcasts.
        }
    }
}