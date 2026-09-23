package nl.thommie.ai;

import android.app.Activity;

import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.RecognitionListener;
import org.vosk.android.SpeechService;
import org.vosk.android.StorageService;

import java.util.Locale;

final class OfflineWakeWord implements RecognitionListener {

    interface Callback {
        void onReady();
        void onDetected();
        void onHeard(String text);
        void onError(String message);
    }

    private final Activity activity;
    private final Callback callback;

    private Model model;
    private SpeechService speechService;

    private boolean preparing = false;
    private boolean ready = false;
    private boolean running = false;
    private boolean detected = false;
    private String lastHeard = "";

    OfflineWakeWord(
            Activity activity,
            Callback callback
    ) {
        this.activity = activity;
        this.callback = callback;
    }

    void prepare() {
        if (ready || preparing) return;

        preparing = true;

        StorageService.unpack(
                activity,
                "model-nl",
                "maatje-vosk-model",
                unpackedModel -> {
                    model = unpackedModel;
                    preparing = false;
                    ready = true;
                    callback.onReady();
                },
                exception -> {
                    preparing = false;
                    ready = false;

                    callback.onError(
                            "Offline wake-model laden mislukt: "
                                    + exception.getMessage()
                    );
                }
        );
    }

    boolean isReady() {
        return ready;
    }

    synchronized void start() {
        if (!ready || running || model == null) {
            return;
        }

        detected = false;
        lastHeard = "";

        try {
            int sensitivity =
                    WakeWordSettings.sensitivity(activity);

            String grammar =
                    grammarForSensitivity(sensitivity);

            Recognizer recognizer =
                    new Recognizer(
                            model,
                            16000.0f,
                            grammar
                    );

            speechService =
                    new SpeechService(
                            recognizer,
                            16000.0f
                    );

            running = true;
            speechService.startListening(this);

        } catch (Exception e) {
            running = false;
            speechService = null;

            callback.onError(
                    "Offline wake-listener starten mislukt: "
                            + e.getMessage()
            );
        }
    }

    synchronized void stop() {
        running = false;

        if (speechService != null) {
            try {
                speechService.stop();
            } catch (Exception ignored) {}

            try {
                speechService.shutdown();
            } catch (Exception ignored) {}

            speechService = null;
        }
    }

    void destroy() {
        stop();
    }

    @Override
    public void onPartialResult(String hypothesis) {
        inspect(hypothesis);
    }

    @Override
    public void onResult(String hypothesis) {
        inspect(hypothesis);
    }

    @Override
    public void onFinalResult(String hypothesis) {
        inspect(hypothesis);
    }

    @Override
    public void onError(Exception exception) {
        running = false;

        callback.onError(
                "Offline wake-listener fout: "
                        + exception.getMessage()
        );
    }

    @Override
    public void onTimeout() {
        running = false;
    }

    private void inspect(String hypothesis) {
        if (detected
                || hypothesis == null
                || hypothesis.isEmpty()) {
            return;
        }

        String text = extractText(hypothesis);

        if (text.isEmpty()) return;

        String normalized = normalize(text);

        if (normalized.isEmpty()
                || "[unk]".equals(normalized)) {
            return;
        }

        if (!normalized.equals(lastHeard)) {
            lastHeard = normalized;

            activity.runOnUiThread(
                    () -> callback.onHeard(normalized)
            );
        }

        int sensitivity =
                WakeWordSettings.sensitivity(activity);

        if (containsWakePhrase(
                normalized,
                sensitivity
        )) {
            detected = true;
            stop();

            activity.runOnUiThread(
                    callback::onDetected
            );
        }
    }

    private String extractText(String json) {
        try {
            JSONObject root = new JSONObject(json);

            String partial =
                    root.optString(
                            "partial",
                            ""
                    ).trim();

            if (!partial.isEmpty()) {
                return partial;
            }

            return root.optString(
                    "text",
                    ""
            ).trim();

        } catch (Exception ignored) {
            return json.trim();
        }
    }

    private String normalize(String value) {
        return value
                .toLowerCase(Locale.ROOT)
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

    private boolean containsWakePhrase(
            String value,
            int sensitivity
    ) {
        if (value.equals("hey maatje")
                || value.equals("hee maatje")
                || value.equals("he maatje")
                || value.equals("hey maartje")
                || value.equals("hee maartje")
                || value.equals("he maartje")) {
            return true;
        }

        if (sensitivity >= 45) {
            if (value.equals("hoi maatje")
                    || value.equals("hoi maartje")
                    || value.equals("hey maat")
                    || value.equals("hee maat")
                    || value.equals("he maat")) {
                return true;
            }
        }

        if (sensitivity >= 70) {
            if (value.contains("hey maatje")
                    || value.contains("hee maatje")
                    || value.contains("he maatje")
                    || value.contains("hey maartje")
                    || value.contains("hee maartje")
                    || value.contains("he maartje")
                    || value.contains("hoi maatje")
                    || value.contains("hoi maartje")) {
                return true;
            }
        }

        if (sensitivity >= 88) {
            return value.equals("maatje")
                    || value.equals("maartje")
                    || value.equals("maat");
        }

        return false;
    }

    private String grammarForSensitivity(
            int sensitivity
    ) {
        if (sensitivity >= 88) {
            return "["
                    + "\"hey maatje\","
                    + "\"hee maatje\","
                    + "\"he maatje\","
                    + "\"hey maartje\","
                    + "\"hee maartje\","
                    + "\"he maartje\","
                    + "\"hoi maatje\","
                    + "\"hoi maartje\","
                    + "\"hey maat\","
                    + "\"hee maat\","
                    + "\"he maat\","
                    + "\"maatje\","
                    + "\"maartje\","
                    + "\"maat\","
                    + "\"[unk]\""
                    + "]";
        }

        if (sensitivity >= 45) {
            return "["
                    + "\"hey maatje\","
                    + "\"hee maatje\","
                    + "\"he maatje\","
                    + "\"hey maartje\","
                    + "\"hee maartje\","
                    + "\"he maartje\","
                    + "\"hoi maatje\","
                    + "\"hoi maartje\","
                    + "\"hey maat\","
                    + "\"hee maat\","
                    + "\"he maat\","
                    + "\"[unk]\""
                    + "]";
        }

        return "["
                + "\"hey maatje\","
                + "\"hee maatje\","
                + "\"he maatje\","
                + "\"hey maartje\","
                + "\"hee maartje\","
                + "\"he maartje\","
                + "\"[unk]\""
                + "]";
    }
}
