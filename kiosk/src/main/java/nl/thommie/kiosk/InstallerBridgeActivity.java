package nl.thommie.kiosk;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class InstallerBridgeActivity
        extends Activity {

    private static final String EXTRA_CONFIRMATION =
            "confirmation_intent";

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

    private boolean launched = false;
    private TextView statusView;

    private final Handler handler =
            new Handler(
                    Looper.getMainLooper()
            );

    static Intent createIntent(
            android.content.Context context,
            Intent confirmation
    ) {
        Intent intent =
                new Intent(
                        context,
                        InstallerBridgeActivity.class
                );

        intent.putExtra(
                EXTRA_CONFIRMATION,
                confirmation
        );

        intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP
        );

        return intent;
    }

    @Override
    protected void onCreate(
            Bundle savedInstanceState
    ) {
        super.onCreate(savedInstanceState);

        launched =
                savedInstanceState != null
                        && savedInstanceState.getBoolean(
                                "launched",
                                false
                        );

        buildUi();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (!launched) {
            launched = true;

            KioskPolicy.enterMaintenance(
                    this
            );

            Intent confirmation =
                    getConfirmationIntent();

            if (confirmation == null) {
                statusView.setText(
                        "Geen geldig Android-installatiescherm ontvangen."
                );
                return;
            }

            String installerPackage =
                    resolvePackage(
                            confirmation
                    );

            KioskPolicy.allowInstallerTemporarily(
                    this,
                    installerPackage
            );

            confirmation.addFlags(
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            );

            statusView.setText(
                    "Android-installatie wordt geopend...\n\n"
                            + "Rond ALLE bevestigingen af, ook een eventuele "
                            + "\"onveilige app / toch installeren\" melding."
            );

            handler.postDelayed(
                    () -> {
                        try {
                            startActivity(
                                    confirmation
                            );
                        } catch (Exception e) {
                            statusView.setText(
                                    "Android-installatiescherm kon niet openen.\n\n"
                                            + safeMessage(e)
                            );
                        }
                    },
                    250L
            );

            return;
        }

        /*
         * Belangrijk voor LineageOS:
         * hier NIET automatisch opnieuw Lock Task starten.
         *
         * De Package Installer / verifier kan meerdere systeemschermen
         * achter elkaar tonen. InstallResultReceiver herstelt de kiosk
         * pas nadat Android definitief SUCCESS of FAILURE meldt.
         */
        statusView.setText(
                "INSTALLATIE NOG BEZIG\n\n"
                        + "Rond alle Android-bevestigingen af. "
                        + "MAATJE wacht op het definitieve installatieresultaat.\n\n"
                        + "Heb je de installatie zelf geannuleerd en gebeurt er niets meer? "
                        + "Gebruik dan alleen de herstelknop hieronder."
        );
    }

    @Override
    protected void onSaveInstanceState(
            Bundle outState
    ) {
        outState.putBoolean(
                "launched",
                launched
        );
        super.onSaveInstanceState(outState);
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
                "MAATJE • UPDATEMODUS"
        );
        title.setTextColor(MINT);
        title.setTextSize(20f);
        title.setGravity(Gravity.CENTER);

        root.addView(title);

        statusView =
                new TextView(this);

        statusView.setText(
                "Installatie voorbereiden..."
        );
        statusView.setTextColor(TEXT);
        statusView.setTextSize(14f);
        statusView.setGravity(Gravity.CENTER);
        statusView.setPadding(
                0,
                dp(24),
                0,
                dp(28)
        );

        root.addView(statusView);

        Button recover =
                new Button(this);

        recover.setAllCaps(false);
        recover.setText(
                "INSTALLATIE GEANNULEERD • KIOSK HERSTELLEN"
        );
        recover.setTextColor(MINT);
        recover.setBackgroundColor(PANEL);
        recover.setOnClickListener(
                v -> restoreKioskManually()
        );

        root.addView(
                recover,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(62)
                )
        );

        TextView note =
                new TextView(this);

        note.setText(
                "Gebruik deze knop alleen als je Androids installatie bewust hebt geannuleerd."
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

    private Intent getConfirmationIntent() {
        Intent source =
                getIntent();

        if (source == null) {
            return null;
        }

        if (Build.VERSION.SDK_INT >= 33) {
            return source.getParcelableExtra(
                    EXTRA_CONFIRMATION,
                    Intent.class
            );
        }

        return source.getParcelableExtra(
                EXTRA_CONFIRMATION
        );
    }

    private String resolvePackage(
            Intent confirmation
    ) {
        try {
            ComponentName component =
                    confirmation.getComponent();

            if (component != null) {
                return component.getPackageName();
            }

            ComponentName resolved =
                    confirmation.resolveActivity(
                            getPackageManager()
                    );

            return resolved == null
                    ? null
                    : resolved.getPackageName();

        } catch (Exception ignored) {
            return null;
        }
    }

    private void restoreKioskManually() {
        KioskPolicy.restoreLockTaskPackages(
                this
        );

        Intent home =
                new Intent(
                        this,
                        KioskActivity.class
                );

        home.addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP
        );

        try {
            startActivity(home);
        } catch (Exception ignored) {}

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
