package nl.thommie.ai;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.widget.Toast;

import java.util.Locale;

final class KioskBridge {

    private static final String KIOSK_PACKAGE =
            "nl.thommie.kiosk";

    private static final String CONTROL_ACTION =
            "nl.thommie.kiosk.CONTROL";

    private static final ComponentName CONTROL_RECEIVER =
            new ComponentName(
                    KIOSK_PACKAGE,
                    "nl.thommie.kiosk.PrivilegedControlReceiver"
            );

    private static final ComponentName ADMIN_UNLOCK =
            new ComponentName(
                    KIOSK_PACKAGE,
                    "nl.thommie.kiosk.AdminUnlockActivity"
            );

    private KioskBridge() {}

    static boolean isAvailable(
            Context context
    ) {
        try {
            context.getPackageManager()
                    .getPackageInfo(
                            KIOSK_PACKAGE,
                            0
                    );
            return true;
        } catch (
                PackageManager
                        .NameNotFoundException e
        ) {
            return false;
        }
    }

    static String handleVoiceCommand(
            Context context,
            String raw
    ) {
        if (!isAvailable(context)
                || raw == null) {
            return null;
        }

        String q =
                raw.toLowerCase(
                        Locale.ROOT
                );

        if (q.contains("wifi")
                && !q.contains("open")
                && !q.contains("instelling")) {
            if (containsOn(q)) {
                sendCommand(
                        context,
                        "wifi_on"
                );
                return "Wi-Fi aangezet.";
            }

            if (containsOff(q)) {
                sendCommand(
                        context,
                        "wifi_off"
                );
                return "Wi-Fi uitgezet.";
            }
        }

        if (q.contains("bluetooth")
                && !q.contains("open")
                && !q.contains("instelling")) {
            if (containsOn(q)) {
                sendCommand(
                        context,
                        "bluetooth_on"
                );
                return "Bluetooth aangezet.";
            }

            if (containsOff(q)) {
                sendCommand(
                        context,
                        "bluetooth_off"
                );
                return "Bluetooth uitgezet.";
            }
        }

        if ((q.contains("locatie")
                || q.contains("gps"))
                && !q.contains("open")
                && !q.contains("instelling")) {
            if (containsOn(q)) {
                sendCommand(
                        context,
                        "location_on"
                );
                return "Locatie aangezet.";
            }

            if (containsOff(q)) {
                sendCommand(
                        context,
                        "location_off"
                );
                return "Locatie uitgezet.";
            }
        }

        return null;
    }

    static void showDevicePanel(
            Activity activity
    ) {
        if (!isAvailable(activity)) {
            Toast.makeText(
                    activity,
                    "MAATJE Kiosk is niet geïnstalleerd.",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        String[] items = {
                "Wi-Fi aan",
                "Wi-Fi uit",
                "Bluetooth aan",
                "Bluetooth uit",
                "Locatie aan",
                "Locatie uit",
                "Kiosk-beheer openen"
        };

        new AlertDialog.Builder(activity)
                .setTitle(
                        "MAATJE • Toestelcontrole"
                )
                .setItems(
                        items,
                        (dialog, which) -> {
                            switch (which) {
                                case 0:
                                    sendCommand(
                                            activity,
                                            "wifi_on"
                                    );
                                    toast(
                                            activity,
                                            "Wi-Fi aan"
                                    );
                                    break;

                                case 1:
                                    sendCommand(
                                            activity,
                                            "wifi_off"
                                    );
                                    toast(
                                            activity,
                                            "Wi-Fi uit"
                                    );
                                    break;

                                case 2:
                                    sendCommand(
                                            activity,
                                            "bluetooth_on"
                                    );
                                    toast(
                                            activity,
                                            "Bluetooth aan"
                                    );
                                    break;

                                case 3:
                                    sendCommand(
                                            activity,
                                            "bluetooth_off"
                                    );
                                    toast(
                                            activity,
                                            "Bluetooth uit"
                                    );
                                    break;

                                case 4:
                                    sendCommand(
                                            activity,
                                            "location_on"
                                    );
                                    toast(
                                            activity,
                                            "Locatie aan"
                                    );
                                    break;

                                case 5:
                                    sendCommand(
                                            activity,
                                            "location_off"
                                    );
                                    toast(
                                            activity,
                                            "Locatie uit"
                                    );
                                    break;

                                case 6:
                                    openAdminUnlock(
                                            activity
                                    );
                                    break;

                                default:
                                    break;
                            }
                        }
                )
                .setNegativeButton(
                        "Sluiten",
                        null
                )
                .show();
    }

    static boolean openAdminUnlock(
            Context context
    ) {
        if (!isAvailable(context)) {
            return false;
        }

        try {
            Intent intent =
                    new Intent();

            intent.setComponent(
                    ADMIN_UNLOCK
            );

            if (!(context instanceof Activity)) {
                intent.addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK
                );
            }

            context.startActivity(
                    intent
            );

            return true;

        } catch (Exception e) {
            return false;
        }
    }

    private static void sendCommand(
            Context context,
            String command
    ) {
        Intent intent =
                new Intent(
                        CONTROL_ACTION
                );

        intent.setComponent(
                CONTROL_RECEIVER
        );

        intent.putExtra(
                "command",
                command
        );

        context.sendBroadcast(
                intent
        );
    }

    private static boolean containsOn(
            String q
    ) {
        return q.contains(" aan")
                || q.endsWith("aan")
                || q.contains("inschakel")
                || q.contains("zet aan");
    }

    private static boolean containsOff(
            String q
    ) {
        return q.contains(" uit")
                || q.endsWith("uit")
                || q.contains("uitschakel")
                || q.contains("zet uit");
    }

    private static void toast(
            Context context,
            String text
    ) {
        Toast.makeText(
                context,
                text,
                Toast.LENGTH_SHORT
        ).show();
    }
}
