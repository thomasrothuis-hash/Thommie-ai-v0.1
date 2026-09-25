package nl.thommie.ai;

import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.service.voice.VoiceInteractionSession;
import android.speech.RecognitionListener;
import android.speech.RecognitionService;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class MaatjeVoiceInteractionSession
        extends VoiceInteractionSession {

    private static final int BG =
            Color.rgb(5, 10, 7);
    private static final int PANEL =
            Color.rgb(10, 20, 13);
    private static final int GREEN =
            Color.rgb(54, 220, 104);
    private static final int TEXT =
            Color.rgb(224, 241, 228);
    private static final int MUTED =
            Color.rgb(114, 145, 121);
    private static final int BORDER =
            Color.rgb(29, 70, 42);

    private final Context context;
    private final Handler mainHandler =
            new Handler(
                    Looper.getMainLooper()
            );

    private final ExecutorService chatExecutor =
            Executors.newSingleThreadExecutor();

    private final ExecutorService voiceExecutor =
            Executors.newSingleThreadExecutor();

    private SpeechRecognizer speechRecognizer;
    private MediaPlayer mediaPlayer;

    private TextView statusText;
    private TextView queryText;
    private TextView answerText;
    private TextView footerText;
    private Button micButton;

    private boolean listening = false;
    private boolean thinking = false;
    private boolean speaking = false;
    private boolean sessionVisible = false;

    MaatjeVoiceInteractionSession(
            Context context
    ) {
        super(context);
        this.context = context;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        setKeepAwake(true);
        setUiEnabled(true);

        createSpeechRecognizer();
    }

    @Override
    public View onCreateContentView() {
        FrameLayout root =
                new FrameLayout(context);

        root.setBackgroundColor(
                Color.TRANSPARENT
        );

        LinearLayout panel =
                new LinearLayout(context);

        panel.setOrientation(
                LinearLayout.VERTICAL
        );

        panel.setPadding(
                dp(18),
                dp(14),
                dp(18),
                dp(16)
        );

        panel.setBackground(
                roundRect(
                        PANEL,
                        24,
                        BORDER
                )
        );

        LinearLayout header =
                new LinearLayout(context);

        header.setOrientation(
                LinearLayout.HORIZONTAL
        );

        header.setGravity(
                Gravity.CENTER_VERTICAL
        );

        TextView title =
                new TextView(context);

        title.setText(
                "●  MAATJE"
        );

        title.setTextColor(
                GREEN
        );

        title.setTextSize(17);

        title.setTypeface(
                Typeface.create(
                        Typeface.MONOSPACE,
                        Typeface.BOLD
                )
        );

        header.addView(
                title,
                new LinearLayout.LayoutParams(
                        0,
                        dp(40),
                        1f
                )
        );

        micButton =
                new Button(context);

        micButton.setAllCaps(false);
        micButton.setText("🎙");
        micButton.setTextSize(16);
        micButton.setTextColor(GREEN);
        micButton.setBackground(
                roundRect(
                        BG,
                        16,
                        BORDER
                )
        );

        micButton.setOnClickListener(
                v -> startListening()
        );

        header.addView(
                micButton,
                new LinearLayout.LayoutParams(
                        dp(46),
                        dp(40)
                )
        );

        Button close =
                new Button(context);

        close.setAllCaps(false);
        close.setText("×");
        close.setTextSize(24);
        close.setTextColor(MUTED);
        close.setBackgroundColor(
                Color.TRANSPARENT
        );

        close.setOnClickListener(
                v -> finishOverlay()
        );

        LinearLayout.LayoutParams closeLp =
                new LinearLayout.LayoutParams(
                        dp(44),
                        dp(40)
                );

        closeLp.leftMargin =
                dp(4);

        header.addView(
                close,
                closeLp
        );

        panel.addView(header);

        statusText =
                new TextView(context);

        statusText.setText(
                "READY"
        );

        statusText.setTextColor(
                GREEN
        );

        statusText.setTextSize(11);

        statusText.setTypeface(
                Typeface.create(
                        Typeface.MONOSPACE,
                        Typeface.BOLD
                )
        );

        statusText.setLetterSpacing(
                .16f
        );

        statusText.setPadding(
                0,
                dp(3),
                0,
                dp(9)
        );

        panel.addView(statusText);

        queryText =
                new TextView(context);

        queryText.setTextColor(
                MUTED
        );

        queryText.setTextSize(14);

        queryText.setMaxLines(2);

        queryText.setText(
                "Zeg iets..."
        );

        panel.addView(queryText);

        answerText =
                new TextView(context);

        answerText.setTextColor(
                TEXT
        );

        answerText.setTextSize(16);

        answerText.setLineSpacing(
                0,
                1.16f
        );

        answerText.setMaxLines(7);

        answerText.setPadding(
                0,
                dp(8),
                0,
                0
        );

        panel.addView(answerText);

        footerText =
                new TextView(context);

        footerText.setTextColor(
                MUTED
        );

        footerText.setTextSize(10);

        footerText.setTypeface(
                Typeface.MONOSPACE
        );

        footerText.setPadding(
                0,
                dp(10),
                0,
                0
        );

        footerText.setText(
                "ASSISTANT OVERLAY"
        );

        panel.addView(footerText);

        FrameLayout.LayoutParams panelLp =
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams
                                .MATCH_PARENT,
                        FrameLayout.LayoutParams
                                .WRAP_CONTENT,
                        Gravity.BOTTOM
                );

        panelLp.leftMargin =
                dp(12);
        panelLp.rightMargin =
                dp(12);
        panelLp.bottomMargin =
                dp(16);

        root.addView(
                panel,
                panelLp
        );

        return root;
    }

    @Override
    public void onShow(
            Bundle args,
            int showFlags
    ) {
        super.onShow(
                args,
                showFlags
        );

        sessionVisible = true;

        MaatjeVoiceInteractionService
                .setSessionVisible(true);

        configureWindow();

        resetUi();

        mainHandler.postDelayed(
                this::startListening,
                260L
        );
    }

    @Override
    public void onHide() {
        sessionVisible = false;

        stopListening();
        stopPlayer();

        MaatjeVoiceInteractionService
                .setSessionVisible(false);

        super.onHide();
    }

    @Override
    public void onCloseSystemDialogs() {
        finishOverlay();
    }

    @Override
    public void onDestroy() {
        sessionVisible = false;

        mainHandler
                .removeCallbacksAndMessages(
                        null
                );

        stopListening();
        stopPlayer();

        if (speechRecognizer != null) {
            try {
                speechRecognizer.destroy();
            } catch (Exception ignored) {}

            speechRecognizer = null;
        }

        chatExecutor.shutdownNow();
        voiceExecutor.shutdownNow();

        MaatjeVoiceInteractionService
                .setSessionVisible(false);

        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        finishOverlay();
    }

    private void configureWindow() {
        try {
            Dialog dialog =
                    getWindow();

            if (dialog == null) {
                return;
            }

            Window window =
                    dialog.getWindow();

            if (window == null) {
                return;
            }

            window.setBackgroundDrawable(
                    new ColorDrawable(
                            Color.TRANSPARENT
                    )
            );

            window.clearFlags(
                    WindowManager.LayoutParams
                            .FLAG_DIM_BEHIND
            );

            window.setGravity(
                    Gravity.BOTTOM
            );

            window.setLayout(
                    WindowManager.LayoutParams
                            .MATCH_PARENT,
                    WindowManager.LayoutParams
                            .WRAP_CONTENT
            );

        } catch (Exception ignored) {}
    }

    private void resetUi() {
        thinking = false;
        speaking = false;
        listening = false;

        if (statusText != null) {
            statusText.setText(
                    "LISTENING"
            );
        }

        if (queryText != null) {
            queryText.setText(
                    "Luistert..."
            );
        }

        if (answerText != null) {
            answerText.setText("");
        }

        if (footerText != null) {
            footerText.setText(
                    UsageTracker.compactLine(
                            context
                    )
            );
        }

        setMicEnabled(true);
    }

    private void createSpeechRecognizer() {
        if (!SpeechRecognizer
                .isRecognitionAvailable(
                        context
                )) {
            return;
        }

        ComponentName backend =
                findExternalRecognizer();

        try {
            speechRecognizer =
                    backend == null
                            ? SpeechRecognizer
                            .createSpeechRecognizer(
                                    context
                            )
                            : SpeechRecognizer
                            .createSpeechRecognizer(
                                    context,
                                    backend
                            );

            speechRecognizer
                    .setRecognitionListener(
                            new RecognitionListener() {
                                @Override
                                public void onReadyForSpeech(
                                        Bundle params
                                ) {
                                    listening = true;
                                    setStatus(
                                            "LISTENING"
                                    );
                                }

                                @Override
                                public void onBeginningOfSpeech() {
                                    setStatus(
                                            "LISTENING"
                                    );
                                }

                                @Override
                                public void onRmsChanged(
                                        float rmsdB
                                ) {}

                                @Override
                                public void onBufferReceived(
                                        byte[] buffer
                                ) {}

                                @Override
                                public void onEndOfSpeech() {
                                    if (listening) {
                                        setStatus(
                                                "PROCESSING SPEECH"
                                        );
                                    }
                                }

                                @Override
                                public void onError(
                                        int error
                                ) {
                                    listening = false;

                                    if (!sessionVisible) {
                                        return;
                                    }

                                    setStatus(
                                            "TAP MIC TO RETRY"
                                    );

                                    if (queryText != null) {
                                        queryText.setText(
                                                "Ik verstond je niet."
                                        );
                                    }

                                    setMicEnabled(true);
                                }

                                @Override
                                public void onResults(
                                        Bundle results
                                ) {
                                    listening = false;

                                    ArrayList<String> list =
                                            results
                                                    .getStringArrayList(
                                                            SpeechRecognizer
                                                                    .RESULTS_RECOGNITION
                                                    );

                                    if (list == null
                                            || list.isEmpty()) {
                                        setStatus(
                                                "TAP MIC TO RETRY"
                                        );

                                        setMicEnabled(true);
                                        return;
                                    }

                                    String query =
                                            formatRecognizedSpeech(
                                                    list.get(0)
                                            );

                                    handleQuery(query);
                                }

                                @Override
                                public void onPartialResults(
                                        Bundle partialResults
                                ) {
                                    ArrayList<String> list =
                                            partialResults
                                                    .getStringArrayList(
                                                            SpeechRecognizer
                                                                    .RESULTS_RECOGNITION
                                                    );

                                    if (list != null
                                            && !list.isEmpty()
                                            && queryText != null) {
                                        queryText.setText(
                                                list.get(0)
                                        );
                                    }
                                }

                                @Override
                                public void onEvent(
                                        int eventType,
                                        Bundle params
                                ) {}
                            }
                    );

        } catch (Exception e) {
            speechRecognizer = null;
        }
    }

    private ComponentName findExternalRecognizer() {
        PackageManager pm =
                context.getPackageManager();

        Intent probe =
                new Intent(
                        RecognitionService
                                .SERVICE_INTERFACE
                );

        List<ResolveInfo> services =
                pm.queryIntentServices(
                        probe,
                        0
                );

        if (services == null) {
            return null;
        }

        ResolveInfo fallback = null;

        for (ResolveInfo info : services) {
            if (info == null
                    || info.serviceInfo == null) {
                continue;
            }

            String packageName =
                    info.serviceInfo.packageName;

            if (context
                    .getPackageName()
                    .equals(packageName)) {
                continue;
            }

            if (fallback == null) {
                fallback = info;
            }

            String lower =
                    packageName == null
                            ? ""
                            : packageName
                            .toLowerCase(
                                    Locale.ROOT
                            );

            if (lower.contains("google")
                    || lower.contains("lineage")
                    || lower.contains("speech")) {
                return new ComponentName(
                        packageName,
                        info.serviceInfo.name
                );
            }
        }

        if (fallback == null) {
            return null;
        }

        return new ComponentName(
                fallback.serviceInfo.packageName,
                fallback.serviceInfo.name
        );
    }

    private void startListening() {
        if (!sessionVisible
                || thinking
                || speaking) {
            return;
        }

        if (speechRecognizer == null) {
            createSpeechRecognizer();
        }

        if (speechRecognizer == null) {
            setStatus(
                    "NO SPEECH RECOGNIZER"
            );

            if (answerText != null) {
                answerText.setText(
                        "Geen Android-spraakherkenner beschikbaar."
                );
            }

            return;
        }

        stopListening();

        if (queryText != null) {
            queryText.setText(
                    "Luistert..."
            );
        }

        if (answerText != null) {
            answerText.setText("");
        }

        setStatus(
                "LISTENING"
        );

        setMicEnabled(false);

        try {
            speechRecognizer.startListening(
                    createRecognizerIntent()
            );

            listening = true;

        } catch (Exception e) {
            listening = false;
            setMicEnabled(true);

            setStatus(
                    "LISTEN ERROR"
            );
        }
    }

    private Intent createRecognizerIntent() {
        Intent intent =
                new Intent(
                        RecognizerIntent
                                .ACTION_RECOGNIZE_SPEECH
                );

        intent.putExtra(
                RecognizerIntent
                        .EXTRA_LANGUAGE_MODEL,
                RecognizerIntent
                        .LANGUAGE_MODEL_FREE_FORM
        );

        intent.putExtra(
                RecognizerIntent
                        .EXTRA_LANGUAGE,
                "nl-NL"
        );

        intent.putExtra(
                RecognizerIntent
                        .EXTRA_PARTIAL_RESULTS,
                true
        );

        intent.putExtra(
                RecognizerIntent
                        .EXTRA_MAX_RESULTS,
                5
        );

        if (android.os.Build.VERSION.SDK_INT
                >= 33) {
            intent.putExtra(
                    RecognizerIntent
                            .EXTRA_ENABLE_FORMATTING,
                    RecognizerIntent
                            .FORMATTING_OPTIMIZE_QUALITY
            );
        }

        return intent;
    }

    private void stopListening() {
        boolean wasListening =
                listening;

        listening = false;

        if (wasListening
                && speechRecognizer != null) {
            try {
                speechRecognizer.cancel();
            } catch (Exception ignored) {}
        }

        setMicEnabled(true);
    }

    private void handleQuery(
            String query
    ) {
        stopListening();

        if (queryText != null) {
            queryText.setText(
                    "JIJ • " + query
            );
        }

        String normalized =
                query.toLowerCase(
                        Locale.ROOT
                );

        if (normalized.equals("stop")
                || normalized.contains(
                "maatje stop"
        )
                || normalized.contains(
                "ga maar weg"
        )) {
            finishOverlay();
            return;
        }

        InternetSettings.CommandResult
                internetCommand =
                InternetSettings
                        .handleCommand(
                                context,
                                query
                        );

        if (internetCommand.handled) {
            showLocalAnswer(
                    internetCommand.message
            );
            return;
        }

        PersonalitySettings.CommandResult
                personalityCommand =
                PersonalitySettings
                        .handleCommand(
                                context,
                                query
                        );

        if (personalityCommand.handled) {
            showLocalAnswer(
                    personalityCommand.message
            );
            return;
        }

        AssistantOverlayActions.Result
                action =
                AssistantOverlayActions
                        .handle(
                                context,
                                query
                        );

        if (action.handled) {
            showLocalAnswer(
                    action.message
            );
            return;
        }

        boolean remembered =
                MemoryStore
                        .captureExplicitMemory(
                                context,
                                query
                        );

        if (remembered) {
            showLocalAnswer(
                    "Opgeslagen in lokaal profielgeheugen."
            );
            return;
        }

        String key =
                SecurePrefs
                        .loadApiKey(
                                context
                        );

        if (key.isEmpty()) {
            showLocalAnswer(
                    "Open de gewone MAATJE-app één keer en vul daar je API-key in."
            );
            return;
        }

        thinking = true;
        setMicEnabled(false);

        setStatus(
                InternetSettings
                        .enabled(context)
                        ? "THINKING • WEB AUTO"
                        : "THINKING"
        );

        if (answerText != null) {
            answerText.setText(
                    "Even denken..."
            );
        }

        chatExecutor.submit(() -> {
            try {
                String conversationId =
                        MemoryStore
                                .getConversationId(
                                        context
                                );

                if (conversationId.isEmpty()) {
                    conversationId =
                            OpenAiClient
                                    .createConversation(
                                            key
                                    );

                    MemoryStore
                            .setConversationId(
                                    context,
                                    conversationId
                            );
                }

                OpenAiClient.Reply reply =
                        OpenAiClient.ask(
                                key,
                                "gpt-5.6-luna",
                                conversationId,
                                query,
                                MemoryStore
                                        .getProfile(
                                                context
                                        ),
                                PersonalitySettings
                                        .prompt(
                                                context
                                        ),
                                InternetSettings
                                        .enabled(
                                                context
                                        )
                        );

                UsageTracker.record(
                        context,
                        reply.inputTokens,
                        reply.outputTokens,
                        reply.totalTokens
                );

                mainHandler.post(() -> {
                    if (!sessionVisible) {
                        return;
                    }

                    thinking = false;

                    if (answerText != null) {
                        answerText.setText(
                                reply.text
                        );
                    }

                    if (footerText != null) {
                        footerText.setText(
                                reply.webUsed
                                        ? UsageTracker
                                        .compactLine(
                                                context
                                        )
                                        + " • WEB"
                                        : UsageTracker
                                        .compactLine(
                                                context
                                        )
                        );
                    }

                    speakOrFinish(
                            reply.text
                    );
                });

            } catch (Exception e) {
                mainHandler.post(() -> {
                    thinking = false;

                    if (!sessionVisible) {
                        return;
                    }

                    setStatus(
                            "ERROR"
                    );

                    if (answerText != null) {
                        answerText.setText(
                                e.getMessage() == null
                                        ? "Er ging iets mis."
                                        : e.getMessage()
                        );
                    }

                    setMicEnabled(true);
                });
            }
        });
    }

    private void showLocalAnswer(
            String message
    ) {
        thinking = false;

        if (answerText != null) {
            answerText.setText(
                    message
            );
        }

        if (footerText != null) {
            footerText.setText(
                    "LOCAL • "
                            + UsageTracker
                            .compactLine(
                                    context
                            )
            );
        }

        speakOrFinish(message);
    }

    private void speakOrFinish(
            String text
    ) {
        if (!VoiceSettings
                .autoSpeak(
                        context
                )) {
            setStatus(
                    "READY"
            );

            setMicEnabled(true);

            mainHandler.postDelayed(
                    this::finishOverlay,
                    5500L
            );

            return;
        }

        String key =
                SecurePrefs
                        .loadApiKey(
                                context
                        );

        if (key.isEmpty()) {
            setStatus("READY");
            setMicEnabled(true);
            return;
        }

        speaking = true;

        setStatus(
                "PREPARING VOICE"
        );

        String cleaned =
                text == null
                        ? ""
                        : text.replace(
                                "**",
                                ""
                        )
                        .trim();

        String style =
                VoiceSettings.style(
                        context
                )
                        + "\n"
                        + PersonalitySettings
                        .voiceStyle(
                                context
                        );

        voiceExecutor.submit(() -> {
            try {
                File file =
                        OpenAiSpeech
                                .synthesize(
                                        context,
                                        key,
                                        cleaned,
                                        VoiceSettings
                                                .voice(
                                                        context
                                                ),
                                        style,
                                        VoiceSettings
                                                .speed(
                                                        context
                                                )
                                );

                mainHandler.post(
                        () -> playAudio(
                                file
                        )
                );

            } catch (Exception e) {
                mainHandler.post(() -> {
                    speaking = false;

                    if (!sessionVisible) {
                        return;
                    }

                    setStatus(
                            "READY"
                    );

                    setMicEnabled(true);

                    mainHandler.postDelayed(
                            this::finishOverlay,
                            4500L
                    );
                });
            }
        });
    }

    private void playAudio(
            File file
    ) {
        if (!sessionVisible) {
            if (file != null) {
                file.delete();
            }

            speaking = false;
            return;
        }

        stopPlayer();

        try {
            mediaPlayer =
                    new MediaPlayer();

            mediaPlayer
                    .setAudioAttributes(
                            new AudioAttributes
                                    .Builder()
                                    .setUsage(
                                            AudioAttributes
                                                    .USAGE_ASSISTANT
                                    )
                                    .setContentType(
                                            AudioAttributes
                                                    .CONTENT_TYPE_SPEECH
                                    )
                                    .build()
                    );

            mediaPlayer.setVolume(
                    1f,
                    1f
            );

            mediaPlayer.setDataSource(
                    file.getAbsolutePath()
            );

            mediaPlayer
                    .setOnPreparedListener(
                            mp -> {
                                speaking = true;

                                setStatus(
                                        "SPEAKING"
                                );

                                mp.start();
                            }
                    );

            mediaPlayer
                    .setOnCompletionListener(
                            mp -> {
                                try {
                                    mp.release();
                                } catch (Exception ignored) {}

                                if (mediaPlayer == mp) {
                                    mediaPlayer = null;
                                }

                                speaking = false;

                                file.delete();

                                if (!sessionVisible) {
                                    return;
                                }

                                setStatus(
                                        "READY"
                                );

                                setMicEnabled(true);

                                mainHandler.postDelayed(
                                        this::finishOverlay,
                                        1500L
                                );
                            }
                    );

            mediaPlayer
                    .setOnErrorListener(
                            (mp, what, extra) -> {
                                try {
                                    mp.release();
                                } catch (Exception ignored) {}

                                if (mediaPlayer == mp) {
                                    mediaPlayer = null;
                                }

                                speaking = false;

                                file.delete();

                                setStatus(
                                        "READY"
                                );

                                setMicEnabled(true);

                                return true;
                            }
                    );

            mediaPlayer.prepareAsync();

        } catch (Exception e) {
            speaking = false;

            if (file != null) {
                file.delete();
            }

            setStatus(
                    "READY"
            );

            setMicEnabled(true);
        }
    }

    private void stopPlayer() {
        speaking = false;

        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
            } catch (Exception ignored) {}

            try {
                mediaPlayer.release();
            } catch (Exception ignored) {}

            mediaPlayer = null;
        }
    }

    private void finishOverlay() {
        if (!sessionVisible) {
            return;
        }

        sessionVisible = false;

        stopListening();
        stopPlayer();

        MaatjeVoiceInteractionService
                .setSessionVisible(false);

        try {
            hide();
        } catch (Exception ignored) {}

        try {
            finish();
        } catch (Exception ignored) {}
    }

    private void setStatus(
            String value
    ) {
        if (statusText != null) {
            statusText.setText(value);
        }
    }

    private void setMicEnabled(
            boolean enabled
    ) {
        if (micButton != null) {
            micButton.setEnabled(enabled);
            micButton.setAlpha(
                    enabled
                            ? 1f
                            : .35f
            );
        }
    }

    private String formatRecognizedSpeech(
            String raw
    ) {
        if (raw == null) {
            return "";
        }

        String value =
                raw.trim()
                        .replaceAll(
                                "\\s+",
                                " "
                        );

        if (value.isEmpty()) {
            return value;
        }

        value =
                Character
                        .toUpperCase(
                                value.charAt(0)
                        )
                        + value.substring(1);

        char last =
                value.charAt(
                        value.length() - 1
                );

        if (last != '.'
                && last != '?'
                && last != '!') {
            value += ".";
        }

        return value;
    }

    private int dp(
            int value
    ) {
        return Math.round(
                value
                        * context
                        .getResources()
                        .getDisplayMetrics()
                        .density
        );
    }

    private GradientDrawable roundRect(
            int color,
            int radiusDp,
            int stroke
    ) {
        GradientDrawable drawable =
                new GradientDrawable();

        drawable.setColor(color);

        drawable.setCornerRadius(
                dp(radiusDp)
        );

        drawable.setStroke(
                dp(1),
                stroke
        );

        return drawable;
    }
}
