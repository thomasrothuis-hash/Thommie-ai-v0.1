package nl.thommie.kiosk;

import android.app.admin.DevicePolicyManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Build;

public class PrivilegedControlReceiver
        extends BroadcastReceiver {

    static final String ACTION =
            "nl.thommie.kiosk.CONTROL";

    @Override
    public void onReceive(
            Context context,
            Intent intent
    ) {
        if (intent == null
                || !ACTION.equals(
                        intent.getAction()
                )
                || !KioskPolicy
                .isDeviceOwner(context)) {
            return;
        }

        String command =
                intent.getStringExtra(
                        "command"
                );

        if (command == null) {
            return;
        }

        switch (command) {
            case "wifi_on":
                setWifi(
                        context,
                        true
                );
                break;

            case "wifi_off":
                setWifi(
                        context,
                        false
                );
                break;

            case "bluetooth_on":
                setBluetooth(
                        context,
                        true
                );
                break;

            case "bluetooth_off":
                setBluetooth(
                        context,
                        false
                );
                break;

            case "location_on":
                setLocation(
                        context,
                        true
                );
                break;

            case "location_off":
                setLocation(
                        context,
                        false
                );
                break;

            default:
                break;
        }
    }

    @SuppressWarnings("deprecation")
    private void setWifi(
            Context context,
            boolean enabled
    ) {
        try {
            WifiManager wifi =
                    (WifiManager)
                            context.getApplicationContext()
                                    .getSystemService(
                                            Context.WIFI_SERVICE
                                    );

            if (wifi != null) {
                wifi.setWifiEnabled(
                        enabled
                );
            }
        } catch (Exception ignored) {}
    }

    @SuppressWarnings("deprecation")
    private void setBluetooth(
            Context context,
            boolean enabled
    ) {
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

            if (adapter == null) {
                return;
            }

            if (enabled) {
                adapter.enable();
            } else {
                adapter.disable();
            }

        } catch (Exception ignored) {}
    }

    private void setLocation(
            Context context,
            boolean enabled
    ) {
        if (Build.VERSION.SDK_INT < 30) {
            return;
        }

        try {
            DevicePolicyManager dpm =
                    KioskPolicy.dpm(
                            context
                    );

            ComponentName admin =
                    KioskPolicy.admin(
                            context
                    );

            if (dpm != null) {
                dpm.setLocationEnabled(
                        admin,
                        enabled
                );
            }

        } catch (Exception ignored) {}
    }
}
