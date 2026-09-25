package nl.thommie.kiosk;

import android.Manifest;
import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.UserManager;
import android.provider.Settings;

final class KioskPolicy {

    static final String MAATJE_PACKAGE =
            "nl.thommie.ai";

    private KioskPolicy() {}

    static ComponentName admin(
            Context context
    ) {
        return new ComponentName(
                context,
                KioskAdminReceiver.class
        );
    }

    static DevicePolicyManager dpm(
            Context context
    ) {
        return (DevicePolicyManager)
                context.getSystemService(
                        Context.DEVICE_POLICY_SERVICE
                );
    }

    static boolean isDeviceOwner(
            Context context
    ) {
        DevicePolicyManager dpm =
                dpm(context);

        return dpm != null
                && dpm.isDeviceOwnerApp(
                        context.getPackageName()
                );
    }

    static void apply(
            Activity activity
    ) {
        DevicePolicyManager dpm =
                dpm(activity);

        if (dpm == null
                || !isDeviceOwner(activity)) {
            return;
        }

        ComponentName admin =
                admin(activity);

        try {
            dpm.setLockTaskPackages(
                    admin,
                    new String[]{
                            activity.getPackageName(),
                            MAATJE_PACKAGE
                    }
            );
        } catch (Exception ignored) {}

        if (Build.VERSION.SDK_INT >= 28) {
            try {
                dpm.setLockTaskFeatures(
                        admin,
                        DevicePolicyManager
                                .LOCK_TASK_FEATURE_NONE
                );
            } catch (Exception ignored) {}
        }

        try {
            dpm.setStatusBarDisabled(
                    admin,
                    true
            );
        } catch (Exception ignored) {}

        try {
            dpm.setKeyguardDisabled(
                    admin,
                    true
            );
        } catch (Exception ignored) {}

        try {
            dpm.addUserRestriction(
                    admin,
                    UserManager.DISALLOW_ADD_USER
            );
        } catch (Exception ignored) {}

        try {
            dpm.addUserRestriction(
                    admin,
                    UserManager.DISALLOW_GUEST
            );
        } catch (Exception ignored) {}

        setPersistentHome(
                activity,
                dpm,
                admin
        );

        grantMaatjePermissions(
                activity,
                dpm,
                admin
        );

        if (Build.VERSION.SDK_INT >= 31) {
            try {
                dpm.setPermissionGrantState(
                        admin,
                        activity.getPackageName(),
                        Manifest.permission.BLUETOOTH_CONNECT,
                        DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED
                );
            } catch (Exception ignored) {}
        }

        try {
            if (dpm.isLockTaskPermitted(
                    activity.getPackageName()
            )) {
                activity.startLockTask();
            }
        } catch (Exception ignored) {}
    }

    static void enterMaintenance(
            Activity activity
    ) {
        DevicePolicyManager dpm =
                dpm(activity);

        try {
            activity.stopLockTask();
        } catch (Exception ignored) {}

        if (dpm != null
                && isDeviceOwner(activity)) {
            try {
                dpm.setStatusBarDisabled(
                        admin(activity),
                        false
                );
            } catch (Exception ignored) {}
        }
    }

    static void leaveMaintenance(
            Activity activity
    ) {
        apply(activity);
    }

    static void openAndroidSettings(
            Activity activity
    ) {
        enterMaintenance(activity);

        Intent intent =
                new Intent(
                        Settings.ACTION_SETTINGS
                );

        intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
        );

        activity.startActivity(intent);
    }

    static boolean launchMaatje(
            Context context
    ) {
        Intent launch =
                context.getPackageManager()
                        .getLaunchIntentForPackage(
                                MAATJE_PACKAGE
                        );

        if (launch == null) {
            return false;
        }

        launch.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP
        );

        context.startActivity(launch);
        return true;
    }

    private static void setPersistentHome(
            Context context,
            DevicePolicyManager dpm,
            ComponentName admin
    ) {
        try {
            IntentFilter filter =
                    new IntentFilter(
                            Intent.ACTION_MAIN
                    );

            filter.addCategory(
                    Intent.CATEGORY_HOME
            );
            filter.addCategory(
                    Intent.CATEGORY_DEFAULT
            );

            dpm.addPersistentPreferredActivity(
                    admin,
                    filter,
                    new ComponentName(
                            context,
                            KioskActivity.class
                    )
            );
        } catch (Exception ignored) {}
    }

    private static void grantMaatjePermissions(
            Context context,
            DevicePolicyManager dpm,
            ComponentName admin
    ) {
        grant(
                context,
                dpm,
                admin,
                Manifest.permission.CAMERA
        );

        grant(
                context,
                dpm,
                admin,
                Manifest.permission.RECORD_AUDIO
        );

        if (Build.VERSION.SDK_INT >= 33) {
            grant(
                    context,
                    dpm,
                    admin,
                    Manifest.permission.POST_NOTIFICATIONS
            );
        }

        grant(
                context,
                dpm,
                admin,
                Manifest.permission.ACCESS_FINE_LOCATION
        );

        grant(
                context,
                dpm,
                admin,
                Manifest.permission.ACCESS_COARSE_LOCATION
        );
    }

    private static void grant(
            Context context,
            DevicePolicyManager dpm,
            ComponentName admin,
            String permission
    ) {
        try {
            PackageManager pm =
                    context.getPackageManager();

            pm.getPackageInfo(
                    MAATJE_PACKAGE,
                    PackageManager
                            .GET_PERMISSIONS
            );

            dpm.setPermissionGrantState(
                    admin,
                    MAATJE_PACKAGE,
                    permission,
                    DevicePolicyManager
                            .PERMISSION_GRANT_STATE_GRANTED
            );

        } catch (Exception ignored) {}
    }
}
