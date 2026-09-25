package nl.thommie.kiosk;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.FileProvider;

import java.io.File;

public class InstallerBridgeActivity
        extends Activity {

    private static final String EXTRA_APK_PATH =
            "apk_path";
    private static final String EXTRA_EXPECTED_VERSION =
            "expected_version";

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

    static Intent createFileInstallIntent(
            android.content.Context context,
            File apk,
            String expectedVersion
    ) {
        Intent intent =
                new Intent(
                        context,
                        InstallerBridgeActivity.class
                );

        intent.putExtra(
                EXTRA_APK_PATH,
                apk.getAbsolutePath()
        );
        intent.putExtra(
                EXTRA_EXPECTED_VERSION,
                expectedVersion
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

            handler.postDelayed(
                    this::openNormalAndroidInstaller,
                    300L
            );

            return;
        }

        statusView.setText(
                "WACHTEN OP INSTALLATIE\n\n"
                        + "Rond alle Android- en Play Protect-bevestigingen af.\n\n"
                        + "MAATJE controleert zelf wanneer de nieuwe versie echt geïnstalleerd is."
        );

        startVersionPolling();
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

    private void openNormalAndroidInstaller() {
        String path =
                getIntent().getStringExtra(
                        EXTRA_APK_PATH
                );

        if (path == null
                || path.trim().isEmpty()) {
            statusView.setText(
                    "APK-pad ontbreekt."
            );
            return;
        }

        File apk =
                new File(path);

        if (!apk.isFile()) {
            statusView.setText(
                    "Update-APK bestaat niet meer."
            );
            return;
        }

        try {
            Uri uri =
                    FileProvider.getUriForFile(
                            this,
                            getPackageName()
                                    + ".files",
                            apk
                    );

            Intent install =
                    new Intent(
                            Intent.ACTION_VIEW
                    );

            install.setDataAndType(
                    uri,
                    "application/vnd.android.package-archive"
            );

            install.addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                            | Intent.FLAG_ACTIVITY_CLEAR_TOP
            );

            ComponentName resolved =
                    install.resolveActivity(
                            getPackageManager()
                    );

            String installerPackage =
                    resolved == null
                            ? null
                            : resolved.getPackageName();

            KioskPolicy.allowInstallerTemporarily(
                    this,
                    installerPackage
            );

            if (installerPackage != null) {
                try {
                    grantUriPermission(
                            installerPackage,
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                    );
                } catch (Exception ignored) {}
            }

            statusView.setText(
                    "Normale LineageOS-installer wordt geopend...\n\n"
                            + "Druk op Update en kies daarna eventueel "
                            + "\"Toch installeren\" bij de beveiligingswaarschuwing."
            );

            startActivity(
                    install
            );

        } catch (Exception e) {
            statusView.setText(
                    "Android-installer kon niet openen.\n\n"
                            + safeMessage(e)
            );
        }
    }

    private void startVersionPolling() {
        handler.removeCallbacksAndMessages(
                null
        );

        handler.post(
                new Runnable() {
                    @Override
                    public void run() {
                        if (isExpectedVersionInstalled()) {
                            statusView.setText(
                                    "UPDATE GELUKT\n\n"
                                            + "Nieuwe MAATJE-versie gedetecteerd. Kiosk wordt opnieuw vergrendeld."
                            );

                            handler.postDelayed(
                                    InstallerBridgeActivity.this
                                            ::finishSuccessfully,
                                    700L
                            );
                            return;
                        }

                        handler.postDelayed(
                                this,
                                1000L
                        );
                    }
                }
        );
    }

    private boolean isExpectedVersionInstalled() {
        String expected =
                getIntent().getStringExtra(
                        EXTRA_EXPECTED_VERSION
                );

        if (expected == null
                || expected.trim().isEmpty()) {
            return false;
        }

        try {
            PackageInfo info;

            if (Build.VERSION.SDK_INT >= 33) {
                info =
                        getPackageManager()
                                .getPackageInfo(
                                        KioskPolicy.MAATJE_PACKAGE,
                                        android.content.pm.PackageManager
                                                .PackageInfoFlags
                                                .of(0)
                                );
            } else {
                info =
                        getPackageManager()
                                .getPackageInfo(
                                        KioskPolicy.MAATJE_PACKAGE,
                                        0
                                );
            }

            return info != null
                    && expected.equals(
                    info.versionName
            );

        } catch (Exception ignored) {
            return false;
        }
    }

    private void finishSuccessfully() {
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

    private void restoreKioskManually() {
        handler.removeCallbacksAndMessages(
                null
        );

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
                "Normale Android-installatie voorbereiden..."
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
                "Gebruik deze knop alleen als je de Android-installatie bewust hebt geannuleerd."
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
