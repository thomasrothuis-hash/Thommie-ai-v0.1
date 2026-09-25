package nl.thommie.kiosk;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Build;

public class InstallResultReceiver
        extends BroadcastReceiver {

    @Override
    public void onReceive(
            Context context,
            Intent intent
    ) {
        int status =
                intent.getIntExtra(
                        PackageInstaller
                                .EXTRA_STATUS,
                        PackageInstaller
                                .STATUS_FAILURE
                );

        String detail =
                intent.getStringExtra(
                        PackageInstaller
                                .EXTRA_STATUS_MESSAGE
                );

        String message;

        if (status
                == PackageInstaller.STATUS_SUCCESS) {
            message =
                    "MAATJE update geïnstalleerd.";
        } else if (status
                == PackageInstaller
                .STATUS_PENDING_USER_ACTION) {
            message =
                    "Installatie wacht op gebruikersactie.";
        } else {
            message =
                    "Installatie mislukt"
                            + (
                            detail == null
                                    ? "."
                                    : ": " + detail
                    );
        }

        Intent update =
                new Intent(
                        ApkInstaller
                                .ACTION_INSTALL_STATUS
                );

        update.setPackage(
                context.getPackageName()
        );
        update.putExtra(
                "status",
                status
        );
        update.putExtra(
                "message",
                message
        );

        if (status
                == PackageInstaller
                .STATUS_PENDING_USER_ACTION) {
            Intent confirmation;

            if (Build.VERSION.SDK_INT >= 33) {
                confirmation =
                        intent.getParcelableExtra(
                                Intent.EXTRA_INTENT,
                                Intent.class
                        );
            } else {
                confirmation =
                        intent.getParcelableExtra(
                                Intent.EXTRA_INTENT
                        );
            }

            if (confirmation != null) {
                update.putExtra(
                        "confirmation_intent",
                        confirmation
                );
            }
        }

        context.sendBroadcast(update);

        if (status
                == PackageInstaller.STATUS_SUCCESS) {
            Intent home =
                    new Intent(
                            context,
                            KioskActivity.class
                    );

            home.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_CLEAR_TOP
                            | Intent.FLAG_ACTIVITY_SINGLE_TOP
            );

            try {
                context.startActivity(home);
            } catch (Exception ignored) {}
        }
    }
}
