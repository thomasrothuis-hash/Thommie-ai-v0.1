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

final class ConversationSettings {

    interface ChangeHandler {
        void onChanged(boolean enabled);
    }

    private static final String PREFS =
            "maatje_conversation";

    private static final String KEY_ENABLED =
            "enabled";

    private static final String KEY_TIMEOUT_SECONDS =
            "timeout_seconds";

    private static final int DEFAULT_TIMEOUT_SECONDS =
            25;

    private ConversationSettings() {}

    private static SharedPreferences prefs(
            Context context
    ) {
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

    static int timeoutSeconds(Context context) {
        return prefs(context).getInt(
                KEY_TIMEOUT_SECONDS,
                DEFAULT_TIMEOUT_SECONDS
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
                "Handsfree vervolgvragen"
        );

        enabled.setChecked(
                enabled(activity)
        );

        box.addView(enabled);

        int savedTimeout =
                timeoutSeconds(activity);

        TextView timeoutLabel =
                new TextView(activity);

        timeoutLabel.setText(
                "\nLuistervenster na antwoord: "
                        + savedTimeout
                        + " sec"
        );

        timeoutLabel.setTextSize(16);
        box.addView(timeoutLabel);

        SeekBar timeout =
                new SeekBar(activity);

        timeout.setMax(50);
        timeout.setProgress(
                Math.max(
                        0,
                        Math.min(
                                50,
                                savedTimeout - 10
                        )
                )
        );

        box.addView(timeout);

        timeout.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(
                            SeekBar seekBar,
                            int progress,
                            boolean fromUser
                    ) {
                        timeoutLabel.setText(
                                "\nLuistervenster na antwoord: "
                                        + (progress + 10)
                                        + " sec"
                        );
                    }

                    @Override
                    public void onStartTrackingTouch(
                            SeekBar seekBar
                    ) {}

                    @Override
                    public void onStopTrackingTouch(
                            SeekBar seekBar
                    ) {}
                }
        );

        TextView info =
                new TextView(activity);

        info.setText(
                "\nNa \"Hey Maatje\" blijft MAATJE in gesprek. "
                        + "Na elk gesproken antwoord luistert ze automatisch opnieuw.\n\n"
                        + "Tijdens een antwoord kun je zeggen:\n"
                        + "• \"Maatje stop\" om haar direct af te kappen.\n\n"
                        + "Tijdens het luisteren kun je zeggen:\n"
                        + "• \"Maatje klaar\"\n"
                        + "• \"Stop gesprek\"\n"
                        + "• \"Slaap maar\"\n"
                        + "om terug te gaan naar wake-word standby."
        );

        box.addView(info);

        new AlertDialog.Builder(activity)
                .setTitle(
                        "MAATJE v0.8.9 – Gespreksmodus"
                )
                .setView(box)
                .setPositiveButton(
                        "Opslaan",
                        (d, which) -> {
                            boolean enabledValue =
                                    enabled.isChecked();

                            int timeoutValue =
                                    timeout.getProgress()
                                            + 10;

                            prefs(activity)
                                    .edit()
                                    .putBoolean(
                                            KEY_ENABLED,
                                            enabledValue
                                    )
                                    .putInt(
                                            KEY_TIMEOUT_SECONDS,
                                            timeoutValue
                                    )
                                    .apply();

                            handler.onChanged(
                                    enabledValue
                            );

                            Toast.makeText(
                                    activity,
                                    enabledValue
                                            ? "Handsfree gespreksmodus staat aan."
                                            : "Handsfree gespreksmodus staat uit.",
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
