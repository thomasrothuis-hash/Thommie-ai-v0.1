package nl.thommie.ai;

import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
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
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
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
    private Bitmap latestScreenshot;
    private RealtimeVoiceClient realtimeVoiceClient;

    private AudioWaveformView waveformView;
    private TextView statusText;
    private TextView queryText;
    private TextView answerText;
    private TextView footerText;
    private Button micButton;

    private boolean listening = false;
    private boolean thinking = false;
    private boolean speaking = false;
    private boolean sessionVisible = false;
    private boolean realtimeConnected = false;
    private boolean realtimeFallbackStarted = false;
    private int speechRetryCount = 0;

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
    }

    @Override
    public View onCreateContentView() {
        FrameLayout root =
                new FrameLayout(context);

        root.setBackgroundColor(
                Color.TRANSPARENT
        );

        View outerGlow =
                new View(context);
        outerGlow.setBackground(
                edgeStroke(
                        Color.argb(
                                24,
                                54,
                                220,
                                104
                        ),
                        10,
                        28
                )
        );
        outerGlow.setClickable(false);

        FrameLayout.LayoutParams outerGlowLp =
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                );
        outerGlowLp.setMargins(
                dp(1),
                dp(1),
                dp(1),
                dp(1)
        );
        root.addView(
                outerGlow,
                outerGlowLp
        );

        View middleGlow =
                new View(context);
        middleGlow.setBackground(
                edgeStroke(
                        Color.argb(
                                52,
                                54,
                                220,
                                104
                        ),
                        5,
                        25
                )
        );
        middleGlow.setClickable(false);

        FrameLayout.LayoutParams middleGlowLp =
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                );
        middleGlowLp.setMargins(
                dp(3),
                dp(3),
                dp(3),
                dp(3)
        );
        root.addView(
                middleGlow,
                middleGlowLp
        );

        View innerGlow =
                new View(context);
        innerGlow.setBackground(
                edgeStroke(
                        Color.argb(
                                145,
                                54,
                                220,
                                104
                        ),
                        1,
                        22
                )
        );
        innerGlow.setClickable(false);

        FrameLayout.LayoutParams innerGlowLp =
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                );
        innerGlowLp.setMargins(
                dp(7),
                dp(7),
                dp(7),
                dp(7)
        );
        root.addView(
                innerGlow,
                innerGlowLp
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
                v -> {
                    if (realtimeVoiceClient == null
                            || !realtimeVoiceClient.isRunning()) {
                        startRealtimeVoice();
                    }
                }
        );

        header.addView(
                micButton,
                new LinearLayout.LayoutParams(
                        dp(46),
                        dp(40)
                )
        );

        TextView close =
                new TextView(context);

        close.setText("×");
        close.setTextSize(25);
        close.setTextColor(MUTED);
        close.setGravity(
                Gravity.CENTER
        );
        close.setIncludeFontPadding(false);
        close.setPadding(
                0,
                0,
                0,
                0
        );
        close.setBackgroundColor(
                Color.TRANSPARENT
        );
        close.setClickable(true);

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

        waveformView =
                new AudioWaveformView(context);
        waveformView.setMode(
                AudioWaveformView.MODE_IDLE
        );

        LinearLayout.LayoutParams waveformLp =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(46)
                );
        waveformLp.bottomMargin = dp(7);
        panel.addView(
                waveformView,
                waveformLp
        );

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
    public void onHandleScreenshot(
            Bitmap screenshot
    ) {
        super.onHandleScreenshot(screenshot);

        clearLatestScreenshot();

        if (screenshot == null) {
            return;
        }

        try {
            Bitmap.Config config =
                    screenshot.getConfig();

            if (config == null
                    || config == Bitmap.Config.HARDWARE) {
                config = Bitmap.Config.ARGB_8888;
            }

            latestScreenshot =
                    screenshot.copy(
                            config,
                            false
                    );
        } catch (Exception ignored) {
            latestScreenshot = null;
        }
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
                .claimMicrophoneForSession();

        configureWindow();

        resetUi();

        mainHandler.postDelayed(
                this::startRealtimeVoice,
                220L
        );
    }

    @Override
    public void onHide() {
        sessionVisible = false;

        stopRealtimeVoice();
        stopListening();
        destroySpeechRecognizer();
        stopPlayer();
        clearLatestScreenshot();

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

        stopRealtimeVoice();
        stopListening();
        stopPlayer();
        clearLatestScreenshot();

        destroySpeechRecognizer();

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
                    Gravity.FILL
            );

            window.setLayout(
                    WindowManager.LayoutParams
                            .MATCH_PARENT,
                    WindowManager.LayoutParams
                            .MATCH_PARENT
            );

        } catch (Exception ignored) {}
    }

    private void resetUi() {
        thinking = false;
        speaking = false;
        listening = false;
        realtimeConnected = false;
        realtimeFallbackStarted = false;

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

        if (waveformView != null) {
            waveformView.setMode(
                    AudioWaveformView.MODE_IDLE
            );
        }

        setMicEnabled(true);
    }

    private void startRealtimeVoice() {
        if (!sessionVisible) {
            return;
        }

        if (context.checkSelfPermission(
                android.Manifest.permission.RECORD_AUDIO
        ) != PackageManager.PERMISSION_GRANTED) {
            setStatus("MIC • PERMISSION");

            if (queryText != null) {
                queryText.setText(
                        "Microfoontoegang ontbreekt voor MAATJE."
                );
            }

            setMicEnabled(true);
            return;
        }

        String key =
                SecurePrefs.loadApiKey(context);

        if (key.isEmpty()) {
            showLocalAnswer(
                    "Open de gewone MAATJE-app één keer en vul daar je API-key in."
            );
            return;
        }

        stopListening();
        destroySpeechRecognizer();
        stopPlayer();
        stopRealtimeVoice();

        realtimeFallbackStarted = false;
        realtimeConnected = false;
        listening = false;
        speaking = false;
        thinking = false;

        setStatus("CONNECTING • REALTIME");

        if (queryText != null) {
            queryText.setText(
                    "Realtime verbinding opzetten..."
            );
        }

        if (answerText != null) {
            answerText.setText("");
        }

        if (footerText != null) {
            footerText.setText(
                    "GPT-REALTIME-2.1 • CONNECTING"
            );
        }

        if (waveformView != null) {
            waveformView.setMode(
                    AudioWaveformView.MODE_IDLE
            );
        }

        setMicEnabled(false);

        realtimeVoiceClient =
                new RealtimeVoiceClient(
                        context,
                        new RealtimeVoiceClient.Listener() {
                            @Override
                            public void onConnected() {
                                mainHandler.post(() -> {
                                    if (!sessionVisible) {
                                        return;
                                    }

                                    realtimeConnected = true;
                                    realtimeFallbackStarted = false;
                                    setStatus(
                                            "LISTENING • REALTIME"
                                    );

                                    if (queryText != null) {
                                        queryText.setText(
                                                "Praat maar..."
                                        );
                                    }

                                    if (footerText != null) {
                                        footerText.setText(
                                                "GPT-REALTIME-2.1 • LIVE"
                                        );
                                    }

                                    if (waveformView != null) {
                                        waveformView.setMode(
                                                AudioWaveformView.MODE_IDLE
                                        );
                                    }

                                    setMicEnabled(false);
                                });
                            }

                            @Override
                            public void onDisconnected() {
                                mainHandler.post(() -> {
                                    realtimeConnected = false;

                                    if (!sessionVisible) {
                                        return;
                                    }

                                    setStatus(
                                            "REALTIME • DISCONNECTED"
                                    );
                                    setMicEnabled(true);
                                });
                            }

                            @Override
                            public void onError(
                                    String message
                            ) {
                                mainHandler.post(() -> {
                                    if (!sessionVisible) {
                                        return;
                                    }

                                    if (!realtimeConnected
                                            && !realtimeFallbackStarted) {
                                        realtimeFallbackStarted = true;

                                        if (footerText != null) {
                                            footerText.setText(
                                                    "REALTIME FAILED • FALLBACK"
                                            );
                                        }

                                        stopRealtimeVoice();

                                        mainHandler.postDelayed(
                                                MaatjeVoiceInteractionSession.this
                                                        ::startListening,
                                                220L
                                        );
                                        return;
                                    }

                                    setStatus(
                                            "REALTIME • ERROR"
                                    );

                                    if (footerText != null) {
                                        footerText.setText(
                                                message == null
                                                        ? "REALTIME API ERROR"
                                                        : message
                                        );
                                    }
                                });
                            }

                            @Override
                            public void onUserSpeechStarted() {
                                mainHandler.post(() -> {
                                    if (!sessionVisible) {
                                        return;
                                    }

                                    listening = true;
                                    speaking = false;
                                    thinking = false;

                                    setStatus(
                                            "LISTENING • LIVE"
                                    );

                                    if (waveformView != null) {
                                        waveformView.setMode(
                                                AudioWaveformView.MODE_USER
                                        );
                                    }
                                });
                            }

                            @Override
                            public void onUserSpeechStopped() {
                                mainHandler.post(() -> {
                                    if (!sessionVisible) {
                                        return;
                                    }

                                    listening = false;
                                    thinking = true;

                                    setStatus(
                                            "RESPONDING • LIVE"
                                    );

                                    if (waveformView != null) {
                                        waveformView.setMode(
                                                AudioWaveformView.MODE_IDLE
                                        );
                                    }
                                });
                            }

                            @Override
                            public void onUserTranscript(
                                    String text,
                                    boolean complete
                            ) {
                                mainHandler.post(() -> {
                                    if (!sessionVisible) {
                                        return;
                                    }

                                    if (queryText != null
                                            && text != null
                                            && !text.trim().isEmpty()) {
                                        queryText.setText(
                                                "JIJ • "
                                                        + text.trim()
                                        );
                                    }

                                    if (complete
                                            && text != null
                                            && !text.trim().isEmpty()) {
                                        handleRealtimeTranscript(
                                                text.trim()
                                        );
                                    }
                                });
                            }

                            @Override
                            public void onAssistantTranscript(
                                    String text,
                                    boolean complete
                            ) {
                                mainHandler.post(() -> {
                                    if (!sessionVisible
                                            || text == null) {
                                        return;
                                    }

                                    String cleaned =
                                            cleanAssistantText(
                                                    text
                                            );

                                    if (!cleaned.isEmpty()
                                            && answerText != null) {
                                        answerText.setText(
                                                cleaned
                                        );
                                    }
                                });
                            }

                            @Override
                            public void onAssistantSpeaking(
                                    boolean active
                            ) {
                                mainHandler.post(() -> {
                                    if (!sessionVisible) {
                                        return;
                                    }

                                    speaking = active;
                                    thinking = false;

                                    if (active) {
                                        setStatus(
                                                "SPEAKING • REALTIME"
                                        );

                                        if (waveformView != null) {
                                            waveformView.setMode(
                                                    AudioWaveformView
                                                            .MODE_ASSISTANT
                                            );
                                        }
                                    } else {
                                        setStatus(
                                                "LISTENING • REALTIME"
                                        );

                                        if (waveformView != null) {
                                            waveformView.setMode(
                                                    AudioWaveformView
                                                            .MODE_IDLE
                                            );
                                        }
                                    }
                                });
                            }

                            @Override
                            public void onMicPcm(
                                    short[] samples,
                                    int length
                            ) {
                                AudioWaveformView view =
                                        waveformView;

                                if (view != null
                                        && listening) {
                                    view.pushPcm16(
                                            samples,
                                            length
                                    );
                                }
                            }

                            @Override
                            public void onAssistantPcm(
                                    byte[] pcm
                            ) {
                                AudioWaveformView view =
                                        waveformView;

                                if (view != null) {
                                    view.pushPcm16(pcm);
                                }
                            }
                        }
                );

        realtimeVoiceClient.start(
                key,
                buildRealtimeInstructions(),
                VoiceSettings.voice(context)
        );
    }

    private String buildRealtimeInstructions() {
        StringBuilder prompt =
                new StringBuilder();

        prompt.append(
                "Je bent MAATJE, de persoonlijke realtime spraakassistent van de gebruiker. "
        );
        prompt.append(
                "Praat standaard Nederlands. Reageer snel, natuurlijk, direct en menselijk. "
        );
        prompt.append(
                "Gewone antwoorden zijn meestal 1 tot 3 korte zinnen. Ga alleen uitgebreid als de gebruiker daarom vraagt. "
        );
        prompt.append(
                "Gebruik een kalme, zelfverzekerde, vrouwelijke assistentstijl. Geen klantenservice-toon en geen overdreven enthousiasme. "
        );
        prompt.append(
                "De gebruiker mag je onderbreken; stop dan meteen en luister naar de nieuwe vraag. "
        );
        prompt.append(
                "Bij een vraag over wat er op het scherm staat, zeg hooguit kort dat je kijkt; de app stuurt daarna automatisch de screenshot. "
        );
        prompt.append(
                "Bij lokale telefooncommando's kan de Android-app de actie zelf uitvoeren; verzin nooit dat een actie gelukt is als je daar geen resultaat van hebt. "
        );

        String voiceStyle =
                VoiceSettings.style(context);

        if (voiceStyle != null
                && !voiceStyle.trim().isEmpty()) {
            prompt.append("\n\nStemstijl:\n");
            prompt.append(
                    voiceStyle.trim()
            );
        }

        String personality =
                PersonalitySettings.prompt(context);

        if (personality != null
                && !personality.trim().isEmpty()) {
            prompt.append("\n\nPersoonlijkheid:\n");
            prompt.append(
                    personality.trim()
            );
        }

        String memory =
                MemoryStore.getProfile(context);

        if (memory != null
                && !memory.trim().isEmpty()) {
            prompt.append(
                    "\n\nRelevant langetermijngeheugen over de gebruiker:\n"
            );
            prompt.append(
                    memory.trim()
            );
        }

        return prompt.toString();
    }

    private void handleRealtimeTranscript(
            String query
    ) {
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
            if (realtimeVoiceClient != null) {
                realtimeVoiceClient.cancelResponse();
            }

            finishOverlay();
            return;
        }

        if (isScreenVisionQuery(normalized)) {
            handleRealtimeScreenQuery(
                    query,
                    normalized
            );
            return;
        }

        InternetSettings.CommandResult
                internetCommand =
                InternetSettings.handleCommand(
                        context,
                        query
                );

        if (internetCommand.handled) {
            if (realtimeVoiceClient != null) {
                realtimeVoiceClient.cancelResponse();
            }

            showLocalAnswer(
                    internetCommand.message
            );
            return;
        }

        PersonalitySettings.CommandResult
                personalityCommand =
                PersonalitySettings.handleCommand(
                        context,
                        query
                );

        if (personalityCommand.handled) {
            if (realtimeVoiceClient != null) {
                realtimeVoiceClient.cancelResponse();
            }

            showLocalAnswer(
                    personalityCommand.message
            );
            return;
        }

        AssistantOverlayActions.Result action =
                AssistantOverlayActions.handle(
                        context,
                        query
                );

        if (action.handled) {
            if (realtimeVoiceClient != null) {
                realtimeVoiceClient.cancelResponse();
            }

            showLocalAnswer(
                    action.message
            );
            return;
        }

        boolean remembered =
                MemoryStore.captureExplicitMemory(
                        context,
                        query
                );

        if (remembered) {
            if (realtimeVoiceClient != null) {
                realtimeVoiceClient.cancelResponse();
            }

            showLocalAnswer(
                    "Opgeslagen in lokaal profielgeheugen."
            );
        }
    }

    private void handleRealtimeScreenQuery(
            String query,
            String normalized
    ) {
        RealtimeVoiceClient client =
                realtimeVoiceClient;

        if (client == null
                || !client.isRunning()) {
            handleQuery(query);
            return;
        }

        client.cancelResponse();

        final Bitmap screenshot =
                copyLatestScreenshot();

        if (screenshot == null) {
            setStatus(
                    "SCREEN • UNAVAILABLE"
            );

            if (answerText != null) {
                answerText.setText(
                        "Ik krijg van Android geen screenshot van dit scherm."
                );
            }
            return;
        }

        final boolean highDetail =
                requiresHighVisionDetail(
                        normalized
                );

        setStatus(
                "LOOKING AT SCREEN • LIVE"
        );

        if (answerText != null) {
            answerText.setText(
                    "Even kijken..."
            );
        }

        chatExecutor.submit(() -> {
            try {
                String dataUrl =
                        encodeScreenshot(
                                screenshot,
                                highDetail
                        );

                mainHandler.post(() -> {
                    RealtimeVoiceClient active =
                            realtimeVoiceClient;

                    if (!sessionVisible
                            || active == null
                            || !active.isRunning()) {
                        return;
                    }

                    active.sendImageQuestion(
                            dataUrl,
                            "Beantwoord mijn vorige vraag over wat er op mijn scherm staat. "
                                    + "Gebruik deze screenshot daadwerkelijk. "
                                    + "Mijn vraag was: "
                                    + query
                    );
                });

            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (sessionVisible) {
                        setStatus(
                                "SCREEN • ERROR"
                        );

                        if (answerText != null) {
                            answerText.setText(
                                    e.getMessage() == null
                                            ? "Schermanalyse is mislukt."
                                            : e.getMessage()
                            );
                        }
                    }
                });
            } finally {
                if (!screenshot.isRecycled()) {
                    screenshot.recycle();
                }
            }
        });
    }

    private void stopRealtimeVoice() {
        RealtimeVoiceClient client =
                realtimeVoiceClient;
        realtimeVoiceClient = null;
        realtimeConnected = false;

        if (client != null) {
            client.shutdown();
        }

        if (waveformView != null) {
            waveformView.setMode(
                    AudioWaveformView.MODE_IDLE
            );
        }
    }

    private void createSpeechRecognizer() {
        if (!SpeechRecognizer
                .isRecognitionAvailable(
                        context
                )) {
            return;
        }

        destroySpeechRecognizer();

        try {
            speechRecognizer =
                    SpeechRecognizer
                            .createSpeechRecognizer(
                                    context
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

                                    if (footerText != null) {
                                        footerText.setText(
                                                "MIC • READY"
                                        );
                                    }
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

                                    String errorName =
                                            speechErrorName(
                                                    error
                                            );

                                    if (footerText != null) {
                                        footerText.setText(
                                                "MIC • "
                                                        + errorName
                                                        + " ("
                                                        + error
                                                        + ")"
                                        );
                                    }

                                    boolean retryable =
                                            error
                                                    == SpeechRecognizer.ERROR_AUDIO
                                                    || error
                                                    == SpeechRecognizer.ERROR_CLIENT
                                                    || error
                                                    == SpeechRecognizer.ERROR_RECOGNIZER_BUSY;

                                    if (retryable
                                            && speechRetryCount < 1) {
                                        speechRetryCount++;

                                        setStatus(
                                                "MIC • RETRYING"
                                        );

                                        if (queryText != null) {
                                            queryText.setText(
                                                    "Microfoon opnieuw overnemen..."
                                            );
                                        }

                                        destroySpeechRecognizer();

                                        mainHandler.postDelayed(
                                                MaatjeVoiceInteractionSession.this
                                                        ::startListeningInternal,
                                                420L
                                        );
                                        return;
                                    }

                                    setStatus(
                                            "TAP MIC TO RETRY"
                                    );

                                    if (queryText != null) {
                                        queryText.setText(
                                                error
                                                        == SpeechRecognizer.ERROR_NO_MATCH
                                                        ? "Ik verstond je niet."
                                                        : "Spraakfout: "
                                                        + errorName
                                        );
                                    }

                                    setMicEnabled(true);
                                }

                                @Override
                                public void onResults(
                                        Bundle results
                                ) {
                                    listening = false;
                                    speechRetryCount = 0;

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
        speechRetryCount = 0;
        startListeningInternal();
    }

    private void startListeningInternal() {
        if (!sessionVisible
                || thinking
                || speaking) {
            return;
        }

        if (context.checkSelfPermission(
                android.Manifest.permission.RECORD_AUDIO
        ) != PackageManager.PERMISSION_GRANTED) {
            setStatus(
                    "MIC • PERMISSION"
            );

            if (queryText != null) {
                queryText.setText(
                        "Microfoontoegang ontbreekt voor MAATJE."
                );
            }

            if (footerText != null) {
                footerText.setText(
                        "MIC • PERMISSION DENIED"
                );
            }

            setMicEnabled(true);
            return;
        }

        MaatjeVoiceInteractionService
                .claimMicrophoneForSession();

        stopListening();
        destroySpeechRecognizer();
        createSpeechRecognizer();

        if (speechRecognizer == null) {
            setStatus(
                    "NO SPEECH RECOGNIZER"
            );

            if (answerText != null) {
                answerText.setText(
                        "Geen Android-spraakherkenner beschikbaar."
                );
            }

            if (footerText != null) {
                footerText.setText(
                        "MIC • NO RECOGNIZER"
                );
            }

            setMicEnabled(true);
            return;
        }

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
                1
        );

        if (android.os.Build.VERSION.SDK_INT
                >= 33) {
            intent.putExtra(
                    RecognizerIntent
                            .EXTRA_ENABLE_FORMATTING,
                    RecognizerIntent
                            .FORMATTING_OPTIMIZE_LATENCY
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

    private void destroySpeechRecognizer() {
        if (speechRecognizer != null) {
            try {
                speechRecognizer.cancel();
            } catch (Exception ignored) {}

            try {
                speechRecognizer.destroy();
            } catch (Exception ignored) {}

            speechRecognizer = null;
        }

        listening = false;
    }

    private String speechErrorName(
            int error
    ) {
        switch (error) {
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                return "NETWORK TIMEOUT";
            case SpeechRecognizer.ERROR_NETWORK:
                return "NETWORK";
            case SpeechRecognizer.ERROR_AUDIO:
                return "AUDIO";
            case SpeechRecognizer.ERROR_SERVER:
                return "SERVER";
            case SpeechRecognizer.ERROR_CLIENT:
                return "CLIENT";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                return "SPEECH TIMEOUT";
            case SpeechRecognizer.ERROR_NO_MATCH:
                return "NO MATCH";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                return "RECOGNIZER BUSY";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                return "PERMISSION";
            case SpeechRecognizer.ERROR_TOO_MANY_REQUESTS:
                return "TOO MANY REQUESTS";
            case SpeechRecognizer.ERROR_SERVER_DISCONNECTED:
                return "SERVER DISCONNECTED";
            case SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED:
                return "LANGUAGE NOT SUPPORTED";
            case SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE:
                return "LANGUAGE UNAVAILABLE";
            default:
                return "ERROR " + error;
        }
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

        boolean screenVisionQuery =
                isScreenVisionQuery(
                        normalized
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

        final boolean highVisionDetail =
                screenVisionQuery
                        && requiresHighVisionDetail(
                                normalized
                        );

        final Bitmap screenshotForRequest =
                screenVisionQuery
                        ? copyLatestScreenshot()
                        : null;

        if (screenVisionQuery
                && screenshotForRequest == null) {
            showLocalAnswer(
                    "Ik krijg van Android geen screenshot van dit scherm. Deze app of een privacy-instelling kan schermopnames blokkeren."
            );
            return;
        }

        thinking = true;
        setMicEnabled(false);

        if (screenVisionQuery) {
            setStatus("LOOKING AT SCREEN");
        } else {
            setStatus(
                    InternetSettings
                            .enabled(context)
                            ? "THINKING • WEB AUTO"
                            : "THINKING"
            );
        }

        if (answerText != null) {
            answerText.setText(
                    screenVisionQuery
                            ? "Even kijken..."
                            : "Even denken..."
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

                String screenshotDataUrl =
                        screenVisionQuery
                                ? encodeScreenshot(
                                        screenshotForRequest,
                                        highVisionDetail
                                )
                                : null;

                OpenAiClient.StreamListener
                        streamListener =
                        streamedText ->
                                mainHandler.post(() -> {
                                    if (!sessionVisible
                                            || !thinking) {
                                        return;
                                    }

                                    String partial =
                                            cleanAssistantText(
                                                    streamedText
                                            );

                                    if (!partial.isEmpty()
                                            && answerText != null) {
                                        answerText.setText(
                                                partial
                                        );
                                    }

                                    setStatus(
                                            screenVisionQuery
                                                    ? "SEEING • RESPONDING"
                                                    : "RESPONDING"
                                    );
                                });

                OpenAiClient.Reply reply;

                if (screenVisionQuery) {
                    reply =
                            OpenAiClient
                                    .askWithImageStreaming(
                                            key,
                                            "gpt-5.6-luna",
                                            conversationId,
                                            query,
                                            screenshotDataUrl,
                                            highVisionDetail
                                                    ? "high"
                                                    : "low",
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
                                                    ),
                                            streamListener
                                    );
                } else {
                    reply =
                            OpenAiClient.askStreaming(
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
                                            ),
                                    streamListener
                            );
                }

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

                    String cleanedReply =
                            cleanAssistantText(
                                    reply.text
                            );

                    if (answerText != null) {
                        answerText.setText(
                                cleanedReply
                        );
                    }

                    if (footerText != null) {
                        String footer =
                                UsageTracker
                                        .compactLine(
                                                context
                                        );

                        if (screenVisionQuery) {
                            footer += " • VISION";
                        }

                        if (reply.webUsed) {
                            footer += " • WEB";
                        }

                        footerText.setText(footer);
                    }

                    speakOrFinish(
                            cleanedReply
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
            } finally {
                if (screenshotForRequest != null
                        && !screenshotForRequest.isRecycled()) {
                    screenshotForRequest.recycle();
                }
            }
        });
    }

    private boolean isScreenVisionQuery(
            String normalized
    ) {
        if (normalized == null
                || normalized.trim().isEmpty()) {
            return false;
        }

        String value = normalized.trim();

        if (!value.contains("scherm")
                && !value.contains("screen")) {
            return false;
        }

        return value.contains("wat zie")
                || value.contains("zie je")
                || value.contains("kan je zien")
                || value.contains("kun je zien")
                || value.contains("wat staat")
                || value.contains("wat heb ik")
                || value.contains("kijk naar")
                || value.contains("bekijk")
                || value.contains("lees")
                || value.contains("wat is dit");
    }

    private boolean requiresHighVisionDetail(
            String normalized
    ) {
        if (normalized == null) {
            return false;
        }

        return normalized.contains("lees")
                || normalized.contains("tekst")
                || normalized.contains("wat staat")
                || normalized.contains("nummer")
                || normalized.contains("code")
                || normalized.contains("kleine letters")
                || normalized.contains("details");
    }

    private Bitmap copyLatestScreenshot() {
        Bitmap screenshot = latestScreenshot;

        if (screenshot == null
                || screenshot.isRecycled()) {
            return null;
        }

        try {
            Bitmap.Config config =
                    screenshot.getConfig();

            if (config == null
                    || config == Bitmap.Config.HARDWARE) {
                config = Bitmap.Config.ARGB_8888;
            }

            return screenshot.copy(
                    config,
                    false
            );
        } catch (Exception ignored) {
            return null;
        }
    }

    private String encodeScreenshot(
            Bitmap screenshot,
            boolean highDetail
    ) throws Exception {
        if (screenshot == null
                || screenshot.isRecycled()) {
            throw new Exception(
                    "Schermbeeld is niet meer beschikbaar."
            );
        }

        Bitmap working = screenshot;
        int width = screenshot.getWidth();
        int height = screenshot.getHeight();
        int maxEdge = Math.max(width, height);
        int targetMaxEdge =
                highDetail ? 2400 : 1600;

        if (maxEdge > targetMaxEdge) {
            float scale =
                    targetMaxEdge / (float) maxEdge;
            working = Bitmap.createScaledBitmap(
                    screenshot,
                    Math.max(1, Math.round(width * scale)),
                    Math.max(1, Math.round(height * scale)),
                    true
            );
        }

        try (ByteArrayOutputStream output =
                     new ByteArrayOutputStream()) {
            if (!working.compress(
                    Bitmap.CompressFormat.JPEG,
                    highDetail ? 82 : 72,
                    output
            )) {
                throw new Exception(
                        "Schermbeeld kon niet worden gecomprimeerd."
                );
            }

            return "data:image/jpeg;base64,"
                    + Base64.encodeToString(
                            output.toByteArray(),
                            Base64.NO_WRAP
                    );
        } finally {
            if (working != screenshot
                    && !working.isRecycled()) {
                working.recycle();
            }
        }
    }

    private void clearLatestScreenshot() {
        Bitmap screenshot = latestScreenshot;
        latestScreenshot = null;

        if (screenshot != null
                && !screenshot.isRecycled()) {
            screenshot.recycle();
        }
    }

    private String cleanAssistantText(
            String text
    ) {
        if (text == null) {
            return "";
        }

        return text
                .replace("**", "")
                .replace("__", "")
                .replace("`", "")
                .replace("*", "")
                .replaceAll(
                        "(?m)^\\s*#{1,6}\\s*",
                        ""
                )
                .replaceAll(
                        "\\n{3,}",
                        "\\n\\n"
                )
                .trim();
    }

    private void showLocalAnswer(
            String message
    ) {
        stopRealtimeVoice();
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

        stopRealtimeVoice();
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

    private GradientDrawable edgeStroke(
            int color,
            int widthDp,
            int radiusDp
    ) {
        GradientDrawable drawable =
                new GradientDrawable();

        drawable.setColor(
                Color.TRANSPARENT
        );

        drawable.setCornerRadius(
                dp(radiusDp)
        );

        drawable.setStroke(
                dp(widthDp),
                color
        );

        return drawable;
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
