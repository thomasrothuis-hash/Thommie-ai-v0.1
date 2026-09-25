package nl.thommie.ai;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

final class MaatjeScheduler {

    static final String TYPE_TIMER = "timer";
    static final String TYPE_ALARM = "alarm";

    static final class Entry {
        final String id;
        final String type;
        final String label;
        final long triggerAt;

        Entry(
                String id,
                String type,
                String label,
                long triggerAt
        ) {
            this.id = id;
            this.type = type;
            this.label = label;
            this.triggerAt = triggerAt;
        }
    }

    private static final String PREFS =
            "maatje_scheduler";
    private static final String KEY_ENTRIES =
            "entries_json";

    private MaatjeScheduler() {}

    static Entry scheduleTimer(
            Context context,
            int seconds,
            String label
    ) throws Exception {
        if (seconds <= 0) {
            throw new IllegalArgumentException(
                    "Timerduur moet groter dan nul zijn."
            );
        }

        long triggerAt =
                System.currentTimeMillis()
                        + (seconds * 1000L);

        Entry entry =
                new Entry(
                        UUID.randomUUID().toString(),
                        TYPE_TIMER,
                        cleanLabel(label, "MAATJE timer"),
                        triggerAt
                );

        addAndSchedule(
                context,
                entry
        );

        return entry;
    }

    static Entry scheduleAlarm(
            Context context,
            int hour,
            int minute,
            boolean tomorrow,
            String label
    ) throws Exception {
        Calendar now =
                Calendar.getInstance();

        Calendar target =
                Calendar.getInstance();

        target.set(
                Calendar.HOUR_OF_DAY,
                hour
        );
        target.set(
                Calendar.MINUTE,
                minute
        );
        target.set(
                Calendar.SECOND,
                0
        );
        target.set(
                Calendar.MILLISECOND,
                0
        );

        if (tomorrow) {
            target.add(
                    Calendar.DAY_OF_YEAR,
                    1
            );
        } else if (!target.after(now)) {
            target.add(
                    Calendar.DAY_OF_YEAR,
                    1
            );
        }

        Entry entry =
                new Entry(
                        UUID.randomUUID().toString(),
                        TYPE_ALARM,
                        cleanLabel(label, "MAATJE wekker"),
                        target.getTimeInMillis()
                );

        addAndSchedule(
                context,
                entry
        );

        return entry;
    }

    static List<Entry> list(
            Context context
    ) {
        List<Entry> entries =
                readEntries(context);

        entries.sort(
                Comparator.comparingLong(
                        e -> e.triggerAt
                )
        );

        return entries;
    }

    static void cancel(
            Context context,
            String id
    ) {
        List<Entry> entries =
                readEntries(context);

        List<Entry> keep =
                new ArrayList<>();

        for (Entry entry : entries) {
            if (entry.id.equals(id)) {
                cancelAlarmManager(
                        context,
                        entry
                );
            } else {
                keep.add(entry);
            }
        }

        writeEntries(
                context,
                keep
        );
    }

    static void cancelAll(
            Context context
    ) {
        List<Entry> entries =
                readEntries(context);

        for (Entry entry : entries) {
            cancelAlarmManager(
                    context,
                    entry
            );
        }

        writeEntries(
                context,
                new ArrayList<>()
        );
    }

    static int cancelType(
            Context context,
            String type
    ) {
        List<Entry> entries =
                readEntries(context);

        List<Entry> keep =
                new ArrayList<>();

        int cancelled = 0;

        for (Entry entry : entries) {
            if (type.equals(entry.type)) {
                cancelAlarmManager(
                        context,
                        entry
                );
                cancelled++;
            } else {
                keep.add(entry);
            }
        }

        writeEntries(
                context,
                keep
        );

        return cancelled;
    }

    static void consume(
            Context context,
            String id
    ) {
        List<Entry> entries =
                readEntries(context);

        List<Entry> keep =
                new ArrayList<>();

        for (Entry entry : entries) {
            if (!entry.id.equals(id)) {
                keep.add(entry);
            }
        }

        writeEntries(
                context,
                keep
        );
    }

    static void rescheduleAll(
            Context context
    ) {
        long now =
                System.currentTimeMillis();

        List<Entry> entries =
                readEntries(context);

        List<Entry> keep =
                new ArrayList<>();

        for (Entry entry : entries) {
            if (entry.triggerAt <= now) {
                fireLate(
                        context,
                        entry
                );
            } else {
                try {
                    scheduleAlarmManager(
                            context,
                            entry
                    );
                    keep.add(entry);
                } catch (Exception ignored) {}
            }
        }

        writeEntries(
                context,
                keep
        );
    }

    private static void addAndSchedule(
            Context context,
            Entry entry
    ) throws Exception {
        List<Entry> entries =
                readEntries(context);

        entries.add(entry);

        writeEntries(
                context,
                entries
        );

        try {
            scheduleAlarmManager(
                    context,
                    entry
            );
        } catch (Exception e) {
            entries.removeIf(
                    item -> item.id.equals(
                            entry.id
                    )
            );

            writeEntries(
                    context,
                    entries
            );

            throw e;
        }
    }

    private static void scheduleAlarmManager(
            Context context,
            Entry entry
    ) throws Exception {
        AlarmManager manager =
                (AlarmManager)
                        context.getSystemService(
                                Context.ALARM_SERVICE
                        );

        if (manager == null) {
            throw new Exception(
                    "AlarmManager niet beschikbaar."
            );
        }

        PendingIntent fireIntent =
                buildFireIntent(
                        context,
                        entry
                );

        Intent show =
                new Intent(
                        context,
                        MaatjeScheduleActivity.class
                );

        show.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP
        );

        PendingIntent showIntent =
                PendingIntent.getActivity(
                        context,
                        requestCode(
                                entry.id,
                                1
                        ),
                        show,
                        pendingFlags()
                );

        try {
            AlarmManager.AlarmClockInfo info =
                    new AlarmManager
                            .AlarmClockInfo(
                            entry.triggerAt,
                            showIntent
                    );

            manager.setAlarmClock(
                    info,
                    fireIntent
            );

        } catch (SecurityException denied) {
            if (Build.VERSION.SDK_INT >= 23) {
                manager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        entry.triggerAt,
                        fireIntent
                );
            } else {
                manager.set(
                        AlarmManager.RTC_WAKEUP,
                        entry.triggerAt,
                        fireIntent
                );
            }
        }
    }

    private static void cancelAlarmManager(
            Context context,
            Entry entry
    ) {
        try {
            AlarmManager manager =
                    (AlarmManager)
                            context.getSystemService(
                                    Context.ALARM_SERVICE
                            );

            if (manager != null) {
                manager.cancel(
                        buildFireIntent(
                                context,
                                entry
                        )
                );
            }
        } catch (Exception ignored) {}
    }

    private static PendingIntent buildFireIntent(
            Context context,
            Entry entry
    ) {
        Intent intent =
                new Intent(
                        context,
                        MaatjeAlarmReceiver.class
                );

        intent.setAction(
                "nl.thommie.ai.SCHEDULER_FIRE."
                        + entry.id
        );

        intent.putExtra(
                "id",
                entry.id
        );
        intent.putExtra(
                "type",
                entry.type
        );
        intent.putExtra(
                "label",
                entry.label
        );
        intent.putExtra(
                "trigger_at",
                entry.triggerAt
        );

        return PendingIntent.getBroadcast(
                context,
                requestCode(
                        entry.id,
                        0
                ),
                intent,
                pendingFlags()
        );
    }

    private static void fireLate(
            Context context,
            Entry entry
    ) {
        Intent intent =
                new Intent(
                        context,
                        MaatjeAlarmReceiver.class
                );

        intent.putExtra(
                "id",
                entry.id
        );
        intent.putExtra(
                "type",
                entry.type
        );
        intent.putExtra(
                "label",
                entry.label
        );
        intent.putExtra(
                "trigger_at",
                entry.triggerAt
        );

        context.sendBroadcast(
                intent
        );
    }

    private static List<Entry> readEntries(
            Context context
    ) {
        List<Entry> result =
                new ArrayList<>();

        try {
            String raw =
                    context.getSharedPreferences(
                                    PREFS,
                                    Context.MODE_PRIVATE
                            )
                            .getString(
                                    KEY_ENTRIES,
                                    "[]"
                            );

            JSONArray array =
                    new JSONArray(raw);

            for (int i = 0;
                 i < array.length();
                 i++) {
                JSONObject item =
                        array.optJSONObject(i);

                if (item == null) {
                    continue;
                }

                String id =
                        item.optString(
                                "id",
                                ""
                        );

                String type =
                        item.optString(
                                "type",
                                ""
                        );

                long triggerAt =
                        item.optLong(
                                "trigger_at",
                                0L
                        );

                if (id.isEmpty()
                        || triggerAt <= 0L) {
                    continue;
                }

                result.add(
                        new Entry(
                                id,
                                type,
                                item.optString(
                                        "label",
                                        "MAATJE"
                                ),
                                triggerAt
                        )
                );
            }

        } catch (Exception ignored) {}

        return result;
    }

    private static void writeEntries(
            Context context,
            List<Entry> entries
    ) {
        JSONArray array =
                new JSONArray();

        for (Entry entry : entries) {
            try {
                array.put(
                        new JSONObject()
                                .put(
                                        "id",
                                        entry.id
                                )
                                .put(
                                        "type",
                                        entry.type
                                )
                                .put(
                                        "label",
                                        entry.label
                                )
                                .put(
                                        "trigger_at",
                                        entry.triggerAt
                                )
                );
            } catch (Exception ignored) {}
        }

        context.getSharedPreferences(
                        PREFS,
                        Context.MODE_PRIVATE
                )
                .edit()
                .putString(
                        KEY_ENTRIES,
                        array.toString()
                )
                .apply();
    }

    private static int requestCode(
            String id,
            int salt
    ) {
        return (
                id.hashCode() * 31
                        + salt
        ) & 0x7fffffff;
    }

    private static int pendingFlags() {
        int flags =
                PendingIntent
                        .FLAG_UPDATE_CURRENT;

        if (Build.VERSION.SDK_INT >= 23) {
            flags |=
                    PendingIntent
                            .FLAG_IMMUTABLE;
        }

        return flags;
    }

    private static String cleanLabel(
            String label,
            String fallback
    ) {
        if (label == null
                || label.trim().isEmpty()) {
            return fallback;
        }

        String clean =
                label.trim();

        if (clean.length() > 80) {
            clean =
                    clean.substring(
                            0,
                            80
                    );
        }

        return clean;
    }
}
