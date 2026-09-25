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
                        PackageInstaller.EXTRA_STATUS,
                        PackageInstaller.STATUS_FAILURE
                );

        String detail =
                intent.getStringExtra(
                        PackageInstaller.EXTRA_STATUS_MESSAGE
                );

        if (status
                == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            handlePendingUserAction(
                    context,
                    intent
            );
            return;
        }

        KioskPolicy.restoreLockTaskPackages(
                context
        );

        String message;

        if (status
                == PackageInstaller.STATUS_SUCCESS) {
            message =
                    "MAATJE update geïnstalleerd.";
        } else {
            message =
                    "Installatie mislukt"
                            + (
                            detail == null
                                    ? "."
                                    : ": " + detail
                    );
        }

        broadcastStatus(
                context,
                status,
                message
        );

        openKioskHome(
                context
        );
    }

    private void handlePendingUserAction(
            Context context,
            Intent resultIntent
    ) {
        Intent confirmation;

        if (Build.VERSION.SDK_INT >= 33) {
            confirmation =
                    resultIntent.getParcelableExtra(
                            Intent.EXTRA_INTENT,
                            Intent.class
                    );
        } else {
            confirmation =
                    resultIntent.getParcelableExtra(
                            Intent.EXTRA_INTENT
                    );
        }

        if (confirmation == null) {
            broadcastStatus(
                    context,
                    PackageInstaller.STATUS_FAILURE,
                    "Android vroeg om installatiebevestiging, maar leverde geen installatiescherm."
            );
            openKioskHome(
                    context
            );
            return;
        }

        broadcastStatus(
                context,
                PackageInstaller.STATUS_PENDING_USER_ACTION,
                "Installatiemodus geopend. Bevestig de update in LineageOS."
        );

        confirmation.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
        );

        try {
            context.startActivity(
                    confirmation
            );
        } catch (Exception e) {
            KioskPolicy.restoreLockTaskPackages(
                    context
            );

            broadcastStatus(
                    context,
                    PackageInstaller.STATUS_FAILURE,
                    "Installatiemodus kon niet openen: "
                            + safeMessage(e)
            );

            openKioskHome(
                    context
            );
        }
    }

    private void broadcastStatus(
            Context context,
            int status,
            String message
    ) {
        Intent update =
                new Intent(
                        ApkInstaller.ACTION_INSTALL_STATUS
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

        context.sendBroadcast(
                update
        );
    }

    private void openKioskHome(
            Context context
    ) {
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
            context.startActivity(
                    home
            );
        } catch (Exception ignored) {}
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
}
