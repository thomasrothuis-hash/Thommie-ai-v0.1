package nl.thommie.ai;

import android.Manifest;
import android.content.ComponentName;
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
import android.provider.AlarmClock;
import android.provider.MediaStore;
import android.provider.Settings;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class AssistantOverlayActions {

    static final class Result {
        final boolean handled;
        final String message;

        Result(
                boolean handled,
                String message
        ) {
            this.handled = handled;
            this.message = message;
        }

        static Result no() {
            return new Result(
                    false,
                    ""
            );
        }
    }

    private static final String PREFS =
            "maatje_device_control";
    private static final String KEY_TORCH_STATE =
            "torch_state";

    private AssistantOverlayActions() {}

    static Result handle(
            Context context,
            String raw
    ) {
        if (raw == null
                || raw.trim().isEmpty()) {
            return Result.no();
        }

        String q =
                normalize(raw);

        Result timer =
                handleTimer(
                        context,
                        q
                );

        if (timer.handled) {
            return timer;
        }

        Result alarm =
                handleAlarm(
                        context,
                        q
                );

        if (alarm.handled) {
            return alarm;
        }

        Result torch =
                handleTorch(
                        context,
                        q
                );

        if (torch.handled) {
            return torch;
        }

        Result volume =
                handleVolume(
                        context,
                        q
                );

        if (volume.handled) {
            return volume;
        }

        Result brightness =
                handleBrightness(
                        context,
                        q
                );

        if (brightness.handled) {
            return brightness;
        }

        Result battery =
                handleBattery(
                        context,
                        q
                );

        if (battery.handled) {
            return battery;
        }

        Result direct =
                handleDirectOpen(
                        context,
                        q
                );

        if (direct.handled) {
            return direct;
        }

        Result app =
                handleOpenApp(
                        context,
                        raw
                );

        if (app.handled) {
            return app;
        }

        return Result.no();
    }

    private static Result handleTimer(
            Context context,
            String q
    ) {
        if (!q.contains("timer")) {
            return Result.no();
        }

        int seconds =
                parseDurationSeconds(q);

        if (seconds <= 0) {
            return new Result(
                    true,
                    "Zeg bijvoorbeeld: zet een timer van 10 minuten."
            );
        }

        try {
            Intent intent =
                    new Intent(
                            AlarmClock.ACTION_SET_TIMER
                    );

            intent.putExtra(
                    AlarmClock.EXTRA_LENGTH,
                    seconds
            );

            intent.putExtra(
                    AlarmClock.EXTRA_MESSAGE,
                    "MAATJE"
            );

            intent.putExtra(
                    AlarmClock.EXTRA_SKIP_UI,
                    true
            );

            intent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
            );

            context.startActivity(intent);

            return new Result(
                    true,
                    "Timer gezet voor "
                            + formatDuration(seconds)
                            + "."
            );

        } catch (Exception e) {
            return new Result(
                    true,
                    "Timer instellen lukte niet."
            );
        }
    }

    private static Result handleAlarm(
            Context context,
            String q
    ) {
        if (!q.contains("wekker")
                && !q.contains("alarm")) {
            return Result.no();
        }

        int[] time =
                parseAlarmTime(q);

        if (time == null) {
            return new Result(
                    true,
                    "Zeg bijvoorbeeld: zet een wekker om 07:30."
            );
        }

        try {
            Intent intent =
                    new Intent(
                            AlarmClock.ACTION_SET_ALARM
                    );

            intent.putExtra(
                    AlarmClock.EXTRA_HOUR,
                    time[0]
            );

            intent.putExtra(
                    AlarmClock.EXTRA_MINUTES,
                    time[1]
            );

            intent.putExtra(
                    AlarmClock.EXTRA_MESSAGE,
                    "MAATJE"
            );

            intent.putExtra(
                    AlarmClock.EXTRA_SKIP_UI,
                    true
            );

            if (q.contains("morgen")) {
                Calendar tomorrow =
                        Calendar.getInstance();

                tomorrow.add(
                        Calendar.DAY_OF_YEAR,
                        1
                );

                java.util.ArrayList<Integer> days =
                        new java.util.ArrayList<>();

                days.add(
                        tomorrow.get(
                                Calendar.DAY_OF_WEEK
                        )
                );

                intent.putIntegerArrayListExtra(
                        AlarmClock.EXTRA_DAYS,
                        days
                );
            }

            intent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
            );

            context.startActivity(intent);

            return new Result(
                    true,
                    String.format(
                            Locale.ROOT,
                            "Wekker gezet om %02d:%02d.",
                            time[0],
                            time[1]
                    )
            );

        } catch (Exception e) {
            return new Result(
                    true,
                    "Wekker instellen lukte niet."
            );
        }
    }

    private static Result handleTorch(
            Context context,
            String q
    ) {
        if (!q.contains("zaklamp")
                && !q.contains("flashlight")
                && !q.contains("flitser")) {
            return Result.no();
        }

        if (context.checkSelfPermission(
                Manifest.permission.CAMERA
        ) != PackageManager.PERMISSION_GRANTED) {
            return new Result(
                    true,
                    "Geef MAATJE eerst cameratoegang in de gewone app voor de zaklamp."
            );
        }

        boolean current =
                prefs(context)
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
                    (CameraManager) context
                            .getSystemService(
                                    Context.CAMERA_SERVICE
                            );

            String id =
                    findFlashCamera(
                            manager
                    );

            if (id == null) {
                return new Result(
                        true,
                        "Ik kan geen flitser vinden."
                );
            }

            manager.setTorchMode(
                    id,
                    turnOn
            );

            prefs(context)
                    .edit()
                    .putBoolean(
                            KEY_TORCH_STATE,
                            turnOn
                    )
                    .apply();

            return new Result(
                    true,
                    turnOn
                            ? "Zaklamp aan."
                            : "Zaklamp uit."
            );

        } catch (Exception e) {
            return new Result(
                    true,
                    "Zaklamp bedienen lukte niet."
            );
        }
    }

    private static String findFlashCamera(
            CameraManager manager
    ) throws Exception {
        if (manager == null) {
            return null;
        }

        String fallback = null;

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

            if (fallback == null) {
                fallback = id;
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
                return id;
            }
        }

        return fallback;
    }

    private static Result handleVolume(
            Context context,
            String q
    ) {
        boolean mentions =
                q.contains("volume")
                        || q.contains("geluid harder")
                        || q.contains("geluid zachter")
                        || q.equals("geluid uit")
                        || q.equals("geluid aan");

        if (!mentions) {
            return Result.no();
        }

        AudioManager audio =
                (AudioManager) context
                        .getSystemService(
                                Context.AUDIO_SERVICE
                        );

        if (audio == null) {
            return new Result(
                    true,
                    "Ik kan het volume niet uitlezen."
            );
        }

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
            return new Result(
                    true,
                    "Het mediavolume staat op "
                            + currentPercent
                            + " procent."
            );
        }

        int value =
                min
                        + Math.round(
                                (max - min)
                                        * (requested / 100f)
                        );

        try {
            audio.setStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    value,
                    0
            );

            return new Result(
                    true,
                    "Volume op "
                            + requested
                            + " procent."
            );

        } catch (Exception e) {
            return new Result(
                    true,
                    "Volume aanpassen lukte niet."
            );
        }
    }

    private static Result handleBrightness(
            Context context,
            String q
    ) {
        boolean mentions =
                q.contains("helderheid")
                        || q.contains("brightness")
                        || q.contains("scherm lichter")
                        || q.contains("scherm donkerder");

        if (!mentions) {
            return Result.no();
        }

        int current =
                Settings.System.getInt(
                        context.getContentResolver(),
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
            if (q.contains("lichter")
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
            return new Result(
                    true,
                    "De helderheid staat ongeveer op "
                            + currentPercent
                            + " procent."
            );
        }

        if (!Settings.System.canWrite(
                context
        )) {
            try {
                Intent intent =
                        new Intent(
                                Settings
                                        .ACTION_MANAGE_WRITE_SETTINGS
                        );

                intent.setData(
                        Uri.parse(
                                "package:"
                                        + context
                                        .getPackageName()
                        )
                );

                intent.addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK
                );

                context.startActivity(intent);

                return new Result(
                        true,
                        "Geef MAATJE toestemming om systeeminstellingen te wijzigen."
                );

            } catch (Exception e) {
                return new Result(
                        true,
                        "Ik heb systeemtoegang nodig voor de helderheid."
                );
            }
        }

        int value =
                Math.round(
                        requested
                                * 255f
                                / 100f
                );

        try {
            Settings.System.putInt(
                    context.getContentResolver(),
                    Settings.System
                            .SCREEN_BRIGHTNESS_MODE,
                    Settings.System
                            .SCREEN_BRIGHTNESS_MODE_MANUAL
            );

            Settings.System.putInt(
                    context.getContentResolver(),
                    Settings.System
                            .SCREEN_BRIGHTNESS,
                    value
            );

            return new Result(
                    true,
                    "Helderheid op "
                            + requested
                            + " procent."
            );

        } catch (Exception e) {
            return new Result(
                    true,
                    "Helderheid aanpassen lukte niet."
            );
        }
    }

    private static Result handleBattery(
            Context context,
            String q
    ) {
        if (!q.contains("batterij")
                && !q.contains("accu")) {
            return Result.no();
        }

        BatteryManager manager =
                (BatteryManager) context
                        .getSystemService(
                                Context.BATTERY_SERVICE
                        );

        int capacity =
                manager == null
                        ? -1
                        : manager.getIntProperty(
                                BatteryManager
                                        .BATTERY_PROPERTY_CAPACITY
                        );

        Intent status =
                context.registerReceiver(
                        null,
                        new IntentFilter(
                                Intent.ACTION_BATTERY_CHANGED
                        )
                );

        boolean charging = false;

        if (status != null) {
            int state =
                    status.getIntExtra(
                            BatteryManager.EXTRA_STATUS,
                            -1
                    );

            charging =
                    state
                            == BatteryManager.BATTERY_STATUS_CHARGING
                            || state
                            == BatteryManager.BATTERY_STATUS_FULL;
        }

        return new Result(
                true,
                capacity >= 0
                        ? "Batterij "
                        + capacity
                        + " procent"
                        + (charging
                        ? ", wordt opgeladen."
                        : ".")
                        : "Ik kan het batterijpercentage niet uitlezen."
        );
    }

    private static Result handleDirectOpen(
            Context context,
            String q
    ) {
        Intent intent = null;
        String message = null;

        if (q.contains("open camera")
                || q.contains("camera openen")
                || q.contains("start camera")) {
            intent =
                    new Intent(
                            MediaStore
                                    .INTENT_ACTION_STILL_IMAGE_CAMERA
                    );

            message =
                    "Camera geopend.";

        } else if ((q.contains("wifi")
                || q.contains("wi fi"))
                && (q.contains("open")
                || q.contains("instelling")
                || q.contains("settings"))) {

            intent =
                    new Intent(
                            Settings.ACTION_WIFI_SETTINGS
                    );

            message =
                    "Wi-Fi-instellingen geopend.";

        } else if (q.contains("bluetooth")
                && (q.contains("open")
                || q.contains("instelling")
                || q.contains("settings"))) {

            intent =
                    new Intent(
                            Settings
                                    .ACTION_BLUETOOTH_SETTINGS
                    );

            message =
                    "Bluetooth-instellingen geopend.";
        }

        if (intent == null) {
            return Result.no();
        }

        try {
            intent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
            );

            context.startActivity(intent);

            return new Result(
                    true,
                    message
            );

        } catch (Exception e) {
            return new Result(
                    true,
                    "Ik kon dat scherm niet openen."
            );
        }
    }

    private static Result handleOpenApp(
            Context context,
            String raw
    ) {
        if (raw == null) {
            return Result.no();
        }

        String lower =
                raw.toLowerCase(
                        Locale.ROOT
                )
                .trim();

        String appName = null;

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
                appName =
                        raw.substring(
                                Math.min(
                                        raw.length(),
                                        prefix.length()
                                )
                        )
                        .trim();

                break;
            }
        }

        if (appName == null
                || appName.isEmpty()) {
            return Result.no();
        }

        String normalized =
                normalize(appName);

        if (normalized.equals("camera")
                || normalized.equals("wifi")
                || normalized.equals("wi fi")
                || normalized.equals("bluetooth")) {
            return Result.no();
        }

        PackageManager pm =
                context.getPackageManager();

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

        for (ResolveInfo info : apps) {
            CharSequence labelCs =
                    info.loadLabel(pm);

            if (labelCs == null) {
                continue;
            }

            String label =
                    normalize(
                            labelCs.toString()
                    );

            int score = 0;

            if (label.equals(normalized)) {
                score = 100;
            } else if (label.startsWith(normalized)
                    || normalized.startsWith(label)) {
                score = 80;
            } else if (label.contains(normalized)
                    || normalized.contains(label)) {
                score = 60;
            }

            if (score > bestScore) {
                bestScore = score;
                best = info;
            }
        }

        if (best == null
                || bestScore < 60) {
            return new Result(
                    true,
                    "Ik kan die app niet vinden."
            );
        }

        Intent launch =
                pm.getLaunchIntentForPackage(
                        best.activityInfo.packageName
                );

        if (launch == null) {
            return new Result(
                    true,
                    "Ik kan die app niet starten."
            );
        }

        try {
            launch.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
            );

            context.startActivity(launch);

            return new Result(
                    true,
                    best.loadLabel(pm)
                            + " geopend."
            );

        } catch (Exception e) {
            return new Result(
                    true,
                    "Ik kon die app niet openen."
            );
        }
    }

    private static int parseDurationSeconds(
            String q
    ) {
        long seconds = 0L;
        boolean matched = false;

        Matcher hour =
                Pattern.compile(
                        "(\\d{1,3})\\s*(?:uur|uren|u\\b)"
                )
                .matcher(q);

        while (hour.find()) {
            seconds +=
                    Long.parseLong(
                            hour.group(1)
                    )
                            * 3600L;

            matched = true;
        }

        Matcher minute =
                Pattern.compile(
                        "(\\d{1,4})\\s*(?:minuut|minuten|min\\b)"
                )
                .matcher(q);

        while (minute.find()) {
            seconds +=
                    Long.parseLong(
                            minute.group(1)
                    )
                            * 60L;

            matched = true;
        }

        Matcher second =
                Pattern.compile(
                        "(\\d{1,5})\\s*(?:seconde|seconden|sec\\b)"
                )
                .matcher(q);

        while (second.find()) {
            seconds +=
                    Long.parseLong(
                            second.group(1)
                    );

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
            Matcher bare =
                    Pattern.compile(
                            "timer(?:\\s+van|\\s+voor)?\\s+(\\d{1,4})(?:\\s|$)"
                    )
                    .matcher(q);

            if (bare.find()) {
                seconds =
                        Long.parseLong(
                                bare.group(1)
                        )
                                * 60L;

                matched = true;
            }
        }

        if (!matched) {
            return 0;
        }

        return (int) Math.min(
                Integer.MAX_VALUE,
                Math.max(
                        1L,
                        seconds
                )
        );
    }

    private static int[] parseAlarmTime(
            String q
    ) {
        Matcher clock =
                Pattern.compile(
                        "(?:om\\s+)?([01]?\\d|2[0-3])[:.]([0-5]\\d)"
                )
                .matcher(q);

        if (clock.find()) {
            return new int[]{
                    Integer.parseInt(
                            clock.group(1)
                    ),
                    Integer.parseInt(
                            clock.group(2)
                    )
            };
        }

        Matcher half =
                Pattern.compile(
                        "half\\s+([a-z]+|\\d{1,2})"
                )
                .matcher(q);

        if (half.find()) {
            Integer next =
                    parseHourToken(
                            half.group(1)
                    );

            if (next != null) {
                return new int[]{
                        (next + 23) % 24,
                        30
                };
            }
        }

        Matcher simple =
                Pattern.compile(
                        "(?:om\\s+)([a-z]+|\\d{1,2})(?:\\s+uur)?"
                )
                .matcher(q);

        if (simple.find()) {
            Integer hour =
                    parseHourToken(
                            simple.group(1)
                    );

            if (hour != null) {
                return new int[]{
                        hour,
                        0
                };
            }
        }

        return null;
    }

    private static Integer parseHourToken(
            String token
    ) {
        if (token == null) {
            return null;
        }

        try {
            int n =
                    Integer.parseInt(token);

            if (n >= 0
                    && n <= 23) {
                return n;
            }

        } catch (Exception ignored) {}

        String[] words = {
                "nul",
                "een",
                "twee",
                "drie",
                "vier",
                "vijf",
                "zes",
                "zeven",
                "acht",
                "negen",
                "tien",
                "elf",
                "twaalf",
                "dertien",
                "veertien",
                "vijftien",
                "zestien",
                "zeventien",
                "achttien",
                "negentien",
                "twintig",
                "eenentwintig",
                "tweeentwintig",
                "drieentwintig"
        };

        String clean =
                token.replace('ë', 'e');

        for (int i = 0; i < words.length; i++) {
            if (words[i].equals(clean)) {
                return i;
            }
        }

        return null;
    }

    private static String formatDuration(
            int seconds
    ) {
        int hours =
                seconds / 3600;

        int minutes =
                (seconds % 3600)
                        / 60;

        int rest =
                seconds % 60;

        StringBuilder sb =
                new StringBuilder();

        if (hours > 0) {
            sb.append(hours)
                    .append(" uur");
        }

        if (minutes > 0) {
            if (sb.length() > 0) {
                sb.append(" en ");
            }

            sb.append(minutes)
                    .append(
                            minutes == 1
                                    ? " minuut"
                                    : " minuten"
                    );
        }

        if (rest > 0
                || sb.length() == 0) {
            if (sb.length() > 0) {
                sb.append(" en ");
            }

            sb.append(rest)
                    .append(
                            rest == 1
                                    ? " seconde"
                                    : " seconden"
                    );
        }

        return sb.toString();
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

    private static boolean containsOn(
            String q
    ) {
        return q.contains(" aan")
                || q.endsWith("aan")
                || q.contains("inschakel");
    }

    private static boolean containsOff(
            String q
    ) {
        return q.contains(" uit")
                || q.endsWith("uit")
                || q.contains("uitschakel");
    }

    private static String normalize(
            String value
    ) {
        return value
                .toLowerCase(
                        Locale.ROOT
                )
                .replace('é', 'e')
                .replace('è', 'e')
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

    private static SharedPreferences prefs(
            Context context
    ) {
        return context.getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
        );
    }
}
