package nl.thommie.kiosk;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;
import android.view.Gravity;

public class InstallerBridgeActivity
        extends Activity {

    private static final String EXTRA_CONFIRMATION =
            "confirmation_intent";

    private boolean launched = false;
    private boolean returnedFromInstaller = false;
    private final Handler handler =
            new Handler(Looper.getMainLooper());

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

        TextView view =
                new TextView(this);

        view.setText(
                "MAATJE UPDATE\n\nAndroid-installatie wordt geopend..."
        );
        view.setTextColor(
                Color.rgb(54, 220, 104)
        );
        view.setBackgroundColor(
                Color.rgb(4, 8, 5)
        );
        view.setGravity(Gravity.CENTER);
        view.setTextSize(18f);

        setContentView(view);

        launched =
                savedInstanceState != null
                        && savedInstanceState.getBoolean(
                                "launched",
                                false
                        );
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
                finishSafely();
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

            handler.postDelayed(
                    () -> {
                        try {
                            startActivity(
                                    confirmation
                            );
                        } catch (Exception ignored) {
                            finishSafely();
                        }
                    },
                    250L
            );

            return;
        }

        if (!returnedFromInstaller) {
            returnedFromInstaller = true;

            handler.postDelayed(
                    this::finishSafely,
                    2500L
            );
        }
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

    private void finishSafely() {
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
}
