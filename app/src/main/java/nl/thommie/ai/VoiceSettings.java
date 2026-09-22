package nl.thommie.ai;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;
import java.util.Set;

final class VoiceSettings {

    private static final String PREFS = "thommie_ai_voice";

    private static final String KEY_LANGUAGE = "language";
    private static final String KEY_VOICE = "voice";
    private static final String KEY_PITCH = "pitch";
    private static final String KEY_RATE = "rate";
    private static final String KEY_AUTO = "auto_speak";

    private VoiceSettings() {}

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static boolean autoSpeak(Context context) {
        return prefs(context).getBoolean(KEY_AUTO, true);
    }

    static void apply(Context context, TextToSpeech tts) {
        if (tts == null) return;

        SharedPreferences p = prefs(context);

        String languageTag = p.getString(KEY_LANGUAGE, "nl-NL");
        String voiceName = p.getString(KEY_VOICE, "");
        float pitch = p.getFloat(KEY_PITCH, 0.88f);
        float rate = p.getFloat(KEY_RATE, 1.03f);

        Locale locale = Locale.forLanguageTag(languageTag);

        tts.setLanguage(locale);

        if (!voiceName.isEmpty()) {
            Set<Voice> available = tts.getVoices();

            if (available != null) {
                for (Voice voice : available) {
                    if (voiceName.equals(voice.getName())) {
                        tts.setVoice(voice);
                        break;
                    }
                }
            }
        }

        tts.setPitch(pitch);
        tts.setSpeechRate(rate);
    }

    static void show(Activity activity, TextToSpeech tts, boolean ttsReady) {

        SharedPreferences p = prefs(activity);

        String savedLanguage = p.getString(KEY_LANGUAGE, "nl-NL");
        String savedVoice = p.getString(KEY_VOICE, "");
        float savedPitch = p.getFloat(KEY_PITCH, 0.88f);
        float savedRate = p.getFloat(KEY_RATE, 1.03f);
        boolean savedAuto = p.getBoolean(KEY_AUTO, true);

        ScrollView scroll = new ScrollView(activity);

        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(45, 20, 45, 30);
        scroll.addView(box);

        TextView languageLabel = new TextView(activity);
        languageLabel.setText("Stemtaal");
        languageLabel.setTextSize(16);
        box.addView(languageLabel);

        Spinner languageSpinner = new Spinner(activity);

        String[] languages = {
                "Nederlands (NL)",
                "English (UK)"
        };

        ArrayAdapter<String> languageAdapter =
                new ArrayAdapter<>(
                        activity,
                        android.R.layout.simple_spinner_item,
                        languages
                );

        languageAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );

        languageSpinner.setAdapter(languageAdapter);

        if ("en-GB".equals(savedLanguage)) {
            languageSpinner.setSelection(1);
        } else {
            languageSpinner.setSelection(0);
        }

        box.addView(languageSpinner);

        TextView voiceLabel = new TextView(activity);
        voiceLabel.setText("\nStem");
        voiceLabel.setTextSize(16);
        box.addView(voiceLabel);

        Spinner voiceSpinner = new Spinner(activity);

        ArrayList<String> voiceLabels = new ArrayList<>();
        ArrayList<Voice> voiceObjects = new ArrayList<>();

        ArrayAdapter<String> voiceAdapter =
                new ArrayAdapter<>(
                        activity,
                        android.R.layout.simple_spinner_item,
                        voiceLabels
                );

        voiceAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );

        voiceSpinner.setAdapter(voiceAdapter);
        box.addView(voiceSpinner);

        Locale initialLocale =
                languageSpinner.getSelectedItemPosition() == 1
                        ? Locale.UK
                        : new Locale("nl", "NL");

        populateVoices(
                tts,
                initialLocale,
                savedVoice,
                voiceSpinner,
                voiceAdapter,
                voiceLabels,
                voiceObjects
        );

        languageSpinner.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(
                            AdapterView<?> parent,
                            View view,
                            int position,
                            long id
                    ) {
                        Locale locale =
                                position == 1
                                        ? Locale.UK
                                        : new Locale("nl", "NL");

                        populateVoices(
                                tts,
                                locale,
                                savedVoice,
                                voiceSpinner,
                                voiceAdapter,
                                voiceLabels,
                                voiceObjects
                        );
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {}
                }
        );

        TextView pitchLabel = new TextView(activity);
        pitchLabel.setText(
                "\nPitch: " +
                        String.format(Locale.US, "%.2f", savedPitch)
        );
        pitchLabel.setTextSize(16);
        box.addView(pitchLabel);

        SeekBar pitchSeek = new SeekBar(activity);
        pitchSeek.setMax(100);
        pitchSeek.setProgress(
                clamp(Math.round((savedPitch - 0.50f) * 100f))
        );
        box.addView(pitchSeek);

        pitchSeek.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(
                            SeekBar seekBar,
                            int progress,
                            boolean fromUser
                    ) {
                        float value = 0.50f + (progress / 100f);

                        pitchLabel.setText(
                                "\nPitch: " +
                                        String.format(
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

        TextView rateLabel = new TextView(activity);
        rateLabel.setText(
                "\nSnelheid: " +
                        String.format(Locale.US, "%.2f", savedRate)
        );
        rateLabel.setTextSize(16);
        box.addView(rateLabel);

        SeekBar rateSeek = new SeekBar(activity);
        rateSeek.setMax(100);
        rateSeek.setProgress(
                clamp(Math.round((savedRate - 0.50f) * 100f))
        );
        box.addView(rateSeek);

        rateSeek.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(
                            SeekBar seekBar,
                            int progress,
                            boolean fromUser
                    ) {
                        float value = 0.50f + (progress / 100f);

                        rateLabel.setText(
                                "\nSnelheid: " +
                                        String.format(
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

        Switch autoSpeak = new Switch(activity);
        autoSpeak.setText("\nAntwoorden automatisch voorlezen");
        autoSpeak.setChecked(savedAuto);
        box.addView(autoSpeak);

        Button jarvisPreset = new Button(activity);
        jarvisPreset.setText("JARVIS TONE PRESET");
        box.addView(jarvisPreset);

        jarvisPreset.setOnClickListener(v -> {
            pitchSeek.setProgress(38);
            rateSeek.setProgress(53);
        });

        Button testVoice = new Button(activity);
        testVoice.setText("▶ TEST STEM");
        box.addView(testVoice);

        testVoice.setOnClickListener(v -> {

            if (!ttsReady || tts == null) {
                Toast.makeText(
                        activity,
                        "TTS is nog niet beschikbaar.",
                        Toast.LENGTH_SHORT
                ).show();
                return;
            }

            String languageTag =
                    languageSpinner.getSelectedItemPosition() == 1
                            ? "en-GB"
                            : "nl-NL";

            float pitch =
                    0.50f + pitchSeek.getProgress() / 100f;

            float rate =
                    0.50f + rateSeek.getProgress() / 100f;

            Voice selectedVoice = null;

            int voicePosition =
                    voiceSpinner.getSelectedItemPosition();

            if (voicePosition >= 0
                    && voicePosition < voiceObjects.size()) {
                selectedVoice =
                        voiceObjects.get(voicePosition);
            }

            preview(
                    tts,
                    languageTag,
                    selectedVoice,
                    pitch,
                    rate
            );

            String testText;

            if ("en-GB".equals(languageTag)) {
                testText =
                        "Good afternoon. THOMMIE AI is online. "
                        + "All systems are functioning normally.";
            } else {
                testText =
                        "Goedemiddag. THOMMIE AI is online. "
                        + "Alle systemen functioneren normaal.";
            }

            tts.speak(
                    testText,
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    "thommie_voice_test"
            );
        });

        AlertDialog dialog =
                new AlertDialog.Builder(activity)
                        .setTitle("THOMMIE AI v0.3 – Stem & audio")
                        .setView(scroll)
                        .setPositiveButton(
                                "Opslaan",
                                (d, which) -> {

                                    String languageTag =
                                            languageSpinner
                                                    .getSelectedItemPosition()
                                                    == 1
                                                    ? "en-GB"
                                                    : "nl-NL";

                                    float pitch =
                                            0.50f
                                                    + pitchSeek
                                                    .getProgress()
                                                    / 100f;

                                    float rate =
                                            0.50f
                                                    + rateSeek
                                                    .getProgress()
                                                    / 100f;

                                    String voiceName = "";

                                    int voicePosition =
                                            voiceSpinner
                                                    .getSelectedItemPosition();

                                    if (voicePosition >= 0
                                            && voicePosition
                                            < voiceObjects.size()
                                            && voiceObjects.get(
                                            voicePosition
                                    ) != null) {

                                        voiceName =
                                                voiceObjects
                                                        .get(voicePosition)
                                                        .getName();
                                    }

                                    p.edit()
                                            .putString(
                                                    KEY_LANGUAGE,
                                                    languageTag
                                            )
                                            .putString(
                                                    KEY_VOICE,
                                                    voiceName
                                            )
                                            .putFloat(
                                                    KEY_PITCH,
                                                    pitch
                                            )
                                            .putFloat(
                                                    KEY_RATE,
                                                    rate
                                            )
                                            .putBoolean(
                                                    KEY_AUTO,
                                                    autoSpeak.isChecked()
                                            )
                                            .apply();

                                    apply(activity, tts);

                                    Toast.makeText(
                                            activity,
                                            "Steminstellingen opgeslagen.",
                                            Toast.LENGTH_SHORT
                                    ).show();
                                }
                        )
                        .setNegativeButton(
                                "Annuleren",
                                (d, which) -> apply(activity, tts)
                        )
                        .create();

        dialog.show();
    }

    private static void preview(
            TextToSpeech tts,
            String languageTag,
            Voice voice,
            float pitch,
            float rate
    ) {
        Locale locale =
                Locale.forLanguageTag(languageTag);

        tts.setLanguage(locale);

        if (voice != null) {
            tts.setVoice(voice);
        }

        tts.setPitch(pitch);
        tts.setSpeechRate(rate);
    }

    private static void populateVoices(
            TextToSpeech tts,
            Locale locale,
            String selectedVoiceName,
            Spinner spinner,
            ArrayAdapter<String> adapter,
            ArrayList<String> labels,
            ArrayList<Voice> objects
    ) {

        labels.clear();
        objects.clear();

        labels.add("Systeemstem");
        objects.add(null);

        if (tts != null && tts.getVoices() != null) {

            ArrayList<Voice> filtered =
                    new ArrayList<>();

            for (Voice voice : tts.getVoices()) {

                Locale voiceLocale =
                        voice.getLocale();

                boolean languageMatch =
                        voiceLocale.getLanguage()
                                .equalsIgnoreCase(
                                        locale.getLanguage()
                                );

                boolean countryMatch =
                        locale.getCountry().isEmpty()
                                || voiceLocale.getCountry()
                                .equalsIgnoreCase(
                                        locale.getCountry()
                                );

                if (languageMatch && countryMatch) {
                    filtered.add(voice);
                }
            }

            Collections.sort(
                    filtered,
                    new Comparator<Voice>() {
                        @Override
                        public int compare(
                                Voice a,
                                Voice b
                        ) {
                            return a.getName()
                                    .compareToIgnoreCase(
                                            b.getName()
                                    );
                        }
                    }
            );

            for (Voice voice : filtered) {

                String type =
                        voice.isNetworkConnectionRequired()
                                ? "online"
                                : "lokaal";

                labels.add(
                        voice.getName()
                                + " • "
                                + type
                );

                objects.add(voice);
            }
        }

        adapter.notifyDataSetChanged();

        int selection = 0;

        if (selectedVoiceName != null
                && !selectedVoiceName.isEmpty()) {

            for (int i = 1;
                 i < objects.size();
                 i++) {

                Voice voice = objects.get(i);

                if (voice != null
                        && selectedVoiceName.equals(
                        voice.getName()
                )) {
                    selection = i;
                    break;
                }
            }
        }

        spinner.setSelection(selection);
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }
}
