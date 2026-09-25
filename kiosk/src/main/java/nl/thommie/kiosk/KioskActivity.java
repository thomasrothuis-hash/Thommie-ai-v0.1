package nl.thommie.kiosk;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class KioskActivity extends Activity {

    private static final int BG =
            Color.rgb(4, 8, 5);
    private static final int PANEL =
            Color.rgb(9, 17, 11);
    private static final int MINT =
            Color.rgb(54, 220, 104);
    private static final int TEXT =
            Color.rgb(216, 240, 222);
    private static final int MUTED =
            Color.rgb(105, 139, 113);

    private final Handler handler =
            new Handler(
                    Looper.getMainLooper()
            );

    private int logoTapCount = 0;
    private long firstLogoTapMs = 0L;
    private TextView status;

    @Override
    protected void onCreate(
            Bundle savedInstanceState
    ) {
        super.onCreate(savedInstanceState);

        buildUi();
    }

    @Override
    protected void onResume() {
        super.onResume();

        boolean owner =
                KioskPolicy.isDeviceOwner(
                        this
                );

        if (owner) {
            status.setText(
                    "KIOSK ACTIVE • MAATJE ONLY"
            );

            KioskPolicy.apply(this);

            handler.postDelayed(
                    () -> {
                        if (!isFinishing()) {
                            KioskPolicy
                                    .launchMaatje(this);
                        }
                    },
                    650L
            );
        } else {
            status.setText(
                    "SETUP REQUIRED • DEVICE OWNER NOT ACTIVE"
            );
        }
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
        root.setBackgroundColor(BG);
        root.setPadding(
                dp(28),
                dp(28),
                dp(28),
                dp(28)
        );

        TextView logo =
                new TextView(this);

        logo.setText(
                "●\nMAATJE"
        );
        logo.setGravity(
                Gravity.CENTER
        );
        logo.setTextColor(MINT);
        logo.setTextSize(38);
        logo.setTypeface(
                Typeface.create(
                        Typeface.MONOSPACE,
                        Typeface.BOLD
                )
        );
        logo.setClickable(true);
        logo.setOnClickListener(
                v -> handleLogoTap()
        );

        root.addView(
                logo,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(190)
                )
        );

        TextView subtitle =
                new TextView(this);

        subtitle.setText(
                "DEDICATED AI TERMINAL"
        );
        subtitle.setGravity(
                Gravity.CENTER
        );
        subtitle.setTextColor(MUTED);
        subtitle.setTextSize(12);
        subtitle.setTypeface(
                Typeface.MONOSPACE
        );

        root.addView(subtitle);

        status =
                new TextView(this);

        status.setGravity(
                Gravity.CENTER
        );
        status.setTextColor(TEXT);
        status.setTextSize(13);
        status.setTypeface(
                Typeface.MONOSPACE
        );
        status.setPadding(
                0,
                dp(22),
                0,
                dp(22)
        );

        root.addView(status);

        Button start =
                new Button(this);

        start.setAllCaps(false);
        start.setText(
                "MAATJE STARTEN"
        );
        start.setTextColor(MINT);
        start.setBackgroundColor(PANEL);
        start.setOnClickListener(
                v -> {
                    if (!KioskPolicy
                            .launchMaatje(this)) {
                        status.setText(
                                "MAATJE IS NIET GEÏNSTALLEERD"
                        );
                    }
                }
        );

        root.addView(
                start,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(58)
                )
        );

        TextView hint =
                new TextView(this);

        hint.setText(
                "Beheer: tik 5× snel op het MAATJE-logo."
        );
        hint.setGravity(
                Gravity.CENTER
        );
        hint.setTextColor(MUTED);
        hint.setTextSize(11);
        hint.setPadding(
                0,
                dp(24),
                0,
                0
        );

        root.addView(hint);

        setContentView(root);
    }

    private void handleLogoTap() {
        long now =
                System.currentTimeMillis();

        if (firstLogoTapMs == 0L
                || now - firstLogoTapMs > 2200L) {
            firstLogoTapMs = now;
            logoTapCount = 1;
            return;
        }

        logoTapCount++;

        if (logoTapCount >= 5) {
            logoTapCount = 0;
            firstLogoTapMs = 0L;

            startActivity(
                    new Intent(
                            this,
                            AdminUnlockActivity.class
                    )
            );
        }
    }

    private int dp(int value) {
        return Math.round(
                value * getResources()
                        .getDisplayMetrics()
                        .density
        );
    }
}
