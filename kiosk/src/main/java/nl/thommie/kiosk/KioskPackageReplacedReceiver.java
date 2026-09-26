package nl.thommie.kiosk;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class KioskPackageReplacedReceiver
        extends BroadcastReceiver {

    @Override
    public void onReceive(
            Context context,
            Intent intent
    ) {
        if (intent == null
                || !Intent.ACTION_MY_PACKAGE_REPLACED.equals(
                        intent.getAction()
                )) {
            return;
        }

        KioskPolicy.restoreLockTaskPackages(
                context
        );

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
}
