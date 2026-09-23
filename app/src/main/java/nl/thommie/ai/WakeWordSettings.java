package nl.thommie.ai;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

final class WakeWordSettings {

    interface ChangeHandler {
        void onChanged(boolean enabled);
    }

    private static final String PREFS = "maatje_wake_word";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_SENSITIVITY = "sensitivity";
    private static final String KEY_DEBUG = "debug";
    private static final int DEFAULT_SENSITIVITY = 72;

    private WakeWordSettings() {}

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static boolean enabled(Context context) {
        return prefs(context).getBoolean(KEY_ENABLED, true);
    }

    static int sensitivity(Context context) {
        return prefs(context).getInt(KEY_SENSITIVITY, DEFAULT_SENSITIVITY);
    }

    static boolean debugEnabled(Context context) {
        return prefs(context).getBoolean(KEY_DEBUG, false);
    }

    static void show(Activity activity, ChangeHandler handler) {
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(45, 20, 45, 20);

        Switch enabled = new Switch(activity);
        enabled.setText("Luister lokaal naar \"Hey Maatje\"");
        enabled.setChecked(enabled(activity));
        box.addView(enabled);

        int savedSensitivity = sensitivity(activity);

        TextView sensitivityLabel = new TextView(activity);
        sensitivityLabel.setText("\nGevoeligheid: " + savedSensitivity + "%");
        sensitivityLabel.setTextSize(16);
        box.addView(sensitivityLabel);

        SeekBar sensitivity = new SeekBar(activity);
        sensitivity.setMax(100);
        sensitivity.setProgress(savedSensitivity);
        box.addView(sensitivity);

        sensitivity.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(
                            SeekBar seekBar,
                            int progress,
                            boolean fromUser
                    ) {
                        sensitivityLabel.setText(
                                "\nGevoeligheid: " + progress + "%"
                        );
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {}

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {}
                }
        );

        Switch debug = new Switch(activity);
        debug.setText("\nLive tonen wat de wake recognizer hoort");
        debug.setChecked(debugEnabled(activity));
        box.addView(debug);

        TextView info = new TextView(activity);
        info.setText(
                "\nMAATJE gebruikt in v0.8.3 een beperkte offline Nederlandse "
                        + "Vosk-woordenlijst voor het wake word.\n\n"
                        + "Aanbevolen startpunt: 70–80%.\n"
                        + "Hoger = makkelijker wakker, maar iets meer kans op foutieve activatie.\n"
                        + "Lager = strenger.\n\n"
                        + "Zet live debug aan als je wilt zien wat Vosk van jouw uitspraak maakt."
        );
        box.addView(info);

        new AlertDialog.Builder(activity)
                .setTitle("MAATJE v0.8.3 – Wake word")
                .setView(box)
                .setPositiveButton(
                        "Opslaan",
                        (d, which) -> {
                            boolean enabledValue = enabled.isChecked();

                            prefs(activity)
                                    .edit()
                                    .putBoolean(KEY_ENABLED, enabledValue)
                                    .putInt(KEY_SENSITIVITY, sensitivity.getProgress())
                                    .putBoolean(KEY_DEBUG, debug.isChecked())
                                    .apply();

                            handler.onChanged(enabledValue);

                            Toast.makeText(
                                    activity,
                                    enabledValue
                                            ? "\"Hey Maatje\" instellingen opgeslagen."
                                            : "Wake word staat uit.",
                                    Toast.LENGTH_SHORT
                            ).show();
                        }
                )
                .setNegativeButton("Annuleren", null)
                .show();
    }
}
