package nl.thommie.ai;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

final class DisplaySettings {

    private static final String PREFS =
            "maatje_display";

    private static final String KEY_AOD =
            "aod_enabled";

    private static final String KEY_WAKE_SCREEN =
            "wake_screen_on_hotword";

    private static final boolean DEFAULT_AOD =
            false;

    private static final boolean DEFAULT_WAKE_SCREEN =
            true;

    private DisplaySettings() {}

    private static SharedPreferences prefs(
            Context context
    ) {
        return context.getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
        );
    }

    static boolean aodEnabled(
            Context context
    ) {
        return prefs(context)
                .getBoolean(
                        KEY_AOD,
                        DEFAULT_AOD
                );
    }

    static void setAodEnabled(
            Context context,
            boolean enabled
    ) {
        prefs(context)
                .edit()
                .putBoolean(
                        KEY_AOD,
                        enabled
                )
                .apply();
    }

    static boolean wakeScreenOnHotword(
            Context context
    ) {
        return prefs(context)
                .getBoolean(
                        KEY_WAKE_SCREEN,
                        DEFAULT_WAKE_SCREEN
                );
    }

    static void setWakeScreenOnHotword(
            Context context,
            boolean enabled
    ) {
        prefs(context)
                .edit()
                .putBoolean(
                        KEY_WAKE_SCREEN,
                        enabled
                )
                .apply();
    }

    static void show(
            Activity activity,
            Runnable onChanged
    ) {
        boolean aod =
                aodEnabled(activity);

        boolean wake =
                wakeScreenOnHotword(
                        activity
                );

        MaatjeMenuDialog.show(
                activity,
                "Scherm & AOD",
                "Gedrag van je dedicated MAATJE-terminal.",
                MaatjeMenuDialog.item(
                        "AOD",
                        "Always-on display",
                        aod
                                ? "AAN • na 50 sec verschijnt de donkere klok"
                                : "UIT • scherm gaat na 1 minuut echt uit",
                        () -> {
                            setAodEnabled(
                                    activity,
                                    !aod
                            );

                            if (onChanged != null) {
                                onChanged.run();
                            }

                            show(
                                    activity,
                                    onChanged
                            );
                        }
                ),
                MaatjeMenuDialog.item(
                        "HEY",
                        "Scherm wakker bij “Hey Maatje”",
                        wake
                                ? "AAN • wake word zet het scherm automatisch aan"
                                : "UIT • wake word reageert zonder display-wake",
                        () -> {
                            setWakeScreenOnHotword(
                                    activity,
                                    !wake
                            );

                            show(
                                    activity,
                                    onChanged
                            );
                        }
                ),
                MaatjeMenuDialog.item(
                        "1M",
                        "Schermtimeout",
                        "1 minuut • ingesteld door MAATJE Kiosk Device Owner",
                        () -> show(
                                activity,
                                onChanged
                        )
                ),
                MaatjeMenuDialog.item(
                        "MIC",
                        "Na antwoord blijven luisteren",
                        ConversationSettings.enabled(activity)
                                ? "AAN • handsfree vervolgvragen"
                                : "UIT • na één antwoord terug naar Hey Maatje",
                        () -> {
                            ConversationSettings.setEnabled(
                                    activity,
                                    !ConversationSettings.enabled(
                                            activity
                                    )
                            );

                            show(
                                    activity,
                                    onChanged
                            );
                        }
                )
        );
    }
}
