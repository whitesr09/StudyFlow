package app.studyflow;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;

/** A daily, inexact local reminder. No network or exact-alarm access is used. */
final class StudyReminder {
    static final String ACTION_DAILY = "app.studyflow.action.DAILY_REMINDER";
    static final String CHANNEL_ID = "studyflow-daily";
    static final int NOTIFICATION_ID = 7401;
    private static final int REQUEST_CODE = 7402;

    private StudyReminder() { }

    static void schedule(Context context, JSONObject root) {
        if (!root.optBoolean("remindersEnabled")) {
            cancel(context);
            return;
        }
        int hour = Math.max(0, Math.min(23, root.optInt("reminderHour", 9)));
        int minute = Math.max(0, Math.min(59, root.optInt("reminderMinute", 0)));
        long trigger = LocalDate.now().atTime(hour, minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        if (trigger <= System.currentTimeMillis())
            trigger = LocalDate.now().plusDays(1).atTime(hour, minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarms == null) return;
        PendingIntent pending = pendingIntent(context);
        if (Build.VERSION.SDK_INT >= 23) alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending);
        else alarms.set(AlarmManager.RTC_WAKEUP, trigger, pending);
    }

    static void cancel(Context context) {
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pending = pendingIntent(context);
        if (alarms != null) alarms.cancel(pending);
        pending.cancel();
    }

    private static PendingIntent pendingIntent(Context context) {
        Intent intent = new Intent(context, StudyReminderReceiver.class).setAction(ACTION_DAILY);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags);
    }

    static int[] dueCounts(JSONObject root, LocalDate today) {
        int cards = 0, assignments = 0;
        JSONArray cardRows = root.optJSONArray("cards");
        for (int i = 0; cardRows != null && i < cardRows.length(); i++) {
            JSONObject item = cardRows.optJSONObject(i);
            if (item != null && due(item.optString("due"), today)) cards++;
        }
        JSONArray assignmentRows = root.optJSONArray("assignments");
        for (int i = 0; assignmentRows != null && i < assignmentRows.length(); i++) {
            JSONObject item = assignmentRows.optJSONObject(i);
            if (item != null && !item.optBoolean("done") && due(item.optString("due"), today)) assignments++;
        }
        return new int[]{cards, assignments};
    }

    private static boolean due(String value, LocalDate today) {
        try { return !value.isEmpty() && !LocalDate.parse(value).isAfter(today); }
        catch (Exception ignored) { return false; }
    }

    static String summary(int[] counts) {
        String cardText = counts[0] + " review " + (counts[0] == 1 ? "card" : "cards");
        String assignmentText = counts[1] + " due " + (counts[1] == 1 ? "assignment" : "assignments");
        if (counts[0] == 0) return assignmentText;
        if (counts[1] == 0) return cardText;
        return String.format(Locale.getDefault(), "%s · %s", cardText, assignmentText);
    }
}