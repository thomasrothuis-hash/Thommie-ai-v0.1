package nl.thommie.ai;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.location.LocationManager;
import android.net.wifi.WifiManager;
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
            if (isStatusQuestion(q, "wifi")) {
                try {
                    WifiManager wifi =
                            (WifiManager)
                                    context.getSystemService(
                                            Context.WIFI_SERVICE
                                    );

                    boolean enabled =
                            wifi != null
                                    && wifi.isWifiEnabled();

                    return enabled
                            ? "Wi-Fi staat aan."
                            : "Wi-Fi staat uit.";
                } catch (Exception e) {
                    return "Ik kon de Wi-Fi-status niet uitlezen.";
                }
            }
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
            if (isStatusQuestion(q, "bluetooth")) {
                try {
                    BluetoothManager manager =
                            (BluetoothManager)
                                    context.getSystemService(
                                            Context.BLUETOOTH_SERVICE
                                    );

                    BluetoothAdapter adapter =
                            manager == null
                                    ? null
                                    : manager.getAdapter();

                    boolean enabled =
                            adapter != null
                                    && adapter.isEnabled();

                    return enabled
                            ? "Bluetooth staat aan."
                            : "Bluetooth staat uit.";
                } catch (Exception e) {
                    return "Ik kon de Bluetooth-status niet uitlezen.";
                }
            }
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
            if (isStatusQuestion(q, "locatie")
                    || isStatusQuestion(q, "gps")) {
                try {
                    LocationManager location =
                            (LocationManager)
                                    context.getSystemService(
                                            Context.LOCATION_SERVICE
                                    );

                    boolean enabled =
                            location != null
                                    && location.isLocationEnabled();

                    return enabled
                            ? "Locatie staat aan."
                            : "Locatie staat uit.";
                } catch (Exception e) {
                    return "Ik kon de locatiestatus niet uitlezen.";
                }
            }
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

        MaatjeMenuDialog.show(
                activity,
                "Toestel & Kiosk",
                "Directe bediening van de dedicated MAATJE-terminal.",
                MaatjeMenuDialog.item(
                        "QR",
                        "Updates via QR",
                        "MAATJE én Kiosk veilig bijwerken",
                        () -> openUpdateUnlock(
                                activity
                        )
                ),
                MaatjeMenuDialog.item(
                        "CLK",
                        "Timers & wekkers",
                        "Interne timers en alarmen",
                        () -> activity.startActivity(
                                new Intent(
                                        activity,
                                        MaatjeScheduleActivity.class
                                )
                        )
                ),
                MaatjeMenuDialog.item(
                        "DEV",
                        "Lokale toestelbediening",
                        "Zaklamp, volume, helderheid en apps",
                        () -> DeviceControl.showSettings(
                                activity
                        )
                ),
                MaatjeMenuDialog.item(
                        "WI+",
                        "Wi-Fi inschakelen",
                        "Zet de Wi-Fi-radio lokaal aan",
                        () -> {
                            sendCommand(
                                    activity,
                                    "wifi_on"
                            );
                            toast(
                                    activity,
                                    "Wi-Fi aan"
                            );
                        }
                ),
                MaatjeMenuDialog.item(
                        "WI−",
                        "Wi-Fi uitschakelen",
                        "Zet de Wi-Fi-radio lokaal uit",
                        () -> {
                            sendCommand(
                                    activity,
                                    "wifi_off"
                            );
                            toast(
                                    activity,
                                    "Wi-Fi uit"
                            );
                        }
                ),
                MaatjeMenuDialog.item(
                        "BT+",
                        "Bluetooth inschakelen",
                        "Bluetooth lokaal aanzetten",
                        () -> {
                            sendCommand(
                                    activity,
                                    "bluetooth_on"
                            );
                            toast(
                                    activity,
                                    "Bluetooth aan"
                            );
                        }
                ),
                MaatjeMenuDialog.item(
                        "BT−",
                        "Bluetooth uitschakelen",
                        "Bluetooth lokaal uitzetten",
                        () -> {
                            sendCommand(
                                    activity,
                                    "bluetooth_off"
                            );
                            toast(
                                    activity,
                                    "Bluetooth uit"
                            );
                        }
                ),
                MaatjeMenuDialog.item(
                        "LOC",
                        "Locatie aan",
                        "Locatieservices activeren",
                        () -> {
                            sendCommand(
                                    activity,
                                    "location_on"
                            );
                            toast(
                                    activity,
                                    "Locatie aan"
                            );
                        }
                ),
                MaatjeMenuDialog.item(
                        "OFF",
                        "Locatie uit",
                        "Locatieservices uitschakelen",
                        () -> {
                            sendCommand(
                                    activity,
                                    "location_off"
                            );
                            toast(
                                    activity,
                                    "Locatie uit"
                            );
                        }
                ),
                MaatjeMenuDialog.item(
                        "PIN",
                        "Kiosk-beheer",
                        "Beveiligd beheer, Android-instellingen en herstel",
                        () -> openAdminUnlock(
                                activity
                        )
                )
        );
    }

    private static boolean openUpdateUnlock(
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
            intent.putExtra(
                    "open_update",
                    true
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

    private static boolean isStatusQuestion(
            String q,
            String subject
    ) {
        return q.contains(subject + " status")
                || q.contains("status " + subject)
                || q.contains("staat " + subject)
                || q.contains("is " + subject)
                || q.contains(subject + " aan of uit")
                || q.contains(subject + " ingeschakeld");
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
