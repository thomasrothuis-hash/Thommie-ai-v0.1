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
        if (!ready
                || running
                || model == null) {
            return;
        }

        detected = false;

        try {
            Recognizer recognizer =
                    new Recognizer(
                            model,
                            16000.0f
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
    public void onPartialResult(
            String hypothesis
    ) {
        inspect(hypothesis);
    }

    @Override
    public void onResult(
            String hypothesis
    ) {
        inspect(hypothesis);
    }

    @Override
    public void onFinalResult(
            String hypothesis
    ) {
        inspect(hypothesis);
    }

    @Override
    public void onError(
            Exception exception
    ) {
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

    private void inspect(
            String hypothesis
    ) {
        if (detected
                || hypothesis == null
                || hypothesis.isEmpty()) {
            return;
        }

        String text = extractText(hypothesis);

        if (text.isEmpty()) return;

        String normalized =
                text.toLowerCase(Locale.ROOT)
                        .replaceAll(
                                "[^\\p{L}\\p{N}\\s]",
                                " "
                        )
                        .replaceAll(
                                "\\s+",
                                " "
                        )
                        .trim();

        if (containsWakePhrase(normalized)) {
            detected = true;
            stop();

            activity.runOnUiThread(
                    callback::onDetected
            );
        }
    }

    private String extractText(
            String json
    ) {
        try {
            JSONObject root =
                    new JSONObject(json);

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

    private boolean containsWakePhrase(
            String value
    ) {
        return value.contains("hey maatje")
                || value.contains("hee maatje")
                || value.contains("hé maatje")
                || value.contains("hey maartje")
                || value.contains("hee maartje")
                || value.contains("hé maartje");
    }
}
