package nl.thommie.kiosk;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;

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

        context.sendBroadcast(update);

        if (status
                == PackageInstaller.STATUS_SUCCESS) {
            KioskPolicy.launchMaatje(
                    context
            );
        }
    }
}
