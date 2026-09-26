package nl.thommie.ai;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.provider.AlarmClock;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.provider.MediaStore;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class DeviceControl {

    static final int REQ_CAMERA = 2401;

    private static final String PREFS =
            "maatje_device_control";
    private static final String KEY_TORCH_STATE =
            "torch_state";
    private static final String KEY_TORCH_CAMERA_ID =
            "torch_camera_id";

    static final class CommandResult {
        final boolean handled;
        final String message;
        final String state;

        CommandResult(
                boolean handled,
                String message,
                String state
        ) {
            this.handled = handled;
            this.message = message;
            this.state = state;
        }

        static CommandResult no() {
            return new CommandResult(
                    false,
                    "",
                    ""
            );
        }
    }

    private DeviceControl() {}

    static CommandResult handleCommand(
            Activity activity,
            String raw
    ) {
        if (raw == null
                || raw.trim().isEmpty()) {
            return CommandResult.no();
        }

        String q = normalize(raw);

        String kioskResult =
                KioskBridge.handleVoiceCommand(
                        activity,
                        raw
                );

        if (kioskResult != null) {
            return new CommandResult(
                    true,
                    kioskResult,
                    "DEVICE • KIOSK"
            );
        }

        CommandResult timer =
                handleTimer(activity, raw, q);
        if (timer.handled) {
            return timer;
        }

        CommandResult alarm =
                handleAlarm(activity, raw, q);
        if (alarm.handled) {
            return alarm;
        }

        CommandResult torch =
                handleTorch(
                        activity,
                        q
                );

        if (torch.handled) {
            return torch;
        }

        CommandResult volume =
                handleVolume(
                        activity,
                        q
                );

        if (volume.handled) {
            return volume;
        }

        CommandResult brightness =
                handleBrightness(
                        activity,
                        q
                );

        if (brightness.handled) {
            return brightness;
        }

        CommandResult battery =
                handleBattery(
                        activity,
                        q
                );

        if (battery.handled) {
            return battery;
        }

        CommandResult directOpen =
                handleDirectOpen(
                        activity,
                        q
                );

        if (directOpen.handled) {
            return directOpen;
        }

        CommandResult info =
                handleDeviceInfo(
                        q
                );

        if (info.handled) {
            return info;
        }

        CommandResult app =
                handleOpenApp(
                        activity,
                        raw,
                        q
                );

        if (app.handled) {
            return app;
        }

        return CommandResult.no();
    }

    static void showSettings(
            Activity activity
    ) {
        boolean cameraGranted =
                activity.checkSelfPermission(
                        Manifest.permission.CAMERA
                )
                == PackageManager.PERMISSION_GRANTED;

        boolean brightnessGranted =
                Settings.System.canWrite(
                        activity
                );

        String message =
                "Lokale toestelbediening in MAATJE:\n\n"
                        + "• Interne MAATJE timers en wekkers (OnePlus-safe)\n"
                        + "• Multi-camera zaklamp (OnePlus-safe)\n"
                        + "• Media-volume\n"
                        + "• Schermhelderheid\n"
                        + "• Batterijstatus\n"
                        + "• Camera openen\n"
                        + "• Apps openen\n"
                        + "• Wi-Fi/Bluetooth-instellingen\n"
                        + "• Toestelinfo\n\n"
                        + "Zaklamp: "
                        + (cameraGranted
                        ? "toegang OK"
                        : "toestemming nodig")
                        + "\nHelderheid: "
                        + (brightnessGranted
                        ? "toegang OK"
                        : "toestemming nodig");

        AlertDialog dialog =
                new AlertDialog.Builder(activity)
                        .setTitle(
                                "MAATJE v1.3.4 ONEPLUS – Toestelbediening"
                        )
                        .setMessage(message)
                        .setPositiveButton(
                                cameraGranted
                                        ? "Zaklamp OK"
                                        : "Zaklamp toegang",
                                null
                        )
                        .setNeutralButton(
                                brightnessGranted
                                        ? "Helderheid OK"
                                        : "Helderheid toegang",
                                null
                        )
                        .setNegativeButton(
                                "Sluiten",
                                null
                        )
                        .create();

        dialog.setOnShowListener(d -> {
            dialog.getButton(
                    AlertDialog.BUTTON_POSITIVE
            ).setOnClickListener(v -> {
                if (!cameraGranted) {
                    activity.requestPermissions(
                            new String[]{
                                    Manifest.permission.CAMERA
                            },
                            REQ_CAMERA
                    );
                }
            });

            dialog.getButton(
                    AlertDialog.BUTTON_NEUTRAL
            ).setOnClickListener(v -> {
                if (!Settings.System.canWrite(activity)) {
                    requestWriteSettings(
                            activity
                    );
                }
            });
        });

        dialog.show();
    }

    private static CommandResult handleTimer(
            Activity activity,
            String raw,
            String q
    ) {
        if (!q.contains("timer")) {
            return CommandResult.no();
        }

        if (q.contains("open")
                || q.contains("toon")
                || q.contains("bekijk")
                || q.contains("lijst")) {
            try {
                activity.startActivity(
                        new Intent(
                                activity,
                                MaatjeScheduleActivity.class
                        )
                );

                return new CommandResult(
                        true,
                        "Timers geopend.",
                        "DEVICE • TIMERS"
                );
            } catch (Exception e) {
                return new CommandResult(
                        true,
                        "Ik kon het timer-overzicht niet openen.",
                        "DEVICE • TIMER ERROR"
                );
            }
        }

        if (q.contains("annuleer")
                || q.contains("annuleren")
                || q.contains("stop timer")
                || q.contains("stop de timer")
                || q.contains("verwijder")) {
            int cancelled =
                    MaatjeScheduler.cancelType(
                            activity,
                            MaatjeScheduler.TYPE_TIMER
                    );

            return new CommandResult(
                    true,
                    cancelled == 0
                            ? "Er stonden geen actieve timers."
                            : (
                                    cancelled == 1
                                            ? "Timer geannuleerd."
                                            : cancelled
                                            + " timers geannuleerd."
                            ),
                    "DEVICE • TIMER CANCEL"
            );
        }

        int seconds = parseDurationSeconds(q);

        if (seconds <= 0) {
            return new CommandResult(
                    true,
                    "Zeg bijvoorbeeld: zet een timer van 10 minuten.",
                    "DEVICE • TIMER"
            );
        }

        String label =
                extractTimerLabel(raw);

        if (label.isEmpty()) {
            label = "MAATJE timer";
        }

        try {
            MaatjeScheduler.scheduleTimer(
                    activity,
                    seconds,
                    label
            );

            return new CommandResult(
                    true,
                    "Timer gezet voor "
                            + formatDuration(seconds)
                            + ".",
                    "DEVICE • TIMER SET"
            );

        } catch (Exception e) {
            return new CommandResult(
                    true,
                    "Timer instellen lukte niet: "
                            + safeMessage(e),
                    "DEVICE • TIMER ERROR"
            );
        }
    }

    private static CommandResult handleAlarm(
            Activity activity,
            String raw,
            String q
    ) {
        if (!q.contains("wekker")
                && !q.contains("alarm")) {
            return CommandResult.no();
        }

        if (q.contains("open")
                || q.contains("toon")
                || q.contains("bekijk")
                || q.contains("lijst")) {
            try {
                activity.startActivity(
                        new Intent(
                                activity,
                                MaatjeScheduleActivity.class
                        )
                );

                return new CommandResult(
                        true,
                        "Wekkers geopend.",
                        "DEVICE • ALARMS"
                );
            } catch (Exception e) {
                return new CommandResult(
                        true,
                        "Ik kon het wekker-overzicht niet openen.",
                        "DEVICE • ALARM ERROR"
                );
            }
        }

        if (q.contains("annuleer")
                || q.contains("annuleren")
                || q.contains("stop wekker")
                || q.contains("stop alarm")
                || q.contains("verwijder")) {
            int cancelled =
                    MaatjeScheduler.cancelType(
                            activity,
                            MaatjeScheduler.TYPE_ALARM
                    );

            return new CommandResult(
                    true,
                    cancelled == 0
                            ? "Er stonden geen actieve wekkers."
                            : (
                                    cancelled == 1
                                            ? "Wekker geannuleerd."
                                            : cancelled
                                            + " wekkers geannuleerd."
                            ),
                    "DEVICE • ALARM CANCEL"
            );
        }

        int[] time =
                parseAlarmTime(q);

        if (time == null) {
            return new CommandResult(
                    true,
                    "Zeg bijvoorbeeld: zet een wekker om 07:30.",
                    "DEVICE • ALARM"
            );
        }

        String label =
                extractAlarmLabel(raw);

        if (label.isEmpty()) {
            label = "MAATJE wekker";
        }

        try {
            MaatjeScheduler.scheduleAlarm(
                    activity,
                    time[0],
                    time[1],
                    q.contains("morgen"),
                    label
            );

            return new CommandResult(
                    true,
                    String.format(
                            Locale.ROOT,
                            "Wekker gezet om %02d:%02d.",
                            time[0],
                            time[1]
                    ),
                    "DEVICE • ALARM SET"
            );

        } catch (Exception e) {
            return new CommandResult(
                    true,
                    "Wekker instellen lukte niet: "
                            + safeMessage(e),
                    "DEVICE • ALARM ERROR"
            );
        }
    }

    private static CommandResult handleTorch(
            Activity activity,
            String q
    ) {
        boolean mentionsTorch =
                q.contains("zaklamp")
                        || q.contains("flashlight")
                        || q.contains("flitser");

        if (!mentionsTorch) {
            return CommandResult.no();
        }

        if (activity.checkSelfPermission(
                Manifest.permission.CAMERA
        ) != PackageManager.PERMISSION_GRANTED) {
            activity.requestPermissions(
                    new String[]{
                            Manifest.permission.CAMERA
                    },
                    REQ_CAMERA
            );

            return new CommandResult(
                    true,
                    "Ik heb éénmalig cameratoestemming nodig voor de zaklamp. Geef die toestemming en probeer het daarna nog een keer.",
                    "DEVICE • PERMISSION"
            );
        }

        boolean current =
                prefs(activity)
                        .getBoolean(
                                KEY_TORCH_STATE,
                                false
                        );

        boolean turnOn;

        if (containsOff(q)) {
            turnOn = false;
        } else if (containsOn(q)) {
            turnOn = true;
        } else {
            turnOn = !current;
        }

        try {
            CameraManager manager =
                    (CameraManager) activity
                            .getSystemService(
                                    Context.CAMERA_SERVICE
                            );

            if (manager == null) {
                throw new Exception(
                        "CameraManager niet beschikbaar."
                );
            }

            List<String> candidates =
                    findFlashCameras(
                            manager
                    );

            if (candidates.isEmpty()) {
                return new CommandResult(
                        true,
                        "Ik kan geen bruikbare flitser op dit toestel vinden.",
                        "DEVICE • TORCH ERROR"
                );
            }

            String remembered =
                    prefs(activity)
                            .getString(
                                    KEY_TORCH_CAMERA_ID,
                                    ""
                            );

            if (!remembered.isEmpty()
                    && candidates.remove(
                    remembered
            )) {
                candidates.add(
                        0,
                        remembered
                );
            }

            if (!turnOn) {
                int disabled = 0;
                Exception lastError = null;

                for (String id : candidates) {
                    try {
                        manager.setTorchMode(
                                id,
                                false
                        );
                        disabled++;
                    } catch (Exception e) {
                        lastError = e;
                    }
                }

                prefs(activity)
                        .edit()
                        .putBoolean(
                                KEY_TORCH_STATE,
                                false
                        )
                        .apply();

                if (disabled == 0
                        && lastError != null) {
                    throw lastError;
                }

                return new CommandResult(
                        true,
                        "Zaklamp uit.",
                        "DEVICE • TORCH OFF"
                );
            }

            Exception lastError = null;

            for (String id : candidates) {
                try {
                    manager.setTorchMode(
                            id,
                            true
                    );

                    prefs(activity)
                            .edit()
                            .putBoolean(
                                    KEY_TORCH_STATE,
                                    true
                            )
                            .putString(
                                    KEY_TORCH_CAMERA_ID,
                                    id
                            )
                            .apply();

                    return new CommandResult(
                            true,
                            "Zaklamp aan.",
                            "DEVICE • TORCH ON"
                    );

                } catch (Exception e) {
                    lastError = e;
                }
            }

            throw lastError == null
                    ? new Exception(
                    "Geen torch-camera accepteerde de opdracht."
            )
                    : lastError;

        } catch (Exception e) {
            return new CommandResult(
                    true,
                    "Zaklamp bedienen lukte niet: "
                            + safeMessage(e)
                            + ". Sluit eventueel live camera en probeer opnieuw.",
                    "DEVICE • TORCH ERROR"
            );
        }
    }

    private static List<String> findFlashCameras(
            CameraManager manager
    ) throws Exception {
        List<String> back =
                new ArrayList<>();
        List<String> other =
                new ArrayList<>();

        for (String id :
                manager.getCameraIdList()) {
            CameraCharacteristics c =
                    manager.getCameraCharacteristics(
                            id
                    );

            Boolean flash =
                    c.get(
                            CameraCharacteristics
                                    .FLASH_INFO_AVAILABLE
                    );

            if (!Boolean.TRUE.equals(flash)) {
                continue;
            }

            Integer facing =
                    c.get(
                            CameraCharacteristics
                                    .LENS_FACING
                    );

            if (facing != null
                    && facing
                    == CameraCharacteristics
                    .LENS_FACING_BACK) {
                back.add(id);
            } else {
                other.add(id);
            }
        }

        back.addAll(other);
        return back;
    }

    private static CommandResult handleVolume(
            Activity activity,
            String q
    ) {
        boolean mentionsVolume =
                q.contains("volume")
                        || q.contains("geluid harder")
                        || q.contains("geluid zachter")
                        || q.contains("geluid op")
                        || q.equals("geluid uit")
                        || q.equals("geluid aan");

        if (!mentionsVolume) {
            return CommandResult.no();
        }

        AudioManager audio =
                (AudioManager) activity
                        .getSystemService(
                                Context.AUDIO_SERVICE
                        );

        int max =
                audio.getStreamMaxVolume(
                        AudioManager.STREAM_MUSIC
                );

        int min =
                Build.VERSION.SDK_INT
                        >= Build.VERSION_CODES.P
                        ? audio.getStreamMinVolume(
                                AudioManager.STREAM_MUSIC
                        )
                        : 0;

        int current =
                audio.getStreamVolume(
                        AudioManager.STREAM_MUSIC
                );

        int currentPercent =
                percent(
                        current,
                        min,
                        max
                );

        Integer requested =
                extractPercent(q);

        if (requested == null) {
            if (q.contains("max")
                    || q.contains("maximum")
                    || q.contains("voluit")) {
                requested = 100;
            } else if (q.contains("mute")
                    || q.contains("stil")
                    || q.contains("geluid uit")
                    || q.contains("volume uit")) {
                requested = 0;
            } else if (q.contains("harder")
                    || q.contains("omhoog")) {
                requested =
                        Math.min(
                                100,
                                currentPercent + 15
                        );
            } else if (q.contains("zachter")
                    || q.contains("omlaag")) {
                requested =
                        Math.max(
                                0,
                                currentPercent - 15
                        );
            }
        }

        if (requested == null) {
            return new CommandResult(
                    true,
                    "Het mediavolume staat op "
                            + currentPercent
                            + " procent.",
                    "DEVICE • VOLUME "
                            + currentPercent
                            + "%"
            );
        }

        int value =
                levelForPercent(
                        requested,
                        min,
                        max
                );

        try {
            audio.setStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    value,
                    0
            );

            return new CommandResult(
                    true,
                    "Volume op "
                            + requested
                            + " procent.",
                    "DEVICE • VOLUME "
                            + requested
                            + "%"
            );

        } catch (Exception e) {
            return new CommandResult(
                    true,
                    "Volume aanpassen lukte niet: "
                            + safeMessage(e),
                    "DEVICE • VOLUME ERROR"
            );
        }
    }

    private static CommandResult handleBrightness(
            Activity activity,
            String q
    ) {
        boolean mentionsBrightness =
                q.contains("helderheid")
                        || q.contains("brightness")
                        || q.contains("scherm lichter")
                        || q.contains("scherm donkerder");

        if (!mentionsBrightness) {
            return CommandResult.no();
        }

        int current =
                Settings.System.getInt(
                        activity.getContentResolver(),
                        Settings.System.SCREEN_BRIGHTNESS,
                        128
                );

        int currentPercent =
                Math.max(
                        0,
                        Math.min(
                                100,
                                Math.round(
                                        current
                                                * 100f
                                                / 255f
                                )
                        )
                );

        Integer requested =
                extractPercent(q);

        if (requested == null) {
            if (q.contains("max")
                    || q.contains("maximum")
                    || q.contains("voluit")) {
                requested = 100;
            } else if (q.contains("lichter")
                    || q.contains("omhoog")) {
                requested =
                        Math.min(
                                100,
                                currentPercent + 15
                        );
            } else if (q.contains("donkerder")
                    || q.contains("omlaag")) {
                requested =
                        Math.max(
                                1,
                                currentPercent - 15
                        );
            }
        }

        if (requested == null) {
            return new CommandResult(
                    true,
                    "De schermhelderheid staat ongeveer op "
                            + currentPercent
                            + " procent.",
                    "DEVICE • BRIGHTNESS "
                            + currentPercent
                            + "%"
            );
        }

        if (!Settings.System.canWrite(activity)) {
            requestWriteSettings(
                    activity
            );

            return new CommandResult(
                    true,
                    "Voor systeembrede helderheid heb ik éénmalig toestemming nodig om systeeminstellingen te wijzigen. Die pagina heb ik nu geopend.",
                    "DEVICE • PERMISSION"
            );
        }

        int value =
                Math.round(
                        requested
                                * 255f
                                / 100f
                );

        try {
            Settings.System.putInt(
                    activity.getContentResolver(),
                    Settings.System
                            .SCREEN_BRIGHTNESS_MODE,
                    Settings.System
                            .SCREEN_BRIGHTNESS_MODE_MANUAL
            );

            boolean ok =
                    Settings.System.putInt(
                            activity.getContentResolver(),
                            Settings.System.SCREEN_BRIGHTNESS,
                            value
                    );

            if (!ok) {
                throw new IllegalStateException(
                        "Android weigerde de instelling."
                );
            }

            return new CommandResult(
                    true,
                    "Helderheid op "
                            + requested
                            + " procent.",
                    "DEVICE • BRIGHTNESS "
                            + requested
                            + "%"
            );

        } catch (Exception e) {
            return new CommandResult(
                    true,
                    "Helderheid aanpassen lukte niet: "
                            + safeMessage(e),
                    "DEVICE • BRIGHTNESS ERROR"
            );
        }
    }

    private static void requestWriteSettings(
            Activity activity
    ) {
        try {
            Intent intent =
                    new Intent(
                            Settings
                                    .ACTION_MANAGE_WRITE_SETTINGS
                    );

            intent.setData(
                    Uri.parse(
                            "package:"
                                    + activity
                                    .getPackageName()
                    )
            );

            activity.startActivity(intent);

        } catch (Exception e) {
            activity.startActivity(
                    new Intent(
                            Settings
                                    .ACTION_SETTINGS
                    )
            );
        }
    }

    private static CommandResult handleBattery(
            Activity activity,
            String q
    ) {
        boolean mentionsBattery =
                q.contains("batterij")
                        || q.contains("accu");

        if (!mentionsBattery) {
            return CommandResult.no();
        }

        BatteryManager manager =
                (BatteryManager) activity
                        .getSystemService(
                                Context.BATTERY_SERVICE
                        );

        int capacity =
                manager.getIntProperty(
                        BatteryManager
                                .BATTERY_PROPERTY_CAPACITY
                );

        Intent status =
                activity.registerReceiver(
                        null,
                        new IntentFilter(
                                Intent.ACTION_BATTERY_CHANGED
                        )
                );

        boolean charging = false;
        float temperature = -1f;

        if (status != null) {
            int state =
                    status.getIntExtra(
                            BatteryManager
                                    .EXTRA_STATUS,
                            -1
                    );

            charging =
                    state
                            == BatteryManager
                            .BATTERY_STATUS_CHARGING
                            || state
                            == BatteryManager
                            .BATTERY_STATUS_FULL;

            int temp =
                    status.getIntExtra(
                            BatteryManager
                                    .EXTRA_TEMPERATURE,
                            -1
                    );

            if (temp >= 0) {
                temperature =
                        temp / 10f;
            }
        }

        StringBuilder answer =
                new StringBuilder();

        answer.append(
                "Batterij "
        )
                .append(capacity)
                .append(" procent");

        answer.append(
                charging
                        ? ", wordt opgeladen"
                        : ", niet aan het opladen"
        );

        if (temperature >= 0f) {
            answer.append(", ")
                    .append(
                            String.format(
                                    Locale.ROOT,
                                    "%.1f",
                                    temperature
                            )
                    )
                    .append(" graden");
        }

        answer.append(".");

        return new CommandResult(
                true,
                answer.toString(),
                "DEVICE • BATTERY "
                        + capacity
                        + "%"
        );
    }

    private static CommandResult handleDirectOpen(
            Activity activity,
            String q
    ) {
        if (q.contains("open camera")
                || q.contains("camera openen")
                || q.contains("start camera")) {
            try {
                Intent intent =
                        new Intent(
                                MediaStore
                                        .INTENT_ACTION_STILL_IMAGE_CAMERA
                        );

                activity.startActivity(intent);

                return new CommandResult(
                        true,
                        "Camera geopend.",
                        "DEVICE • CAMERA"
                );

            } catch (Exception e) {
                return new CommandResult(
                        true,
                        "Ik kon de camera niet openen.",
                        "DEVICE • CAMERA ERROR"
                );
            }
        }

        if ((q.contains("wifi")
                || q.contains("wi fi"))
                && (q.contains("open")
                || q.contains("instelling")
                || q.contains("settings"))) {
            activity.startActivity(
                    new Intent(
                            Settings
                                    .ACTION_WIFI_SETTINGS
                    )
            );

            return new CommandResult(
                    true,
                    "Wi-Fi-instellingen geopend.",
                    "DEVICE • WIFI SETTINGS"
            );
        }

        if (q.contains("bluetooth")
                && (q.contains("open")
                || q.contains("instelling")
                || q.contains("settings"))) {
            activity.startActivity(
                    new Intent(
                            Settings
                                    .ACTION_BLUETOOTH_SETTINGS
                    )
            );

            return new CommandResult(
                    true,
                    "Bluetooth-instellingen geopend.",
                    "DEVICE • BLUETOOTH SETTINGS"
            );
        }

        return CommandResult.no();
    }

    private static CommandResult handleDeviceInfo(
            String q
    ) {
        boolean asksDevice =
                q.contains("welke telefoon")
                        || q.contains("welk toestel")
                        || q.contains("toestel info")
                        || q.contains("telefoon info")
                        || q.contains("android versie");

        if (!asksDevice) {
            return CommandResult.no();
        }

        String manufacturer =
                Build.MANUFACTURER == null
                        ? ""
                        : Build.MANUFACTURER.trim();

        String model =
                Build.MODEL == null
                        ? ""
                        : Build.MODEL.trim();

        String name =
                (manufacturer
                        + " "
                        + model)
                        .trim();

        return new CommandResult(
                true,
                "Dit toestel is "
                        + name
                        + " met Android "
                        + Build.VERSION.RELEASE
                        + ".",
                "DEVICE • INFO"
        );
    }

    private static CommandResult handleOpenApp(
            Activity activity,
            String raw,
            String q
    ) {
        String appName =
                extractAppName(
                        raw,
                        q
                );

        if (appName == null
                || appName.isEmpty()) {
            return CommandResult.no();
        }

        PackageManager pm =
                activity.getPackageManager();

        Intent launcher =
                new Intent(
                        Intent.ACTION_MAIN
                );

        launcher.addCategory(
                Intent.CATEGORY_LAUNCHER
        );

        List<ResolveInfo> apps =
                pm.queryIntentActivities(
                        launcher,
                        0
                );

        ResolveInfo best = null;
        int bestScore = 0;
        String wanted =
                normalize(appName);

        for (ResolveInfo info : apps) {
            CharSequence labelCs =
                    info.loadLabel(pm);

            if (labelCs == null) {
                continue;
            }

            String label =
                    labelCs.toString();

            String normalizedLabel =
                    normalize(label);

            int score = 0;

            if (normalizedLabel.equals(wanted)) {
                score = 100;
            } else if (normalizedLabel.startsWith(wanted)
                    || wanted.startsWith(normalizedLabel)) {
                score = 80;
            } else if (normalizedLabel.contains(wanted)
                    || wanted.contains(normalizedLabel)) {
                score = 60;
            }

            if (score > bestScore) {
                bestScore = score;
                best = info;
            }
        }

        if (best == null
                || bestScore < 60) {
            return new CommandResult(
                    true,
                    "Ik kan de app "
                            + appName
                            + " niet vinden.",
                    "DEVICE • APP NOT FOUND"
            );
        }

        String packageName =
                best.activityInfo
                        .packageName;

        Intent launch =
                pm.getLaunchIntentForPackage(
                        packageName
                );

        if (launch == null) {
            return new CommandResult(
                    true,
                    "Ik kan die app niet starten.",
                    "DEVICE • APP ERROR"
            );
        }

        activity.startActivity(launch);

        String label =
                best.loadLabel(pm)
                        .toString();

        return new CommandResult(
                true,
                label
                        + " geopend.",
                "DEVICE • APP OPEN"
        );
    }

    private static String extractAppName(
            String raw,
            String normalized
    ) {
        String lower =
                raw.toLowerCase(
                        Locale.ROOT
                )
                .trim();

        String[] prefixes = {
                "open app ",
                "open de app ",
                "open ",
                "start app ",
                "start de app ",
                "start "
        };

        for (String prefix : prefixes) {
            if (lower.startsWith(prefix)) {
                String candidate =
                        raw.substring(
                                Math.min(
                                        raw.length(),
                                        prefix.length()
                                )
                        )
                        .trim();

                String normalizedCandidate =
                        normalize(candidate);

                if (normalizedCandidate.equals("camera")
                        || normalizedCandidate.equals("wifi")
                        || normalizedCandidate.equals("wi fi")
                        || normalizedCandidate.equals("bluetooth")
                        || normalizedCandidate.equals("instellingen")
                        || normalizedCandidate.equals("settings")) {
                    return null;
                }

                return candidate;
            }
        }

        return null;
    }

    private static int parseDurationSeconds(
            String q
    ) {
        long seconds = 0L;
        boolean matched = false;

        Matcher hour = Pattern.compile(
                "(\\d{1,3})\\s*(?:uur|uren|u\\b)"
        ).matcher(q);
        while (hour.find()) {
            seconds += Long.parseLong(hour.group(1)) * 3600L;
            matched = true;
        }

        Matcher minute = Pattern.compile(
                "(\\d{1,4})\\s*(?:minuut|minuten|min\\b)"
        ).matcher(q);
        while (minute.find()) {
            seconds += Long.parseLong(minute.group(1)) * 60L;
            matched = true;
        }

        Matcher second = Pattern.compile(
                "(\\d{1,5})\\s*(?:seconde|seconden|sec\\b)"
        ).matcher(q);
        while (second.find()) {
            seconds += Long.parseLong(second.group(1));
            matched = true;
        }

        if (q.contains("anderhalf uur")) {
            seconds += 5400L;
            matched = true;
        } else if (q.contains("half uur")) {
            seconds += 1800L;
            matched = true;
        }

        if (q.contains("kwartier")) {
            seconds += 900L;
            matched = true;
        }

        if (!matched) {
            Matcher bare = Pattern.compile(
                    "timer(?:\\s+van|\\s+voor)?\\s+(\\d{1,4})(?:\\s|$)"
            ).matcher(q);
            if (bare.find()) {
                seconds = Long.parseLong(bare.group(1)) * 60L;
                matched = true;
            }
        }

        if (!matched) return 0;

        return (int) Math.min(
                Integer.MAX_VALUE,
                Math.max(1L, seconds)
        );
    }

    private static int[] parseAlarmTime(
            String q
    ) {
        Matcher clock = Pattern.compile(
                "(?:om\\s+)?([01]?\\d|2[0-3])[:.]([0-5]\\d)"
        ).matcher(q);
        if (clock.find()) {
            return new int[]{
                    Integer.parseInt(clock.group(1)),
                    Integer.parseInt(clock.group(2))
            };
        }

        Matcher half = Pattern.compile(
                "half\\s+([a-z]+|\\d{1,2})"
        ).matcher(q);
        if (half.find()) {
            Integer next = parseHourToken(half.group(1));
            if (next != null) {
                return new int[]{(next + 23) % 24, 30};
            }
        }

        Matcher over = Pattern.compile(
                "kwart\\s+over\\s+([a-z]+|\\d{1,2})"
        ).matcher(q);
        if (over.find()) {
            Integer hour = parseHourToken(over.group(1));
            if (hour != null) {
                return new int[]{hour, 15};
            }
        }

        Matcher before = Pattern.compile(
                "kwart\\s+voor\\s+([a-z]+|\\d{1,2})"
        ).matcher(q);
        if (before.find()) {
            Integer next = parseHourToken(before.group(1));
            if (next != null) {
                return new int[]{(next + 23) % 24, 45};
            }
        }

        Matcher simple = Pattern.compile(
                "(?:om\\s+)([a-z]+|\\d{1,2})(?:\\s+uur)?"
        ).matcher(q);
        if (simple.find()) {
            Integer hour = parseHourToken(simple.group(1));
            if (hour != null) {
                return new int[]{hour, 0};
            }
        }

        return null;
    }

    private static Integer parseHourToken(
            String token
    ) {
        if (token == null) return null;

        try {
            int value = Integer.parseInt(token);
            if (value >= 0 && value <= 23) return value;
        } catch (Exception ignored) {}

        switch (token) {
            case "nul": return 0;
            case "een": return 1;
            case "twee": return 2;
            case "drie": return 3;
            case "vier": return 4;
            case "vijf": return 5;
            case "zes": return 6;
            case "zeven": return 7;
            case "acht": return 8;
            case "negen": return 9;
            case "tien": return 10;
            case "elf": return 11;
            case "twaalf": return 12;
            case "dertien": return 13;
            case "veertien": return 14;
            case "vijftien": return 15;
            case "zestien": return 16;
            case "zeventien": return 17;
            case "achttien": return 18;
            case "negentien": return 19;
            case "twintig": return 20;
            case "eenentwintig": return 21;
            case "tweeentwintig":
            case "tweeëntwintig": return 22;
            case "drieentwintig":
            case "drieëntwintig": return 23;
            default: return null;
        }
    }

    private static String formatDuration(
            int totalSeconds
    ) {
        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        int seconds = totalSeconds % 60;
        StringBuilder sb = new StringBuilder();

        if (hours > 0) {
            sb.append(hours).append(" uur");
        }
        if (minutes > 0) {
            if (sb.length() > 0) sb.append(" en ");
            sb.append(minutes)
                    .append(minutes == 1 ? " minuut" : " minuten");
        }
        if (seconds > 0 || sb.length() == 0) {
            if (sb.length() > 0) sb.append(" en ");
            sb.append(seconds)
                    .append(seconds == 1 ? " seconde" : " seconden");
        }
        return sb.toString();
    }

    private static String extractTimerLabel(
            String raw
    ) {
        if (raw == null) return "";
        String lower = raw.toLowerCase(Locale.ROOT);
        Matcher m = Pattern.compile(
                "(?i)\\bvoor\\s+(?:de\\s+)?([\\p{L}][\\p{L}\\s-]{1,30})$"
        ).matcher(raw);
        if (!m.find()) return "";

        String candidate = m.group(1).trim();
        String normalized = normalize(candidate);
        if (normalized.matches(
                ".*(?:minuut|minuten|seconde|seconden|uur|uren|kwartier).*"
        )) {
            return "";
        }
        return candidate;
    }

    private static String extractAlarmLabel(
            String raw
    ) {
        if (raw == null) return "";
        Matcher m = Pattern.compile(
                "(?i)\\bvoor\\s+(?:de\\s+)?([\\p{L}][\\p{L}\\s-]{1,30})$"
        ).matcher(raw);
        if (!m.find()) return "";
        return m.group(1).trim();
    }

    private static Integer extractPercent(
            String q
    ) {
        Matcher matcher =
                Pattern.compile(
                        "(\\d{1,3})\\s*(?:%|procent)?"
                )
                .matcher(q);

        if (!matcher.find()) {
            return null;
        }

        try {
            int value =
                    Integer.parseInt(
                            matcher.group(1)
                    );

            return Math.max(
                    0,
                    Math.min(
                            100,
                            value
                    )
            );

        } catch (Exception ignored) {
            return null;
        }
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

    private static int percent(
            int value,
            int min,
            int max
    ) {
        if (max <= min) {
            return 0;
        }

        return Math.max(
                0,
                Math.min(
                        100,
                        Math.round(
                                (value - min)
                                        * 100f
                                        / (max - min)
                        )
                )
        );
    }

    private static int levelForPercent(
            int percent,
            int min,
            int max
    ) {
        return min
                + Math.round(
                        (max - min)
                                * (percent / 100f)
                );
    }

    private static SharedPreferences prefs(
            Context context
    ) {
        return context.getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
        );
    }

    private static String normalize(
            String value
    ) {
        return value
                .toLowerCase(
                        Locale.ROOT
                )
                .replaceAll(
                        "[^\\p{L}\\p{N}\\s]",
                        " "
                )
                .replaceAll(
                        "\\s+",
                        " "
                )
                .trim();
    }

    private static String safeMessage(
            Exception e
    ) {
        String message =
                e.getMessage();

        return message == null
                || message.trim().isEmpty()
                ? e.getClass()
                .getSimpleName()
                : message.trim();
    }
}
