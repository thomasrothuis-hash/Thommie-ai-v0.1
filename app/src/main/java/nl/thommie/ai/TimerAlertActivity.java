package nl.thommie.ai;

import android.app.Activity;
import android.app.NotificationManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class TimerAlertActivity extends Activity {

    private static final int BG =
            Color.rgb(4, 8, 5);
    private static final int MINT =
            Color.rgb(54, 220, 104);
    private static final int TEXT =
            Color.rgb(216, 240, 222);
    private static final int MUTED =
            Color.rgb(105, 139, 113);

    private MediaPlayer player;
    private Vibrator vibrator;

    @Override
    protected void onCreate(
            Bundle savedInstanceState
    ) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(
                Window.FEATURE_NO_TITLE
        );

        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(
                    WindowManager.LayoutParams
                            .FLAG_SHOW_WHEN_LOCKED
                            | WindowManager.LayoutParams
                            .FLAG_TURN_SCREEN_ON
            );
        }

        getWindow().addFlags(
                WindowManager.LayoutParams
                        .FLAG_KEEP_SCREEN_ON
                        | WindowManager.LayoutParams
                        .FLAG_DISMISS_KEYGUARD
        );

        buildUi();
        startAlertSound();
    }

    @Override
    protected void onDestroy() {
        stopAlertSound();
        super.onDestroy();
    }

    private void buildUi() {
        String type =
                getIntent().getStringExtra(
                        "type"
                );

        String label =
                getIntent().getStringExtra(
                        "label"
                );

        boolean alarm =
                MaatjeScheduler.TYPE_ALARM
                        .equals(type);

        LinearLayout root =
                new LinearLayout(this);

        root.setOrientation(
                LinearLayout.VERTICAL
        );
        root.setGravity(
                Gravity.CENTER
        );
        root.setPadding(
                dp(28),
                dp(28),
                dp(28),
                dp(28)
        );
        root.setBackgroundColor(BG);

        TextView orb =
                new TextView(this);

        orb.setText("●");
        orb.setTextColor(MINT);
        orb.setTextSize(88);
        orb.setGravity(Gravity.CENTER);

        root.addView(orb);

        TextView title =
                new TextView(this);

        title.setText(
                alarm
                        ? "WEKKER"
                        : "TIMER KLAAR"
        );
        title.setTextColor(MINT);
        title.setTextSize(31);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(
                Typeface.create(
                        Typeface.MONOSPACE,
                        Typeface.BOLD
                )
        );

        root.addView(title);

        TextView message =
                new TextView(this);

        message.setText(
                label == null
                        || label.trim().isEmpty()
                        ? "MAATJE"
                        : label
        );
        message.setTextColor(TEXT);
        message.setTextSize(18);
        message.setGravity(Gravity.CENTER);
        message.setPadding(
                0,
                dp(18),
                0,
                dp(32)
        );

        root.addView(message);

        Button stop =
                new Button(this);

        stop.setAllCaps(false);
        stop.setText(
                "STOP"
        );
        stop.setTextSize(20);
        stop.setTextColor(BG);
        stop.setBackgroundColor(MINT);
        stop.setOnClickListener(
                v -> {
                    stopAlertSound();
                    clearNotification();
                    finish();
                }
        );

        root.addView(
                stop,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams
                                .MATCH_PARENT,
                        dp(68)
                )
        );

        TextView hint =
                new TextView(this);

        hint.setText(
                "MAATJE OnePlus Edition"
        );
        hint.setTextColor(MUTED);
        hint.setTextSize(11);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(
                0,
                dp(24),
                0,
                0
        );

        root.addView(hint);

        setContentView(root);
    }

    private void startAlertSound() {
        try {
            Uri uri =
                    RingtoneManager.getDefaultUri(
                            RingtoneManager.TYPE_ALARM
                    );

            if (uri == null) {
                uri =
                        RingtoneManager
                                .getDefaultUri(
                                        RingtoneManager
                                                .TYPE_NOTIFICATION
                                );
            }

            player =
                    new MediaPlayer();

            player.setDataSource(
                    this,
                    uri
            );

            player.setAudioAttributes(
                    new AudioAttributes.Builder()
                            .setUsage(
                                    AudioAttributes
                                            .USAGE_ALARM
                            )
                            .setContentType(
                                    AudioAttributes
                                            .CONTENT_TYPE_SONIFICATION
                            )
                            .build()
            );

            player.setLooping(true);
            player.prepare();
            player.start();

        } catch (Exception ignored) {}

        try {
            vibrator =
                    (Vibrator)
                            getSystemService(
                                    VIBRATOR_SERVICE
                            );

            if (vibrator != null
                    && vibrator.hasVibrator()) {
                long[] pattern =
                        new long[]{
                                0,
                                600,
                                250,
                                600
                        };

                if (Build.VERSION.SDK_INT >= 26) {
                    vibrator.vibrate(
                            VibrationEffect
                                    .createWaveform(
                                            pattern,
                                            0
                                    )
                    );
                } else {
                    vibrator.vibrate(
                            pattern,
                            0
                    );
                }
            }
        } catch (Exception ignored) {}
    }

    private void stopAlertSound() {
        MediaPlayer p = player;
        player = null;

        if (p != null) {
            try {
                p.stop();
            } catch (Exception ignored) {}

            try {
                p.release();
            } catch (Exception ignored) {}
        }

        Vibrator v = vibrator;
        vibrator = null;

        if (v != null) {
            try {
                v.cancel();
            } catch (Exception ignored) {}
        }
    }

    private void clearNotification() {
        try {
            NotificationManager manager =
                    (NotificationManager)
                            getSystemService(
                                    NOTIFICATION_SERVICE
                            );

            if (manager != null) {
                manager.cancel(9001);
            }
        } catch (Exception ignored) {}
    }

    private int dp(int value) {
        return Math.round(
                value
                        * getResources()
                                .getDisplayMetrics()
                                .density
        );
    }
}
