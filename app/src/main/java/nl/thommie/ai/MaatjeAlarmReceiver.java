package nl.thommie.ai;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;

public class MaatjeAlarmReceiver
        extends BroadcastReceiver {

    static final String CHANNEL_ID =
            "maatje_timers_alarms";

    @Override
    public void onReceive(
            Context context,
            Intent intent
    ) {
        if (intent == null) {
            return;
        }

        String id =
                intent.getStringExtra("id");
        String type =
                intent.getStringExtra("type");
        String label =
                intent.getStringExtra("label");

        if (id == null) {
            return;
        }

        MaatjeScheduler.consume(
                context,
                id
        );

        Intent alert =
                new Intent(
                        context,
                        TimerAlertActivity.class
                );

        alert.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP
        );

        alert.putExtra("id", id);
        alert.putExtra("type", type);
        alert.putExtra("label", label);

        try {
            context.startActivity(alert);
        } catch (Exception ignored) {}

        showNotification(
                context,
                alert,
                type,
                label
        );
    }

    private void showNotification(
            Context context,
            Intent alert,
            String type,
            String label
    ) {
        try {
            NotificationManager manager =
                    (NotificationManager)
                            context.getSystemService(
                                    Context.NOTIFICATION_SERVICE
                            );

            if (manager == null) {
                return;
            }

            Uri sound =
                    RingtoneManager.getDefaultUri(
                            RingtoneManager.TYPE_ALARM
                    );

            if (Build.VERSION.SDK_INT >= 26) {
                NotificationChannel channel =
                        new NotificationChannel(
                                CHANNEL_ID,
                                "MAATJE timers en wekkers",
                                NotificationManager.IMPORTANCE_HIGH
                        );

                channel.enableVibration(true);
                channel.setLockscreenVisibility(1);

                AudioAttributes attrs =
                        new AudioAttributes.Builder()
                                .setUsage(
                                        AudioAttributes
                                                .USAGE_ALARM
                                )
                                .build();

                channel.setSound(
                        sound,
                        attrs
                );

                manager.createNotificationChannel(
                        channel
                );
            }

            PendingIntent fullScreen =
                    PendingIntent.getActivity(
                            context,
                            9001,
                            alert,
                            PendingIntent.FLAG_UPDATE_CURRENT
                                    | (
                                    Build.VERSION.SDK_INT >= 23
                                            ? PendingIntent.FLAG_IMMUTABLE
                                            : 0
                            )
                    );

            String title =
                    MaatjeScheduler.TYPE_ALARM
                            .equals(type)
                            ? "MAATJE wekker"
                            : "MAATJE timer";

            String text =
                    label == null
                            || label.trim().isEmpty()
                            ? "Tijd is om."
                            : label;

            android.app.Notification.Builder builder =
                    Build.VERSION.SDK_INT >= 26
                            ? new android.app.Notification.Builder(
                                    context,
                                    CHANNEL_ID
                            )
                            : new android.app.Notification.Builder(
                                    context
                            );

            builder.setSmallIcon(
                            android.R.drawable
                                    .ic_lock_idle_alarm
                    )
                    .setContentTitle(title)
                    .setContentText(text)
                    .setCategory(
                            android.app.Notification
                                    .CATEGORY_ALARM
                    )
                    .setPriority(
                            android.app.Notification
                                    .PRIORITY_MAX
                    )
                    .setAutoCancel(true)
                    .setContentIntent(fullScreen)
                    .setFullScreenIntent(
                            fullScreen,
                            true
                    )
                    .setSound(sound)
                    .setVibrate(
                            new long[]{
                                    0,
                                    600,
                                    250,
                                    600
                            }
                    );

            manager.notify(
                    9001,
                    builder.build()
            );

        } catch (Exception ignored) {}
    }
}
