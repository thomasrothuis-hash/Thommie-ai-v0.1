package nl.thommie.kiosk;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class SettingsBridgeActivity
        extends Activity {

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

    private boolean settingsOpened = false;
    private TextView status;

    @Override
    protected void onCreate(
            Bundle savedInstanceState
    ) {
        super.onCreate(savedInstanceState);

        settingsOpened =
                savedInstanceState != null
                        && savedInstanceState.getBoolean(
                                "settings_opened",
                                false
                        );

        buildUi();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (!settingsOpened) {
            settingsOpened = true;
            openSettings();
            return;
        }

        status.setText(
                "ANDROID-INSTELLINGEN ZIJN GESLOTEN\n\n"
                        + "De kiosk blijft bewust in onderhoudsmodus. "
                        + "Druk hieronder om MAATJE weer volledig te vergrendelen."
        );
    }

    @Override
    protected void onSaveInstanceState(
            Bundle outState
    ) {
        outState.putBoolean(
                "settings_opened",
                settingsOpened
        );

        super.onSaveInstanceState(
                outState
        );
    }

    private void openSettings() {
        KioskPolicy.enterMaintenance(
                this
        );

        Intent intent =
                new Intent(
                        Settings.ACTION_SETTINGS
                );

        ComponentName resolved = null;

        try {
            resolved =
                    intent.resolveActivity(
                            getPackageManager()
                    );
        } catch (Exception ignored) {}

        if (resolved != null) {
            KioskPolicy.allowPackageTemporarily(
                    this,
                    resolved.getPackageName()
            );

            intent.setComponent(
                    resolved
            );
        } else {
            KioskPolicy.allowPackageTemporarily(
                    this,
                    "com.android.settings"
            );
        }

        intent.addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP
        );

        status.setText(
                "ANDROID-INSTELLINGEN OPENEN..."
        );

        try {
            startActivity(
                    intent
            );
        } catch (Exception first) {
            try {
                Intent fallback =
                        new Intent();

                fallback.setComponent(
                        new ComponentName(
                                "com.android.settings",
                                "com.android.settings.Settings"
                        )
                );

                startActivity(
                        fallback
                );
            } catch (Exception second) {
                status.setText(
                        "Android-instellingen konden niet worden geopend.\n\n"
                                + safeMessage(second)
                );
            }
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
        root.setPadding(
                dp(28),
                dp(28),
                dp(28),
                dp(28)
        );
        root.setBackgroundColor(BG);

        TextView title =
                new TextView(this);

        title.setText(
                "MAATJE • ONDERHOUDSMODUS"
        );
        title.setTextColor(MINT);
        title.setTextSize(20f);
        title.setGravity(Gravity.CENTER);

        root.addView(title);

        status =
                new TextView(this);

        status.setText(
                "Android-instellingen voorbereiden..."
        );
        status.setTextColor(TEXT);
        status.setTextSize(14f);
        status.setGravity(Gravity.CENTER);
        status.setPadding(
                0,
                dp(24),
                0,
                dp(28)
        );

        root.addView(status);

        Button restore =
                new Button(this);

        restore.setAllCaps(false);
        restore.setText(
                "KIOSK HERSTELLEN → MAATJE"
        );
        restore.setTextColor(MINT);
        restore.setBackgroundColor(PANEL);
        restore.setOnClickListener(
                v -> restoreKiosk()
        );

        root.addView(
                restore,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(62)
                )
        );

        TextView note =
                new TextView(this);

        note.setText(
                "Tijdens onderhoud blijft Android vrij toegankelijk. "
                        + "Vergrendel hem daarom weer wanneer je klaar bent."
        );
        note.setTextColor(MUTED);
        note.setTextSize(11f);
        note.setGravity(Gravity.CENTER);
        note.setPadding(
                0,
                dp(16),
                0,
                0
        );

        root.addView(note);

        setContentView(root);
    }

    private void restoreKiosk() {
        KioskPolicy.restoreLockTaskPackages(
                this
        );

        KioskPolicy.apply(
                this
        );

        KioskPolicy.launchMaatje(
                this
        );

        finish();
    }

    private String safeMessage(
            Exception e
    ) {
        String message =
                e.getMessage();

        return message == null
                || message.trim().isEmpty()
                ? e.getClass().getSimpleName()
                : message;
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
