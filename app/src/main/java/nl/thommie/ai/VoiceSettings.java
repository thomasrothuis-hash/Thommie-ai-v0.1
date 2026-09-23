package nl.thommie.ai;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.InputType;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

final class VoiceSettings {

    interface TestHandler {
        void test(String voice, float speed, String instructions);
    }

    private static final String PREFS = "thommie_ai_voice";
    private static final String KEY_VOICE = "cloud_voice";
    private static final String KEY_SPEED = "cloud_speed";
    private static final String KEY_STYLE = "cloud_style";
    private static final String KEY_AUTO = "auto_speak";

    private static final String DEFAULT_VOICE = "marin";
    private static final float DEFAULT_SPEED = 0.96f;

    private static final String DEFAULT_STYLE =
            "Speak in Dutch with a calm, composed, warm and intelligent presentation. "
            + "Use a smooth feminine-leaning tone, restrained confidence, subtle dry wit, "
            + "natural short pauses and a slightly cinematic personal-assistant feel. "
            + "Never sound excited, sales-like, childish or overly cheerful.";

    private VoiceSettings() {}

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static boolean autoSpeak(Context context) {
        return prefs(context).getBoolean(KEY_AUTO, true);
    }

    static String voice(Context context) {
        return prefs(context).getString(KEY_VOICE, DEFAULT_VOICE);
    }

    static float speed(Context context) {
        return prefs(context).getFloat(KEY_SPEED, DEFAULT_SPEED);
    }

    static String style(Context context) {
        return prefs(context).getString(KEY_STYLE, DEFAULT_STYLE);
    }

    static void show(Activity activity, TestHandler testHandler) {
        SharedPreferences p = prefs(activity);

        String savedVoice = voice(activity);
        float savedSpeed = speed(activity);
        String savedStyle = style(activity);
        boolean savedAuto = autoSpeak(activity);

        ScrollView scroll = new ScrollView(activity);
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(45, 20, 45, 30);
        scroll.addView(box);

        TextView engineInfo = new TextView(activity);
        engineInfo.setText(
                "Cloud voice via OpenAI gpt-4o-mini-tts.\n"
                + "Dit vervangt de Samsung-voorleesstem voor MAATJE."
        );
        engineInfo.setPadding(0, 0, 0, 20);
        box.addView(engineInfo);

        TextView voiceLabel = new TextView(activity);
        voiceLabel.setText("Stem");
        voiceLabel.setTextSize(16);
        box.addView(voiceLabel);

        Spinner voiceSpinner = new Spinner(activity);
        String[] labels = {
                "Marin",
                "Coral",
                "Shimmer",
                "Nova",
                "Sage",
                "Cedar"
        };
        String[] values = {
                "marin",
                "coral",
                "shimmer",
                "nova",
                "sage",
                "cedar"
        };

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                activity,
                android.R.layout.simple_spinner_item,
                labels
        );
        adapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );
        voiceSpinner.setAdapter(adapter);

        int selected = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(savedVoice)) {
                selected = i;
                break;
            }
        }
        voiceSpinner.setSelection(selected);
        box.addView(voiceSpinner);

        TextView speedLabel = new TextView(activity);
        speedLabel.setText(
                "\nSnelheid: "
                        + String.format(Locale.US, "%.2f", savedSpeed)
        );
        speedLabel.setTextSize(16);
        box.addView(speedLabel);

        SeekBar speedSeek = new SeekBar(activity);
        speedSeek.setMax(100);
        int initialProgress =
                Math.round((savedSpeed - 0.75f) / 0.005f);
        speedSeek.setProgress(Math.max(0, Math.min(100, initialProgress)));
        box.addView(speedSeek);

        speedSeek.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(
                            SeekBar seekBar,
                            int progress,
                            boolean fromUser
                    ) {
                        float value = 0.75f + progress * 0.005f;
                        speedLabel.setText(
                                "\nSnelheid: "
                                        + String.format(
                                        Locale.US,
                                        "%.2f",
                                        value
                                )
                        );
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {}

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {}
                }
        );

        TextView styleLabel = new TextView(activity);
        styleLabel.setText("\nSpreekstijl");
        styleLabel.setTextSize(16);
        box.addView(styleLabel);

        EditText style = new EditText(activity);
        style.setText(savedStyle);
        style.setMinLines(5);
        style.setMaxLines(8);
        style.setInputType(
                InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                        | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        );
        box.addView(style);

        Switch autoSpeak = new Switch(activity);
        autoSpeak.setText("\nAntwoorden automatisch voorlezen");
        autoSpeak.setChecked(savedAuto);
        box.addView(autoSpeak);

        Button preset = new Button(activity);
        preset.setText("CALM ASSISTANT PRESET");
        box.addView(preset);

        preset.setOnClickListener(v -> {
            voiceSpinner.setSelection(0);
            speedSeek.setProgress(
                    Math.round((0.96f - 0.75f) / 0.005f)
            );
            style.setText(DEFAULT_STYLE);
        });

        Button test = new Button(activity);
        test.setText("▶ TEST STEM");
        box.addView(test);

        test.setOnClickListener(v -> {
            String voice = values[voiceSpinner.getSelectedItemPosition()];
            float speedValue = 0.75f + speedSeek.getProgress() * 0.005f;
            String styleValue = style.getText().toString().trim();
            testHandler.test(voice, speedValue, styleValue);
        });

        new AlertDialog.Builder(activity)
                .setTitle("MAATJE v0.8.5 – Stem & audio")
                .setView(scroll)
                .setPositiveButton("Opslaan", (d, which) -> {
                    String voice =
                            values[voiceSpinner.getSelectedItemPosition()];
                    float speedValue =
                            0.75f + speedSeek.getProgress() * 0.005f;

                    p.edit()
                            .putString(KEY_VOICE, voice)
                            .putFloat(KEY_SPEED, speedValue)
                            .putString(
                                    KEY_STYLE,
                                    style.getText().toString().trim()
                            )
                            .putBoolean(KEY_AUTO, autoSpeak.isChecked())
                            .apply();

                    Toast.makeText(
                            activity,
                            "Cloudstem opgeslagen.",
                            Toast.LENGTH_SHORT
                    ).show();
                })
                .setNegativeButton("Annuleren", null)
                .show();
    }
}
