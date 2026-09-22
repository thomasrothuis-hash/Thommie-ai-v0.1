package nl.thommie.ai;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

final class WakeWordSettings {

    interface ChangeHandler {
        void onChanged(boolean enabled);
    }

    private static final String PREFS = "maatje_wake_word";
    private static final String KEY_ENABLED = "enabled";

    private WakeWordSettings() {}

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
        );
    }

    static boolean enabled(Context context) {
        return prefs(context).getBoolean(
                KEY_ENABLED,
                true
        );
    }

    static void show(
            Activity activity,
            ChangeHandler handler
    ) {
        LinearLayout box =
                new LinearLayout(activity);

        box.setOrientation(
                LinearLayout.VERTICAL
        );

        box.setPadding(
                45,
                20,
                45,
                20
        );

        Switch enabled =
                new Switch(activity);

        enabled.setText(
                "Luister naar \"Hey Maatje\""
        );

        enabled.setChecked(
                enabled(activity)
        );

        box.addView(enabled);

        TextView info =
                new TextView(activity);

        info.setText(
                "\nWanneer dit aan staat luistert MAATJE "
                        + "naar de wake phrase zolang de app zichtbaar is.\n\n"
                        + "Je kunt daarna je opdracht inspreken, of meteen zeggen:\n"
                        + "\"Hey Maatje, zet humor op 80.\"\n\n"
                        + "Deze v0.6 gebruikt daarvoor Android SpeechRecognizer. "
                        + "Een volledig lokale always-on hotwordengine bouwen we later."
        );

        box.addView(info);

        new AlertDialog.Builder(activity)
                .setTitle(
                        "MAATJE v0.6 – Wake word"
                )
                .setView(box)
                .setPositiveButton(
                        "Opslaan",
                        (d, which) -> {
                            boolean value =
                                    enabled.isChecked();

                            prefs(activity)
                                    .edit()
                                    .putBoolean(
                                            KEY_ENABLED,
                                            value
                                    )
                                    .apply();

                            handler.onChanged(value);

                            Toast.makeText(
                                    activity,
                                    value
                                            ? "\"Hey Maatje\" staat aan."
                                            : "Wake word staat uit.",
                                    Toast.LENGTH_SHORT
                            ).show();
                        }
                )
                .setNegativeButton(
                        "Annuleren",
                        null
                )
                .show();
    }
}
