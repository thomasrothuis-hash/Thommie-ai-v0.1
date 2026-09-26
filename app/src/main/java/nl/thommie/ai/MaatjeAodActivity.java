package nl.thommie.ai;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MaatjeAodActivity
        extends Activity {

    static final String ACTION_WAKE_DISPLAY =
            "nl.thommie.ai.WAKE_DISPLAY";

    private TextView clock;
    private TextView date;
    private TextView battery;

    private BackgroundWakeWord
            aodWakeWord;

    private final Handler handler =
            new Handler(
                    Looper.getMainLooper()
            );

    private final Runnable clockRunnable =
            new Runnable() {
                @Override
                public void run() {
                    updateClock();

                    handler.postDelayed(
                            this,
                            15000L
                    );
                }
            };

    private final BroadcastReceiver wakeReceiver =
            new BroadcastReceiver() {
                @Override
                public void onReceive(
                        Context context,
                        Intent intent
                ) {
                    if (intent != null
                            && ACTION_WAKE_DISPLAY
                            .equals(
                                    intent.getAction()
                            )) {
                        openMaatje();
                    }
                }
            };

    @Override
    protected void onCreate(
            Bundle savedInstanceState
    ) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(
                WindowManager.LayoutParams
                        .FLAG_KEEP_SCREEN_ON
        );

        WindowManager.LayoutParams params =
                getWindow().getAttributes();

        params.screenBrightness = .035f;

        getWindow().setAttributes(
                params
        );

        if (android.os.Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }

        buildUi();
        hideSystemUi();
        ensureLockTask();

        MaatjeVoiceInteractionService
                .setAodVisible(true);

        aodWakeWord =
                new BackgroundWakeWord(
                        this,
                        new BackgroundWakeWord.Callback() {
                            @Override
                            public void onReady() {
                                if (aodWakeWord != null) {
                                    aodWakeWord.start();
                                }
                            }

                            @Override
                            public void onDetected() {
                                handler.post(
                                        MaatjeAodActivity.this
                                                ::openMaatjeFromWakeWord
                                );
                            }

                            @Override
                            public void onError(
                                    String message
                            ) {
                                handler.postDelayed(
                                        () -> {
                                            if (aodWakeWord != null) {
                                                aodWakeWord.start();
                                            }
                                        },
                                        1200L
                                );
                            }
                        }
                );

        aodWakeWord.prepare();

        IntentFilter filter =
                new IntentFilter(
                        ACTION_WAKE_DISPLAY
                );

        if (android.os.Build.VERSION.SDK_INT >= 33) {
            registerReceiver(
                    wakeReceiver,
                    filter,
                    Context.RECEIVER_NOT_EXPORTED
            );
        } else {
            registerReceiver(
                    wakeReceiver,
                    filter
            );
        }

        handler.post(
                clockRunnable
        );
    }

    @Override
    protected void onResume() {
        super.onResume();

        MaatjeVoiceInteractionService
                .setAodVisible(true);

        hideSystemUi();
        ensureLockTask();

        if (aodWakeWord != null
                && aodWakeWord.isReady()) {
            aodWakeWord.start();
        }
    }

    @Override
    protected void onPause() {
        if (aodWakeWord != null) {
            aodWakeWord.stop();
        }

        super.onPause();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(
                null
        );

        try {
            unregisterReceiver(
                    wakeReceiver
            );
        } catch (Exception ignored) {}

        if (aodWakeWord != null) {
            aodWakeWord.destroy();
            aodWakeWord = null;
        }

        MaatjeVoiceInteractionService
                .setAodVisible(false);

        super.onDestroy();
    }

    @Override
    public boolean dispatchTouchEvent(
            MotionEvent event
    ) {
        if (event.getActionMasked()
                == MotionEvent.ACTION_DOWN) {
            openMaatje();
            return true;
        }

        return super.dispatchTouchEvent(
                event
        );
    }

    private void buildUi() {
        LinearLayout root =
                new LinearLayout(this);

        root.setOrientation(
                LinearLayout.VERTICAL
        );

        root.setGravity(
                Gravity.CENTER
        );

        root.setBackgroundColor(
                Color.BLACK
        );

        TextView brand =
                new TextView(this);

        brand.setText(
                "MAATJE"
        );
        brand.setTextColor(
                Color.rgb(
                        54,
                        220,
                        104
                )
        );
        brand.setTextSize(14f);
        brand.setLetterSpacing(.28f);
        brand.setTypeface(
                Typeface.create(
                        Typeface.MONOSPACE,
                        Typeface.BOLD
                )
        );
        brand.setGravity(
                Gravity.CENTER
        );

        root.addView(brand);

        clock =
                new TextView(this);

        clock.setTextColor(
                Color.rgb(
                        190,
                        255,
                        205
                )
        );
        clock.setTextSize(58f);
        clock.setTypeface(
                Typeface.create(
                        Typeface.SANS_SERIF,
                        Typeface.NORMAL
                )
        );
        clock.setGravity(
                Gravity.CENTER
        );

        clock.setPadding(
                0,
                dp(18),
                0,
                0
        );

        root.addView(clock);

        date =
                new TextView(this);

        date.setTextColor(
                Color.rgb(
                        82,
                        125,
                        92
                )
        );
        date.setTextSize(13f);
        date.setGravity(
                Gravity.CENTER
        );

        root.addView(date);

        battery =
                new TextView(this);

        battery.setTextColor(
                Color.rgb(
                        54,
                        220,
                        104
                )
        );
        battery.setTextSize(11f);
        battery.setGravity(
                Gravity.CENTER
        );

        battery.setPadding(
                0,
                dp(28),
                0,
                0
        );

        root.addView(battery);

        TextView hint =
                new TextView(this);

        hint.setText(
                "ZEG “HEY MAATJE” • OF TIK OM TE OPENEN"
        );
        hint.setTextColor(
                Color.rgb(
                        64,
                        92,
                        70
                )
        );
        hint.setTextSize(10f);
        hint.setLetterSpacing(.10f);
        hint.setGravity(
                Gravity.CENTER
        );
        hint.setPadding(
                dp(24),
                dp(18),
                dp(24),
                0
        );

        root.addView(hint);

        setContentView(root);
    }

    private void updateClock() {
        Date now =
                new Date();

        clock.setText(
                new SimpleDateFormat(
                        "HH:mm",
                        Locale.getDefault()
                ).format(now)
        );

        date.setText(
                new SimpleDateFormat(
                        "EEEE • d MMMM",
                        Locale.getDefault()
                ).format(now)
        );

        BatteryManager manager =
                (BatteryManager)
                        getSystemService(
                                BATTERY_SERVICE
                        );

        int level =
                manager == null
                        ? -1
                        : manager.getIntProperty(
                                BatteryManager
                                        .BATTERY_PROPERTY_CAPACITY
                        );

        battery.setText(
                level >= 0
                        ? "BATTERIJ • "
                        + level
                        + "%"
                        : "AOD • STANDBY"
        );
    }

    private void openMaatje() {
        openMaatje(false);
    }

    private void openMaatjeFromWakeWord() {
        openMaatje(true);
    }

    private void openMaatje(
            boolean fromWakeWord
    ) {
        if (aodWakeWord != null) {
            aodWakeWord.stop();
        }

        MaatjeVoiceInteractionService
                .setAodVisible(false);

        Intent intent =
                new Intent(
                        this,
                        MainActivity.class
                );

        intent.addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP
        );

        if (fromWakeWord) {
            intent.putExtra(
                    MainActivity
                            .EXTRA_ASSISTANT_INVOCATION,
                    true
            );
        }

        try {
            startActivity(intent);
        } catch (Exception ignored) {}

        finish();
    }

    private void ensureLockTask() {
        try {
            DevicePolicyManager dpm =
                    (DevicePolicyManager)
                            getSystemService(
                                    DEVICE_POLICY_SERVICE
                            );

            if (dpm != null
                    && dpm.isLockTaskPermitted(
                    getPackageName()
            )) {
                startLockTask();
            }

        } catch (Exception ignored) {}
    }

    private void hideSystemUi() {
        View decor =
                getWindow().getDecorView();

        decor.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        );

        if (android.os.Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(
                    false
            );

            WindowInsetsController controller =
                    getWindow().getInsetsController();

            if (controller != null) {
                controller.hide(
                        WindowInsets.Type.statusBars()
                                | WindowInsets.Type.navigationBars()
                );
            }
        }
    }

    private int dp(
            int value
    ) {
        return Math.round(
                value
                        * getResources()
                        .getDisplayMetrics()
                        .density
        );
    }
}
