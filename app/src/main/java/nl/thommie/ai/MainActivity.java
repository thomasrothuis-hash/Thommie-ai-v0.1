package nl.thommie.ai;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.util.Base64;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.text.InputType;
import android.text.method.ScrollingMovementMethod;
import android.view.Gravity;
import android.view.TextureView;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    public static final String EXTRA_ASSISTANT_INVOCATION =
            "maatje_assistant_invocation";
    public static final String EXTRA_ASSISTANT_SOURCE =
            "maatje_assistant_source";
    public static final String EXTRA_CAMERA_INVOCATION =
            "maatje_camera_invocation";

    private static final int REQ_AUDIO = 1001;
    private static final int REQ_CAMERA = 1002;
    private static final int BG = Color.rgb(4, 8, 5);
    private static final int PANEL = Color.rgb(9, 17, 11);
    private static final int MINT = Color.rgb(54, 220, 104);
    private static final int TEXT = Color.rgb(216, 240, 222);
    private static final int MUTED = Color.rgb(105, 139, 113);
    private static final int BORDER = Color.rgb(28, 63, 38);

    private final ExecutorService chatExecutor =
            Executors.newSingleThreadExecutor();
    private final ExecutorService voiceExecutor =
            Executors.newSingleThreadExecutor();

    private TextView stateText;
    private TextView wakeDebugText;
    private TextView usageText;
    private TextView transcript;
    private EditText input;
    private Button micButton;
    private AudioWaveformView waveformView;
    private RealtimeVoiceClient realtimeVoiceClient;
    private CameraVisionController cameraVisionController;
    private LinearLayout cameraPanel;
    private TextureView cameraPreview;
    private TextView cameraStatusText;
    private Button cameraButton;
    private Button cameraSwitchButton;
    private Button stopResponseButton;
    private boolean realtimeConnected = false;
    private boolean realtimeAssistantSpeaking = false;
    private Button sendButton;
    private Button replayButton;
    private SpeechRecognizer speechRecognizer;
    private MediaPlayer mediaPlayer;
    private AudioManager audioManager;
    private OfflineWakeWord offlineWakeWord;
    private boolean communicationAudioActive = false;
    private int previousAudioMode = AudioManager.MODE_NORMAL;
    private boolean previousSpeakerphoneOn = false;
    private AudioDeviceInfo previousCommunicationDevice = null;
    private int previousVoiceCallVolume = -1;
    private boolean voiceCallVolumeBoosted = false;
    private String lastAssistantReply = "";
    private final Handler mainHandler =
            new Handler(Looper.getMainLooper());
    private final Runnable cameraFrameRunnable =
            new Runnable() {
                @Override
                public void run() {
                    if (!cameraVisionActive
                            || cameraVisionController == null) {
                        return;
                    }

                    cameraVisionController.captureFrame();
                    mainHandler.postDelayed(
                            this,
                            1000L
                    );
                }
            };

    private boolean wakeWordEnabled = true;
    private boolean wakeWordListening = false;
    private boolean commandListening = false;
    private boolean ignoreNextRecognitionError = false;
    private boolean appVisible = false;
    private boolean chatBusy = false;
    private boolean pendingManualPermission = false;
    private boolean conversationModeActive = false;
    private long conversationExpiresAt = 0L;
    private boolean assistantSpeaking = false;
    private boolean pendingAssistantInvocation = false;
    private boolean pendingCameraInvocation = false;
    private boolean pendingCameraPermission = false;
    private boolean cameraVisionActive = false;
    private boolean cameraFront = false;
    private boolean cameraQuestionPending = false;
    private String latestCameraDataUrl = "";
    private int kioskLogoTapCount = 0;
    private long kioskFirstLogoTapMs = 0L;
    private long normalListeningBlockedUntil = 0L;
    private static final long POST_TTS_COOLDOWN_MS = 850L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        );
        installDedicatedNavigationGuards();

        buildUi();
        enforceDedicatedUi();
        initSpeechRecognizer();

        audioManager =
                (AudioManager) getSystemService(
                        AUDIO_SERVICE
                );

        wakeWordEnabled = WakeWordSettings.enabled(this);

        offlineWakeWord =
                new OfflineWakeWord(
                        this,
                        new OfflineWakeWord.Callback() {
                            @Override
                            public void onReady() {
                                stateText.setText(
                                        "WAKE MODEL READY"
                                );

                                scheduleWakeListening(
                                        300
                                );
                            }

                            @Override
                            public void onDetected() {
                                wakeWordListening = false;

                                beginConversationSession();

                                stateText.setText("YES?");

                                if (wakeDebugText != null) {
                                    wakeDebugText.setText(
                                            "HEARD • WAKE WORD"
                                    );
                                }

                                mainHandler.postDelayed(
                                        MainActivity.this
                                                ::startRealtimeVoice,
                                        350
                                );
                            }

                            @Override
                            public void onStopDetected() {
                                handleSpokenStop();
                            }

                            @Override
                            public void onHeard(
                                    String text
                            ) {
                                updateWakeDebug(text);
                            }

                            @Override
                            public void onError(
                                    String message
                            ) {
                                stateText.setText(
                                        "WAKE ERROR"
                                );

                                Toast.makeText(
                                        MainActivity.this,
                                        message,
                                        Toast.LENGTH_LONG
                                ).show();
                            }
                        }
                );

        offlineWakeWord.prepare();

        consumeAssistantIntent(
                getIntent()
        );

        if (SecurePrefs.loadApiKey(this).isEmpty()) {
            showApiKeyDialog(true);
        }
    }

    private void consumeAssistantIntent(
            Intent intent
    ) {
        if (intent == null) {
            return;
        }

        if (intent.getBooleanExtra(
                EXTRA_ASSISTANT_INVOCATION,
                false
        )) {
            pendingAssistantInvocation = true;
        }

        if (intent.getBooleanExtra(
                EXTRA_CAMERA_INVOCATION,
                false
        )) {
            pendingCameraInvocation = true;
        }
    }

    @Override
    protected void onNewIntent(
            Intent intent
    ) {
        super.onNewIntent(intent);
        setIntent(intent);
        consumeAssistantIntent(intent);

        if (appVisible
                && pendingCameraInvocation) {
            pendingCameraInvocation = false;
            mainHandler.postDelayed(
                    this::startCameraVision,
                    250L
            );
        } else if (appVisible
                && pendingAssistantInvocation) {
            pendingAssistantInvocation = false;

            mainHandler.postDelayed(
                    this::startRealtimeVoice,
                    300L
            );
        }
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(22), dp(30), dp(22), dp(22));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText("MAATJE");
        title.setTextColor(MINT);
        title.setTextSize(25);
        title.setTypeface(
                Typeface.create(
                        Typeface.MONOSPACE,
                        Typeface.BOLD
                )
        );
        title.setClickable(true);
        title.setOnClickListener(
                v -> handleKioskLogoTap()
        );
        header.addView(
                title,
                new LinearLayout.LayoutParams(
                        0,
                        dp(50),
                        1f
                )
        );

        Button settings = button("⚙", PANEL, MINT);
        settings.setOnClickListener(
                v -> showSettingsMenu()
        );
        header.addView(
                settings,
                new LinearLayout.LayoutParams(
                        dp(54),
                        dp(46)
                )
        );
        root.addView(header);

        TextView version = new TextView(this);
        version.setText(
                "v1.3.3-oneplus  •  ONEPLUS EDITION • PERSONAL AI TERMINAL"
        );
        version.setTextColor(MUTED);
        version.setTextSize(11);
        version.setTypeface(Typeface.MONOSPACE);
        version.setLetterSpacing(.16f);
        root.addView(version);

        TextView orb = new TextView(this);
        orb.setText("●");
        orb.setTextColor(MINT);
        orb.setTextSize(90);
        orb.setGravity(Gravity.CENTER);

        LinearLayout.LayoutParams orbLp =
                new LinearLayout.LayoutParams(
                        -1,
                        dp(125)
                );
        orbLp.topMargin = dp(8);
        root.addView(orb, orbLp);

        stateText = new TextView(this);
        stateText.setText("READY");
        stateText.setGravity(Gravity.CENTER);
        stateText.setTextColor(MINT);
        stateText.setTextSize(13);
        stateText.setTypeface(
                Typeface.create(
                        Typeface.MONOSPACE,
                        Typeface.BOLD
                )
        );
        stateText.setLetterSpacing(.20f);
        root.addView(stateText);

        waveformView = new AudioWaveformView(this);
        waveformView.setMode(AudioWaveformView.MODE_IDLE);
        LinearLayout.LayoutParams waveformLp =
                new LinearLayout.LayoutParams(
                        -1,
                        dp(58)
                );
        waveformLp.topMargin = dp(8);
        waveformLp.bottomMargin = dp(4);
        root.addView(waveformView, waveformLp);

        cameraPanel = new LinearLayout(this);
        cameraPanel.setOrientation(LinearLayout.VERTICAL);
        cameraPanel.setVisibility(View.GONE);
        cameraPanel.setPadding(
                dp(8),
                dp(8),
                dp(8),
                dp(8)
        );
        cameraPanel.setBackground(
                roundRect(
                        PANEL,
                        18,
                        MINT
                )
        );

        FrameLayout previewFrame =
                new FrameLayout(this);

        cameraPreview =
                new TextureView(this);
        previewFrame.addView(
                cameraPreview,
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                )
        );

        cameraStatusText =
                new TextView(this);
        cameraStatusText.setText(
                "CAMERA OFF"
        );
        cameraStatusText.setTextColor(MINT);
        cameraStatusText.setTextSize(11);
        cameraStatusText.setTypeface(
                Typeface.create(
                        Typeface.MONOSPACE,
                        Typeface.BOLD
                )
        );
        cameraStatusText.setPadding(
                dp(10),
                dp(6),
                dp(10),
                dp(6)
        );

        FrameLayout.LayoutParams statusLp =
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                );
        statusLp.gravity =
                Gravity.TOP | Gravity.START;
        previewFrame.addView(
                cameraStatusText,
                statusLp
        );

        cameraPanel.addView(
                previewFrame,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(175)
                )
        );

        LinearLayout cameraControls =
                new LinearLayout(this);
        cameraControls.setOrientation(
                LinearLayout.HORIZONTAL
        );
        cameraControls.setGravity(
                Gravity.CENTER_VERTICAL
        );

        cameraSwitchButton =
                button(
                        "↺  WISSEL",
                        PANEL,
                        MINT
                );
        cameraSwitchButton.setTextSize(11);
        cameraSwitchButton.setOnClickListener(
                v -> switchCameraVision()
        );

        Button cameraStopButton =
                button(
                        "■  STOP CAMERA",
                        PANEL,
                        MINT
                );
        cameraStopButton.setTextSize(11);
        cameraStopButton.setOnClickListener(
                v -> stopCameraVision()
        );

        LinearLayout.LayoutParams cameraControlLp =
                new LinearLayout.LayoutParams(
                        0,
                        dp(42),
                        1f
                );
        cameraControlLp.topMargin = dp(6);

        cameraControls.addView(
                cameraSwitchButton,
                cameraControlLp
        );

        LinearLayout.LayoutParams stopCameraLp =
                new LinearLayout.LayoutParams(
                        0,
                        dp(42),
                        1f
                );
        stopCameraLp.topMargin = dp(6);
        stopCameraLp.leftMargin = dp(6);

        cameraControls.addView(
                cameraStopButton,
                stopCameraLp
        );

        cameraPanel.addView(
                cameraControls
        );

        LinearLayout.LayoutParams cameraPanelLp =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
        cameraPanelLp.topMargin = dp(6);
        cameraPanelLp.bottomMargin = dp(6);
        root.addView(
                cameraPanel,
                cameraPanelLp
        );

        wakeDebugText = new TextView(this);
        wakeDebugText.setGravity(Gravity.CENTER);
        wakeDebugText.setTextColor(MUTED);
        wakeDebugText.setTextSize(11);
        wakeDebugText.setTypeface(Typeface.MONOSPACE);
        wakeDebugText.setPadding(
                0,
                dp(6),
                0,
                0
        );

        wakeDebugText.setVisibility(
                WakeWordSettings.debugEnabled(this)
                        ? View.VISIBLE
                        : View.GONE
        );

        root.addView(wakeDebugText);

        usageText = new TextView(this);
        usageText.setGravity(Gravity.CENTER);
        usageText.setTextColor(MUTED);
        usageText.setTextSize(10);
        usageText.setTypeface(Typeface.MONOSPACE);
        usageText.setPadding(0, dp(5), 0, 0);
        usageText.setText(
                UsageTracker.compactLine(this)
        );
        root.addView(usageText);

        GradientDrawable panelBg =
                roundRect(
                        PANEL,
                        22,
                        BORDER
                );

        transcript = new TextView(this);
        transcript.setText(
                "Welkom.\n\n"
                        + "MAATJE v1.3.0 gebruikt OpenAI cloud voice, "
                        + "blijvend gespreksgeheugen, lokaal profielgeheugen en lokale \"Hey Maatje\" activatie."
        );
        transcript.setTextColor(TEXT);
        transcript.setTextSize(16);
        transcript.setLineSpacing(0, 1.25f);
        transcript.setPadding(
                dp(18),
                dp(18),
                dp(18),
                dp(18)
        );
        transcript.setBackground(panelBg);
        transcript.setMovementMethod(
                new ScrollingMovementMethod()
        );
        transcript.setVerticalScrollBarEnabled(true);
        transcript.setScrollBarStyle(
                View.SCROLLBARS_INSIDE_OVERLAY
        );

        LinearLayout.LayoutParams transcriptLp =
                new LinearLayout.LayoutParams(
                        -1,
                        0,
                        1f
                );
        transcriptLp.topMargin = dp(20);
        transcriptLp.bottomMargin = dp(8);
        root.addView(transcript, transcriptLp);

        replayButton = button(
                "▶  OPNIEUW",
                PANEL,
                MINT
        );
        replayButton.setTextSize(12);
        replayButton.setTypeface(Typeface.MONOSPACE);
        replayButton.setEnabled(false);
        replayButton.setAlpha(.38f);
        replayButton.setOnClickListener(
                v -> replayLastAssistantReply()
        );

        LinearLayout.LayoutParams replayLp =
                new LinearLayout.LayoutParams(
                        dp(145),
                        dp(40)
                );
        replayLp.gravity = Gravity.END;
        replayLp.bottomMargin = dp(10);
        root.addView(replayButton, replayLp);

        LinearLayout composer = new LinearLayout(this);
        composer.setOrientation(
                LinearLayout.HORIZONTAL
        );
        composer.setGravity(
                Gravity.CENTER_VERTICAL
        );

        micButton = button("🎙", PANEL, MINT);
        micButton.setOnClickListener(
                v -> toggleRealtimeVoice()
        );
        composer.addView(
                micButton,
                new LinearLayout.LayoutParams(
                        dp(58),
                        dp(58)
                )
        );

        cameraButton = button(
                "📷",
                PANEL,
                MINT
        );
        cameraButton.setOnClickListener(
                v -> {
                    if (cameraVisionActive) {
                        stopCameraVision();
                    } else {
                        startCameraVision();
                    }
                }
        );

        LinearLayout.LayoutParams cameraButtonLp =
                new LinearLayout.LayoutParams(
                        dp(58),
                        dp(58)
                );
        cameraButtonLp.leftMargin = dp(8);
        composer.addView(
                cameraButton,
                cameraButtonLp
        );

        stopResponseButton = button(
                "STOP",
                PANEL,
                MINT
        );
        stopResponseButton.setTextSize(10);
        stopResponseButton.setTypeface(
                Typeface.create(
                        Typeface.MONOSPACE,
                        Typeface.BOLD
                )
        );
        stopResponseButton.setEnabled(false);
        stopResponseButton.setAlpha(.35f);
        stopResponseButton.setOnClickListener(
                v -> interruptRealtimeAnswer()
        );

        LinearLayout.LayoutParams stopResponseLp =
                new LinearLayout.LayoutParams(
                        dp(54),
                        dp(58)
                );
        stopResponseLp.leftMargin = dp(8);
        composer.addView(
                stopResponseButton,
                stopResponseLp
        );

        input = new EditText(this);
        input.setHint("Vraag iets…");
        input.setHintTextColor(MUTED);
        input.setTextColor(TEXT);
        input.setTextSize(16);
        input.setSingleLine(false);
        input.setMaxLines(3);
        input.setPadding(
                dp(16),
                dp(8),
                dp(16),
                dp(8)
        );
        input.setBackground(
                roundRect(
                        PANEL,
                        18,
                        BORDER
                )
        );

        LinearLayout.LayoutParams inputLp =
                new LinearLayout.LayoutParams(
                        0,
                        dp(58),
                        1f
                );
        inputLp.leftMargin = dp(10);
        inputLp.rightMargin = dp(10);
        composer.addView(input, inputLp);

        sendButton = button("➜", MINT, BG);
        sendButton.setTextSize(21);
        sendButton.setOnClickListener(
                v -> sendCurrentInput()
        );
        composer.addView(
                sendButton,
                new LinearLayout.LayoutParams(
                        dp(58),
                        dp(58)
                )
        );

        root.addView(composer);
        setContentView(root);
    }

    private void setLastAssistantReply(
            String text
    ) {
        lastAssistantReply =
                text == null
                        ? ""
                        : text.trim();

        if (replayButton != null) {
            boolean available =
                    !lastAssistantReply.isEmpty();

            replayButton.setEnabled(available);
            replayButton.setAlpha(
                    available ? 1f : .38f
            );
        }
    }

    private void replayLastAssistantReply() {
        if (lastAssistantReply.isEmpty()) {
            Toast.makeText(
                    this,
                    "Nog geen MAATJE-antwoord om af te spelen.",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        if (chatBusy) {
            Toast.makeText(
                    this,
                    "Wacht even tot het huidige antwoord klaar is.",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        pauseConversationTimer();
        stopRecognitionSession();
        stopPlayer();
        stateText.setText("REPLAY");
        speak(lastAssistantReply, true);
    }

    private void sendCurrentInput() {
        String q =
                input.getText()
                        .toString()
                        .trim();

        if (q.isEmpty()) return;

        input.setText("");
        ask(q);
    }

    private void ask(String q) {
        if (isConversationEndCommand(q)) {
            endConversationSession();

            stateText.setText(
                    "STANDBY • HEY MAATJE"
            );

            normalListeningBlockedUntil =
                    System.currentTimeMillis()
                            + 500L;

            scheduleWakeListening(550);
            return;
        }

        DeviceControl.CommandResult deviceCommand =
                DeviceControl.handleCommand(
                        this,
                        q
                );

        if (deviceCommand.handled) {
            append("\n\nJIJ\n" + q);
            append(
                    "\n\nMAATJE\n"
                            + deviceCommand.message
            );

            if (stateText != null
                    && deviceCommand.state != null
                    && !deviceCommand.state.isEmpty()) {
                stateText.setText(
                        deviceCommand.state
                );
            }

            setLastAssistantReply(
                    deviceCommand.message
            );
            speak(deviceCommand.message);
            return;
        }

        InternetSettings.CommandResult internetCommand =
                InternetSettings.handleCommand(
                        this,
                        q
                );

        if (internetCommand.handled) {
            append("\n\nJIJ\n" + q);
            append(
                    "\n\nMAATJE\n"
                            + internetCommand.message
            );
            setLastAssistantReply(
                    internetCommand.message
            );
            speak(internetCommand.message);
            return;
        }

        PersonalitySettings.CommandResult personalityCommand =
                PersonalitySettings.handleCommand(this, q);

        if (personalityCommand.handled) {
            append("\n\nJIJ\n" + q);
            append("\n\nMAATJE\n" + personalityCommand.message);
            setLastAssistantReply(personalityCommand.message);
            speak(personalityCommand.message);
            return;
        }

        if (PersonalitySettings.captureHumorFeedback(
                this,
                q,
                lastAssistantReply
        )) {
            Toast.makeText(
                    this,
                    "Humorprofiel bijgewerkt.",
                    Toast.LENGTH_SHORT
            ).show();
        }

        String key = SecurePrefs.loadApiKey(this);

        if (key.isEmpty()) {
            showApiKeyDialog(true);
            return;
        }

        boolean remembered =
                MemoryStore.captureExplicitMemory(
                        this,
                        q
                );

        append("\n\nJIJ\n" + q);

        if (remembered) {
            append(
                    "\n\nGEHEUGEN\n"
                            + "Opgeslagen in lokaal profielgeheugen."
            );
        }

        boolean webEnabled =
                InternetSettings.enabled(this);

        setBusy(
                true,
                webEnabled
                        ? "THINKING • WEB AUTO"
                        : "THINKING"
        );

        chatExecutor.submit(() -> {
            try {
                String conversationId =
                        MemoryStore.getConversationId(
                                MainActivity.this
                        );

                if (conversationId.isEmpty()) {
                    conversationId =
                            OpenAiClient.createConversation(
                                    key
                            );

                    MemoryStore.setConversationId(
                            MainActivity.this,
                            conversationId
                    );
                }

                String profileMemory =
                        MemoryStore.getProfile(
                                MainActivity.this
                        );

                OpenAiClient.Reply reply =
                        OpenAiClient.ask(
                                key,
                                "gpt-5.6-luna",
                                conversationId,
                                q,
                                profileMemory,
                                PersonalitySettings.prompt(MainActivity.this),
                                webEnabled
                        );

                runOnUiThread(() -> {
                    append(
                            "\n\nMAATJE\n"
                                    + reply.text
                    );
                    if (reply.webUsed) {
                        append(
                                formatWebSources(
                                        reply.sources
                                )
                        );
                    }

                    UsageTracker.record(
                            MainActivity.this,
                            reply.inputTokens,
                            reply.outputTokens,
                            reply.totalTokens
                    );

                    if (usageText != null) {
                        usageText.setText(
                                UsageTracker.compactLine(
                                        MainActivity.this
                                )
                        );
                    }

                    setLastAssistantReply(reply.text);
                    setBusy(
                            false,
                            reply.webUsed
                                    ? "READY • WEB"
                                    : "READY"
                    );
                    speak(reply.text);
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    append(
                            "\n\nFOUT\n"
                                    + e.getMessage()
                    );
                    setBusy(false, "ERROR");
                    recoverAfterAssistantFailure(700);
                });
            }
        });
    }

    private String formatWebSources(
            java.util.List<OpenAiClient.Source> sources
    ) {
        if (sources == null
                || sources.isEmpty()) {
            return "\n\nWEB\nLive web search gebruikt.";
        }

        StringBuilder sb =
                new StringBuilder(
                        "\n\nBRONNEN"
                );

        int count =
                Math.min(
                        sources.size(),
                        5
                );

        for (int i = 0; i < count; i++) {
            OpenAiClient.Source source =
                    sources.get(i);

            sb.append("\n")
                    .append(i + 1)
                    .append(". ");

            if (source.title != null
                    && !source.title.isEmpty()) {
                sb.append(source.title)
                        .append("\n   ");
            }

            sb.append(source.url);
        }

        return sb.toString();
    }

    private void initSpeechRecognizer() {
        if (!SpeechRecognizer
                .isRecognitionAvailable(this)) {
            return;
        }

        speechRecognizer =
                SpeechRecognizer
                        .createSpeechRecognizer(this);

        speechRecognizer.setRecognitionListener(
                new RecognitionListener() {
                    public void onReadyForSpeech(
                            Bundle params
                    ) {
                        if (wakeWordListening) {
                            stateText.setText(
                                    "STANDBY • HEY MAATJE"
                            );
                        } else {
                            stateText.setText(
                                    "LISTENING"
                            );
                        }
                    }

                    public void onBeginningOfSpeech() {
                        if (!wakeWordListening) {
                            stateText.setText(
                                    "LISTENING"
                            );
                        }
                    }

                    public void onRmsChanged(
                            float rmsdB
                    ) {}

                    public void onBufferReceived(
                            byte[] buffer
                    ) {}

                    public void onEndOfSpeech() {
                        if (wakeWordListening) {
                            stateText.setText(
                                    "CHECKING"
                            );
                        } else if (commandListening) {
                            stateText.setText(
                                    "PROCESSING SPEECH"
                            );
                        }
                    }

                    public void onError(int error) {
                        if (ignoreNextRecognitionError) {
                            ignoreNextRecognitionError = false;
                            return;
                        }

                        boolean wasWake =
                                wakeWordListening;

                        boolean wasCommand =
                                commandListening;

                        wakeWordListening = false;
                        commandListening = false;

                        if (wasWake) {
                            stateText.setText(
                                    "STANDBY • HEY MAATJE"
                            );

                            scheduleWakeListening(
                                    error
                                            == SpeechRecognizer.ERROR_NO_MATCH
                                            ? 200
                                            : 700
                            );
                            return;
                        }

                        if (wasCommand) {
                            setBusy(false, "READY");

                            if (conversationModeActive
                                    && isConversationWindowOpen()
                                    && (error
                                    == SpeechRecognizer.ERROR_NO_MATCH
                                    || error
                                    == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)) {

                                scheduleFollowUpListening(
                                        350,
                                        false
                                );
                                return;
                            }

                            if (conversationModeActive) {
                                endConversationSession();
                            }

                            Toast.makeText(
                                    MainActivity.this,
                                    "Spraakherkenning fout: "
                                            + error,
                                    Toast.LENGTH_SHORT
                            ).show();

                            scheduleWakeListening(700);
                        }
                    }

                    public void onResults(
                            Bundle results
                    ) {
                        boolean wasWake =
                                wakeWordListening;

                        boolean wasCommand =
                                commandListening;

                        wakeWordListening = false;
                        commandListening = false;

                        ArrayList<String> list =
                                results.getStringArrayList(
                                        SpeechRecognizer
                                                .RESULTS_RECOGNITION
                                );

                        if (wasWake) {
                            handleWakeResults(list);
                            return;
                        }

                        if (wasCommand) {
                            setBusy(false, "READY");

                            if (list != null
                                    && !list.isEmpty()) {
                                String rawValue =
                                        list.get(0);

                                if (isConversationEndCommand(rawValue)) {
                                    endConversationSession();
                                    stateText.setText(
                                            "STANDBY • HEY MAATJE"
                                    );
                                    scheduleWakeListening(300);
                                    return;
                                }

                                pauseConversationTimer();

                                String value =
                                        formatRecognizedSpeech(
                                                rawValue
                                        );

                                input.setText(value);
                                input.setSelection(
                                        input.length()
                                );

                                ask(value);
                                input.setText("");
                            } else if (conversationModeActive
                                    && isConversationWindowOpen()) {
                                scheduleFollowUpListening(
                                        350,
                                        false
                                );
                            } else {
                                endConversationSession();
                                scheduleWakeListening(
                                        400
                                );
                            }
                        }
                    }

                    public void onPartialResults(
                            Bundle partialResults
                    ) {
                        if (!wakeWordListening) {
                            return;
                        }

                        ArrayList<String> list =
                                partialResults.getStringArrayList(
                                        SpeechRecognizer
                                                .RESULTS_RECOGNITION
                                );

                        String command =
                                extractWakeCommand(list);

                        if (command == null) {
                            return;
                        }

                        wakeWordListening = false;
                        commandListening = false;
                        ignoreNextRecognitionError = true;

                        stateText.setText("YES?");

                        try {
                            speechRecognizer.cancel();
                        } catch (Exception ignored) {}

                        final String wakeCommand =
                                command;

                        mainHandler.postDelayed(
                                () -> {
                                    ignoreNextRecognitionError = false;

                                    if (!wakeCommand.isEmpty()) {
                                        ask(
                                                formatRecognizedSpeech(
                                                        wakeCommand
                                                )
                                        );
                                    } else {
                                        startCommandListeningInternal();
                                    }
                                },
                                650
                        );
                    }

                    public void onEvent(
                            int eventType,
                            Bundle params
                    ) {}
                }
        );
    }

    private void startCameraVision() {
        if (!appVisible) {
            return;
        }

        if (checkSelfPermission(
                Manifest.permission.CAMERA
        ) != PackageManager.PERMISSION_GRANTED) {
            pendingCameraPermission = true;

            requestPermissions(
                    new String[]{
                            Manifest.permission.CAMERA
                    },
                    REQ_CAMERA
            );
            return;
        }

        pendingCameraPermission = false;

        if (cameraVisionActive) {
            return;
        }

        cameraVisionActive = true;
        cameraQuestionPending = false;
        latestCameraDataUrl = "";

        if (cameraPanel != null) {
            cameraPanel.setVisibility(
                    View.VISIBLE
            );
        }

        if (cameraButton != null) {
            cameraButton.setText("📷✓");
        }

        if (cameraStatusText != null) {
            cameraStatusText.setText(
                    "CAMERA STARTING..."
            );
        }

        if (offlineWakeWord != null) {
            offlineWakeWord.stop();
        }

        if (realtimeVoiceClient == null
                || !realtimeVoiceClient.isRunning()) {
            startRealtimeVoice();
        }

        CameraVisionController old =
                cameraVisionController;

        if (old != null) {
            old.stop();
        }

        cameraVisionController =
                new CameraVisionController(
                        this,
                        cameraPreview,
                        new CameraVisionController.Callback() {
                            @Override
                            public void onCameraReady(
                                    boolean front
                            ) {
                                cameraFront = front;

                                if (cameraStatusText != null) {
                                    cameraStatusText.setText(
                                            front
                                                    ? "CAMERA LIVE • FRONT"
                                                    : "CAMERA LIVE • REAR"
                                    );
                                }

                                RealtimeVoiceClient client =
                                        realtimeVoiceClient;

                                if (client != null
                                        && client.isRunning()) {
                                    client.setCameraMode(true);
                                }

                                mainHandler.removeCallbacks(
                                        cameraFrameRunnable
                                );
                                mainHandler.post(
                                        cameraFrameRunnable
                                );
                            }

                            @Override
                            public void onFrame(
                                    byte[] jpeg
                            ) {
                                if (!cameraVisionActive
                                        || jpeg == null
                                        || jpeg.length == 0) {
                                    return;
                                }

                                String dataUrl =
                                        "data:image/jpeg;base64,"
                                                + Base64.encodeToString(
                                                        jpeg,
                                                        Base64.NO_WRAP
                                                );

                                latestCameraDataUrl =
                                        dataUrl;

                                RealtimeVoiceClient client =
                                        realtimeVoiceClient;

                                if (client == null
                                        || !client.isRunning()) {
                                    return;
                                }

                                boolean answerNow =
                                        cameraQuestionPending;

                                if (answerNow) {
                                    cameraQuestionPending =
                                            false;
                                }

                                if (realtimeAssistantSpeaking
                                        && !answerNow) {
                                    return;
                                }

                                client.sendCameraFrame(
                                        dataUrl,
                                        answerNow
                                );

                                if (cameraStatusText != null) {
                                    cameraStatusText.setText(
                                            answerNow
                                                    ? "CAMERA LIVE • ANALYZING"
                                                    : (
                                                            cameraFront
                                                                    ? "CAMERA LIVE • FRONT"
                                                                    : "CAMERA LIVE • REAR"
                                                    )
                                    );
                                }
                            }

                            @Override
                            public void onError(
                                    String message
                            ) {
                                if (cameraStatusText != null) {
                                    cameraStatusText.setText(
                                            "CAMERA ERROR"
                                    );
                                }

                                Toast.makeText(
                                        MainActivity.this,
                                        message,
                                        Toast.LENGTH_LONG
                                ).show();
                            }
                        }
                );

        cameraVisionController.start(
                cameraFront
        );
    }

    private void stopCameraVision() {
        cameraVisionActive = false;
        cameraQuestionPending = false;
        latestCameraDataUrl = "";

        mainHandler.removeCallbacks(
                cameraFrameRunnable
        );

        CameraVisionController controller =
                cameraVisionController;
        cameraVisionController = null;

        if (controller != null) {
            controller.stop();
        }

        RealtimeVoiceClient client =
                realtimeVoiceClient;

        if (client != null
                && client.isRunning()) {
            client.setCameraMode(false);
        }

        if (cameraPanel != null) {
            cameraPanel.setVisibility(
                    View.GONE
            );
        }

        if (cameraButton != null) {
            cameraButton.setText("📷");
        }

        if (cameraStatusText != null) {
            cameraStatusText.setText(
                    "CAMERA OFF"
            );
        }
    }

    private void switchCameraVision() {
        if (!cameraVisionActive
                || cameraVisionController == null) {
            return;
        }

        cameraFront = !cameraFront;

        if (cameraStatusText != null) {
            cameraStatusText.setText(
                    "SWITCHING CAMERA..."
            );
        }

        cameraVisionController.start(
                cameraFront
        );
    }

    private boolean handleRealtimeCameraCommand(
            String raw
    ) {
        if (raw == null) {
            return false;
        }

        String q =
                raw.toLowerCase(
                        java.util.Locale.ROOT
                );

        boolean cameraMention =
                q.contains("camera")
                        || q.contains("meekijken")
                        || q.contains("mee kijken")
                        || q.contains("lens");

        if (!cameraMention) {
            return false;
        }

        if (q.contains("stop")
                || q.contains("uit")
                || q.contains("sluit")
                || q.contains("klaar met")) {
            RealtimeVoiceClient client =
                    realtimeVoiceClient;

            if (client != null) {
                client.cancelResponse();
            }

            stopCameraVision();

            append(
                    "\n\nMAATJE\nCamera meekijken gestopt."
            );
            return true;
        }

        if (q.contains("wissel")
                || q.contains("switch")
                || q.contains("andere camera")
                || q.contains("frontcamera")
                || q.contains("achtercamera")) {
            RealtimeVoiceClient client =
                    realtimeVoiceClient;

            if (client != null) {
                client.cancelResponse();
            }

            switchCameraVision();
            return true;
        }

        if (q.contains("kijk")
                || q.contains("open")
                || q.contains("aan")
                || q.contains("start")
                || q.contains("meekijken")
                || q.contains("mee kijken")) {
            RealtimeVoiceClient client =
                    realtimeVoiceClient;

            if (client != null) {
                client.cancelResponse();
            }

            startCameraVision();
            return true;
        }

        return false;
    }

    private void setStopResponseEnabled(
            boolean enabled
    ) {
        if (stopResponseButton == null) {
            return;
        }

        stopResponseButton.setEnabled(enabled);
        stopResponseButton.setAlpha(
                enabled ? 1f : .35f
        );
    }

    private void interruptRealtimeAnswer() {
        RealtimeVoiceClient client =
                realtimeVoiceClient;

        if (client == null
                || !client.isRunning()) {
            setStopResponseEnabled(false);
            return;
        }

        cameraQuestionPending = false;
        client.cancelResponse();

        realtimeAssistantSpeaking = false;
        setStopResponseEnabled(false);

        if (stateText != null) {
            stateText.setText(
                    cameraVisionActive
                            ? "CAMERA LIVE • LISTENING"
                            : "LISTENING • REALTIME"
            );
        }

        if (waveformView != null) {
            waveformView.setMode(
                    AudioWaveformView.MODE_USER
            );
        }
    }

    private void toggleRealtimeVoice() {
        if (realtimeVoiceClient != null
                && realtimeVoiceClient.isRunning()) {
            stopRealtimeVoice();
            stateText.setText("READY");
            scheduleWakeListening(400L);
        } else {
            startRealtimeVoice();
        }
    }

    private void startRealtimeVoice() {
        if (!appVisible) {
            return;
        }

        if (checkSelfPermission(
                Manifest.permission.RECORD_AUDIO
        ) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{
                            Manifest.permission.RECORD_AUDIO
                    },
                    REQ_AUDIO
            );
            Toast.makeText(
                    this,
                    "Geef MAATJE microfoontoegang en tik daarna opnieuw op de microfoon.",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        String apiKey =
                SecurePrefs.loadApiKey(this);

        if (apiKey.isEmpty()) {
            showApiKeyDialog(true);
            return;
        }

        stopRecognitionSession();

        if (offlineWakeWord != null) {
            offlineWakeWord.stop();
        }

        stopRealtimeVoice();

        realtimeConnected = false;
        realtimeAssistantSpeaking = false;
        stateText.setText("CONNECTING • REALTIME");
        micButton.setText("■");
        waveformView.setMode(
                AudioWaveformView.MODE_IDLE
        );

        realtimeVoiceClient =
                new RealtimeVoiceClient(
                        this,
                        new RealtimeVoiceClient.Listener() {
                            @Override
                            public void onConnected() {
                                runOnUiThread(() -> {
                                    realtimeConnected = true;
                                    setStopResponseEnabled(false);
                                    stateText.setText(
                                            "LISTENING • REALTIME"
                                    );
                                    waveformView.setMode(
                                            AudioWaveformView.MODE_USER
                                    );

                                    if (cameraVisionActive
                                            && realtimeVoiceClient != null) {
                                        realtimeVoiceClient
                                                .setCameraMode(true);
                                    }
                                });
                            }

                            @Override
                            public void onDisconnected() {
                                runOnUiThread(() -> {
                                    realtimeConnected = false;
                                    realtimeAssistantSpeaking = false;
                                    setStopResponseEnabled(false);
                                    stateText.setText(
                                            "REALTIME • DISCONNECTED"
                                    );
                                    micButton.setText("🎙");
                                    waveformView.setMode(
                                            AudioWaveformView.MODE_IDLE
                                    );
                                });
                            }

                            @Override
                            public void onError(
                                    String message
                            ) {
                                runOnUiThread(() -> {
                                    stateText.setText(
                                            "REALTIME • ERROR"
                                    );
                                    Toast.makeText(
                                            MainActivity.this,
                                            message == null
                                                    ? "Onbekende realtime-fout."
                                                    : message,
                                            Toast.LENGTH_LONG
                                    ).show();
                                });
                            }

                            @Override
                            public void onUserSpeechStarted() {
                                runOnUiThread(() -> {
                                    stateText.setText(
                                            "LISTENING • LIVE"
                                    );
                                    waveformView.setMode(
                                            AudioWaveformView.MODE_USER
                                    );
                                });
                            }

                            @Override
                            public void onUserSpeechStopped() {
                                runOnUiThread(() -> {
                                    stateText.setText(
                                            cameraVisionActive
                                                    ? "CAMERA • CAPTURING"
                                                    : "RESPONDING • LIVE"
                                    );
                                    waveformView.setMode(
                                            AudioWaveformView.MODE_IDLE
                                    );
                                });
                            }

                            @Override
                            public void onUserTranscript(
                                    String text,
                                    boolean complete
                            ) {
                                if (complete
                                        && text != null
                                        && !text.trim().isEmpty()) {
                                    final String clean =
                                            text.trim();

                                    runOnUiThread(() -> {
                                        append(
                                                "\n\nJIJ\n"
                                                        + clean
                                        );

                                        if (handleRealtimeCameraCommand(
                                                clean
                                        )) {
                                            return;
                                        }

                                        if (cameraVisionActive
                                                && cameraVisionController
                                                != null) {
                                            cameraQuestionPending = true;
                                            stateText.setText(
                                                    "CAMERA • CAPTURING"
                                            );
                                            cameraVisionController
                                                    .captureFrame();
                                        }
                                    });
                                }
                            }

                            @Override
                            public void onAssistantTranscript(
                                    String text,
                                    boolean complete
                            ) {
                                if (complete
                                        && text != null
                                        && !text.trim().isEmpty()) {
                                    runOnUiThread(() -> {
                                        String clean =
                                                text.trim();
                                        setLastAssistantReply(
                                                clean
                                        );
                                        append(
                                                "\n\nMAATJE\n"
                                                        + clean
                                        );
                                    });
                                }
                            }

                            @Override
                            public void onAssistantSpeaking(
                                    boolean speaking
                            ) {
                                runOnUiThread(() -> {
                                    realtimeAssistantSpeaking =
                                            speaking;
                                    setStopResponseEnabled(
                                            speaking
                                    );

                                    if (speaking) {
                                        stateText.setText(
                                                "SPEAKING • REALTIME"
                                        );
                                        waveformView.setMode(
                                                AudioWaveformView
                                                        .MODE_ASSISTANT
                                        );
                                    } else if (realtimeConnected) {
                                        stateText.setText(
                                                cameraVisionActive
                                                        ? "CAMERA LIVE • LISTENING"
                                                        : "LISTENING • REALTIME"
                                        );
                                        waveformView.setMode(
                                                AudioWaveformView
                                                        .MODE_USER
                                        );
                                    }
                                });
                            }

                            @Override
                            public void onMicPcm(
                                    short[] samples,
                                    int length
                            ) {
                                if (!realtimeAssistantSpeaking
                                        && waveformView != null) {
                                    waveformView.pushPcm16(
                                            samples,
                                            length
                                    );
                                }
                            }

                            @Override
                            public void onAssistantPcm(
                                    byte[] pcm
                            ) {
                                if (waveformView != null) {
                                    waveformView.pushPcm16(pcm);
                                }
                            }
                        }
                );

        realtimeVoiceClient.start(
                apiKey,
                buildRealtimeInstructions(),
                VoiceSettings.voice(this)
        );
    }

    private void stopRealtimeVoice() {
        RealtimeVoiceClient client =
                realtimeVoiceClient;

        realtimeVoiceClient = null;
        realtimeConnected = false;
        realtimeAssistantSpeaking = false;
        setStopResponseEnabled(false);

        if (client != null) {
            client.shutdown();
        }

        if (waveformView != null) {
            waveformView.setMode(
                    AudioWaveformView.MODE_IDLE
            );
        }

        if (micButton != null) {
            micButton.setText("🎙");
        }
    }

    private String buildRealtimeInstructions() {
        StringBuilder prompt =
                new StringBuilder(
                        "Je bent MAATJE, de persoonlijke realtime "
                                + "spraakassistent van Thommie. Praat "
                                + "standaard Nederlands. Reageer snel, "
                                + "natuurlijk, direct en menselijk. "
                                + "Gewone antwoorden zijn meestal 1 tot "
                                + "3 korte zinnen. Gebruik een kalme, "
                                + "zelfverzekerde vrouwelijke "
                                + "assistentstijl. Wacht tot je hele "
                                + "antwoord is uitgesproken voordat je "
                                + "weer luistert. "
                                + "Wanneer cameramodus actief is, krijg je steeds het nieuwste live camerabeeld als input_image. "
                                + "Gebruik dat beeld daadwerkelijk bij vragen over wat zichtbaar is en doe niet alsof je iets ziet dat niet in het beeld staat."
                );

        String voiceStyle =
                VoiceSettings.style(this);

        if (voiceStyle != null
                && !voiceStyle.trim().isEmpty()) {
            prompt.append("\n\nStemstijl:\n")
                    .append(voiceStyle.trim());
        }

        String personality =
                PersonalitySettings.prompt(this);

        if (personality != null
                && !personality.trim().isEmpty()) {
            prompt.append("\n\nPersoonlijkheid:\n")
                    .append(personality.trim());
        }

        String profile =
                MemoryStore.getProfile(this);

        if (profile != null
                && !profile.trim().isEmpty()) {
            prompt.append("\n\nGebruikersprofiel:\n")
                    .append(profile.trim());
        }

        return prompt.toString();
    }

    private void startListening() {
        if (checkSelfPermission(
                Manifest.permission.RECORD_AUDIO
        ) != PackageManager.PERMISSION_GRANTED) {
            pendingManualPermission = true;

            requestPermissions(
                    new String[]{
                            Manifest.permission.RECORD_AUDIO
                    },
                    REQ_AUDIO
            );
            return;
        }

        pendingManualPermission = false;

        if (speechRecognizer == null) {
            Toast.makeText(
                    this,
                    "Geen Android spraakherkenner beschikbaar.",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        stopPlayer();
        stopRecognitionSession();

        beginConversationSession();

        mainHandler.postDelayed(
                this::startCommandListeningInternal,
                250
        );
    }

    private void startCommandListeningInternal() {
        if (offlineWakeWord != null) {
            offlineWakeWord.stop();
        }

        wakeWordListening = false;

        if (!appVisible
                || speechRecognizer == null
                || chatBusy
                || assistantSpeaking) {
            return;
        }

        long blockedFor =
                normalListeningBlockedUntil
                        - System.currentTimeMillis();

        if (blockedFor > 0L) {
            mainHandler.postDelayed(
                    this::startCommandListeningInternal,
                    blockedFor + 25L
            );
            return;
        }

        wakeWordListening = false;
        commandListening = true;

        stateText.setText(
                conversationModeActive
                        ? "CONVERSATION • LISTENING"
                        : "LISTENING"
        );

        try {
            speechRecognizer.startListening(
                    createRecognizerIntent(false)
            );
        } catch (Exception e) {
            commandListening = false;

            Toast.makeText(
                    this,
                    "Luisteren starten mislukt: "
                            + e.getMessage(),
                    Toast.LENGTH_SHORT
            ).show();

            scheduleWakeListening(700);
        }
    }

    private void startWakeListening() {
        wakeWordEnabled =
                WakeWordSettings.enabled(this);

        if (!wakeWordEnabled
                || !appVisible
                || chatBusy
                || assistantSpeaking
                || mediaPlayer != null
                || offlineWakeWord == null
                || wakeWordListening
                || commandListening) {
            return;
        }

        long blockedFor =
                normalListeningBlockedUntil
                        - System.currentTimeMillis();

        if (blockedFor > 0L) {
            scheduleWakeListening(
                    blockedFor + 25L
            );
            return;
        }

        if (checkSelfPermission(
                Manifest.permission.RECORD_AUDIO
        ) != PackageManager.PERMISSION_GRANTED) {
            stateText.setText("MIC PERMISSION");
            return;
        }

        if (!offlineWakeWord.isReady()) {
            stateText.setText(
                    "PREPARING WAKE MODEL"
            );

            offlineWakeWord.prepare();
            return;
        }

        wakeWordListening = true;
        commandListening = false;

        stateText.setText(
                "STANDBY • HEY MAATJE"
        );

        offlineWakeWord.start();
    }

    private Intent createRecognizerIntent(
            boolean partialResults
    ) {
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
                RecognizerIntent.EXTRA_LANGUAGE,
                "nl-NL"
        );

        intent.putExtra(
                RecognizerIntent
                        .EXTRA_PARTIAL_RESULTS,
                partialResults
        );

        intent.putExtra(
                RecognizerIntent.EXTRA_MAX_RESULTS,
                5
        );

        if (Build.VERSION.SDK_INT
                >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+: ask the active speech provider for its
            // highest-quality punctuation/capitalization pass.
            intent.putExtra(
                    RecognizerIntent.EXTRA_ENABLE_FORMATTING,
                    RecognizerIntent.FORMATTING_OPTIMIZE_QUALITY
            );
        }

        return intent;
    }

    private void handleWakeResults(
            ArrayList<String> results
    ) {
        String command =
                extractWakeCommand(results);

        if (command == null) {
            stateText.setText(
                    "STANDBY • HEY MAATJE"
            );

            scheduleWakeListening(250);
            return;
        }

        stateText.setText("YES?");

        if (!command.isEmpty()) {
            ask(
                    formatRecognizedSpeech(
                            command
                    )
            );
            return;
        }

        mainHandler.postDelayed(
                this::startCommandListeningInternal,
                300
        );
    }

    private String extractWakeCommand(
            ArrayList<String> results
    ) {
        if (results == null) return null;

        String[] triggers = {
                "hey maatje",
                "hee maatje",
                "hé maatje",
                "hey maartje",
                "hee maartje",
                "hé maartje",
                "hey maatie",
                "hee maatie"
        };

        for (String result : results) {
            if (result == null) continue;

            String normalized =
                    result.toLowerCase(java.util.Locale.ROOT)
                            .replaceAll(
                                    "[^\\\\p{L}\\\\p{N}\\\\s]",
                                    " "
                            )
                            .replaceAll(
                                    "\\\\s+",
                                    " "
                            )
                            .trim();

            for (String trigger : triggers) {
                int index =
                        normalized.indexOf(trigger);

                if (index >= 0) {
                    return normalized
                            .substring(
                                    index
                                            + trigger.length()
                            )
                            .trim();
                }
            }
        }

        return null;
    }

    private void scheduleWakeListening(
            long delayMs
    ) {
        mainHandler.removeCallbacks(
                wakeRestartRunnable
        );

        if (!wakeWordEnabled
                || !appVisible
                || chatBusy
                || conversationModeActive) {
            return;
        }

        mainHandler.postDelayed(
                wakeRestartRunnable,
                delayMs
        );
    }

    private final Runnable wakeRestartRunnable =
            new Runnable() {
                @Override
                public void run() {
                    startWakeListening();
                }
            };

    private void stopRecognitionSession() {
        mainHandler.removeCallbacks(
                wakeRestartRunnable
        );

        if (offlineWakeWord != null) {
            offlineWakeWord.stop();
        }

        wakeWordListening = false;

        if (speechRecognizer == null) {
            commandListening = false;
            return;
        }

        if (commandListening) {
            ignoreNextRecognitionError = true;
        }

        commandListening = false;

        try {
            speechRecognizer.cancel();
        } catch (Exception ignored) {}
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults
    ) {
        super.onRequestPermissionsResult(
                requestCode,
                permissions,
                grantResults
        );

        if (requestCode == REQ_AUDIO
                && grantResults.length > 0
                && grantResults[0]
                == PackageManager
                .PERMISSION_GRANTED) {

            if (pendingManualPermission) {
                pendingManualPermission = false;
                startListening();
            } else {
                scheduleWakeListening(300);
            }

            MaatjeVoiceInteractionService
                    .refreshFromActivity();
        }

        if (requestCode == REQ_CAMERA) {
            boolean granted =
                    grantResults.length > 0
                            && grantResults[0]
                            == PackageManager.PERMISSION_GRANTED;

            if (granted
                    && pendingCameraPermission) {
                pendingCameraPermission = false;
                startCameraVision();
            } else if (!granted) {
                pendingCameraPermission = false;
                Toast.makeText(
                        this,
                        "Cameratoegang is nodig om live mee te kijken.",
                        Toast.LENGTH_LONG
                ).show();
            }
        }
    }

    private void handleKioskLogoTap() {
        long now =
                System.currentTimeMillis();

        if (kioskFirstLogoTapMs == 0L
                || now - kioskFirstLogoTapMs > 2200L) {
            kioskFirstLogoTapMs = now;
            kioskLogoTapCount = 1;
            return;
        }

        kioskLogoTapCount++;

        if (kioskLogoTapCount >= 5) {
            kioskLogoTapCount = 0;
            kioskFirstLogoTapMs = 0L;

            if (!KioskBridge.openAdminUnlock(
                    this
            )) {
                Toast.makeText(
                        this,
                        "MAATJE Kiosk is niet geïnstalleerd.",
                        Toast.LENGTH_SHORT
                ).show();
            }
        }
    }

    private void showSettingsMenu() {
        String[] options = {
                "API-key",
                "Internet",
                "Standaard assistent",
                "Gebruik & tokens",
                "Toestel & Kiosk",
                "Stem & audio",
                "Geheugen",
                "Persoonlijkheid",
                "Gespreksmodus",
                "Wake word"
        };

        new AlertDialog.Builder(this)
                .setTitle(
                        "MAATJE v1.3.3 ONEPLUS – Instellingen"
                )
                .setItems(
                        options,
                        (dialog, which) -> {
                            if (which == 0) {
                                showApiKeyDialog(false);
                            } else if (which == 1) {
                                InternetSettings.show(this);
                            } else if (which == 2) {
                                AssistantSettings.show(this);
                            } else if (which == 3) {
                                UsageTracker.show(this);
                            } else if (which == 4) {
                                KioskBridge.showDevicePanel(this);
                            } else if (which == 5) {
                                VoiceSettings.show(
                                        this,
                                        this::testCloudVoice
                                );
                            } else if (which == 6) {
                                showMemoryDialog();
                            } else if (which == 7) {
                                PersonalitySettings.show(this);
                            } else if (which == 8) {
                                ConversationSettings.show(
                                        this,
                                        enabled -> {
                                            if (!enabled) {
                                                endConversationSession();
                                                scheduleWakeListening(300);
                                            }
                                        }
                                );
                            } else {
                                WakeWordSettings.show(
                                        this,
                                        enabled -> {
                                            wakeWordEnabled = enabled;

                                            MaatjeVoiceInteractionService
                                                    .refreshFromActivity();

                                            updateWakeDebugVisibility();
                                            stopRecognitionSession();

                                            if (enabled) {
                                                if (checkSelfPermission(
                                                        Manifest.permission.RECORD_AUDIO
                                                ) != PackageManager.PERMISSION_GRANTED) {
                                                    pendingManualPermission = false;

                                                    requestPermissions(
                                                            new String[]{
                                                                    Manifest.permission.RECORD_AUDIO
                                                            },
                                                            REQ_AUDIO
                                                    );
                                                } else {
                                                    scheduleWakeListening(350);
                                                }
                                            } else {
                                                stateText.setText("READY");
                                            }
                                        }
                                );
                            }
                        }
                )
                .setNegativeButton(
                        "Sluiten",
                        null
                )
                .show();
    }

    private void showMemoryDialog() {
        LinearLayout box =
                new LinearLayout(this);
        box.setOrientation(
                LinearLayout.VERTICAL
        );
        box.setPadding(
                dp(20),
                dp(4),
                dp(20),
                0
        );

        String conversationId =
                MemoryStore.getConversationId(this);

        TextView info = new TextView(this);
        info.setText(
                conversationId.isEmpty()
                        ? "Gespreksgeheugen: nog niet gestart."
                        : "Gespreksgeheugen: actief.\n"
                        + "Nieuwe chats blijven gekoppeld aan dezelfde conversation."
        );
        box.addView(info);

        TextView label = new TextView(this);
        label.setText(
                "\nLangetermijnprofiel\n"
                        + "Je kunt dit zelf aanpassen. "
                        + "Je kunt ook zeggen: \"onthoud dat ...\""
        );
        box.addView(label);

        EditText memory = new EditText(this);
        memory.setMinLines(6);
        memory.setMaxLines(12);
        memory.setInputType(
                InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                        | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        );
        memory.setText(
                MemoryStore.getProfile(this)
        );
        box.addView(memory);

        AlertDialog dialog =
                new AlertDialog.Builder(this)
                        .setTitle(
                                "MAATJE v1.3.3 ONEPLUS – Geheugen"
                        )
                        .setView(box)
                        .setPositiveButton(
                                "Opslaan",
                                (d, which) -> {
                                    MemoryStore.setProfile(
                                            this,
                                            memory.getText()
                                                    .toString()
                                    );

                                    Toast.makeText(
                                            this,
                                            "Profielgeheugen opgeslagen.",
                                            Toast.LENGTH_SHORT
                                    ).show();
                                }
                        )
                        .setNeutralButton(
                                "Nieuw gesprek",
                                (d, which) -> {
                                    MemoryStore
                                            .clearConversation(
                                                    this
                                            );

                                    Toast.makeText(
                                            this,
                                            "Nieuw gesprek gestart. "
                                                    + "Profielgeheugen blijft behouden.",
                                            Toast.LENGTH_LONG
                                    ).show();
                                }
                        )
                        .setNegativeButton(
                                "Sluiten",
                                null
                        )
                        .create();

        dialog.show();
    }

    private void showApiKeyDialog(
            boolean mandatory
    ) {
        LinearLayout box =
                new LinearLayout(this);
        box.setOrientation(
                LinearLayout.VERTICAL
        );
        box.setPadding(
                dp(20),
                dp(4),
                dp(20),
                0
        );

        EditText key =
                new EditText(this);
        key.setHint("sk-…");
        key.setSingleLine(true);
        key.setInputType(
                InputType.TYPE_CLASS_TEXT
                        | InputType
                        .TYPE_TEXT_VARIATION_PASSWORD
        );
        key.setText(
                SecurePrefs.loadApiKey(this)
        );
        box.addView(key);

        TextView note =
                new TextView(this);
        note.setText(
                "De API-key wordt lokaal versleuteld "
                        + "met Android Keystore."
        );
        note.setPadding(
                0,
                dp(12),
                0,
                0
        );
        box.addView(note);

        AlertDialog dialog =
                new AlertDialog.Builder(this)
                        .setTitle(
                                "MAATJE v1.3.3 ONEPLUS – API"
                        )
                        .setView(box)
                        .setPositiveButton(
                                "Opslaan",
                                null
                        )
                        .setNegativeButton(
                                mandatory
                                        ? "Later"
                                        : "Annuleren",
                                null
                        )
                        .setNeutralButton(
                                "Wissen",
                                null
                        )
                        .create();

        dialog.setOnShowListener(d -> {
            dialog.getButton(
                    AlertDialog.BUTTON_POSITIVE
            ).setOnClickListener(v -> {
                String value =
                        key.getText()
                                .toString()
                                .trim();

                if (value.isEmpty()) {
                    Toast.makeText(
                            this,
                            "Vul een API-key in.",
                            Toast.LENGTH_SHORT
                    ).show();
                    return;
                }

                try {
                    SecurePrefs.saveApiKey(
                            this,
                            value
                    );

                    Toast.makeText(
                            this,
                            "API-key opgeslagen.",
                            Toast.LENGTH_SHORT
                    ).show();

                    dialog.dismiss();

                } catch (Exception e) {
                    Toast.makeText(
                            this,
                            "Opslaan mislukt: "
                                    + e.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();
                }
            });

            dialog.getButton(
                    AlertDialog.BUTTON_NEUTRAL
            ).setOnClickListener(v -> {
                SecurePrefs.clear(this);
                MemoryStore.clearConversation(this);
                key.setText("");

                Toast.makeText(
                        this,
                        "API-key gewist.",
                        Toast.LENGTH_SHORT
                ).show();
            });
        });

        dialog.show();
    }

    private void beginConversationSession() {
        if (!ConversationSettings.enabled(this)) {
            conversationModeActive = false;
            conversationExpiresAt = 0L;
            return;
        }

        conversationModeActive = true;
        conversationExpiresAt = 0L;

        mainHandler.removeCallbacks(
                conversationTimeoutRunnable
        );
    }

    private void pauseConversationTimer() {
        mainHandler.removeCallbacks(
                conversationTimeoutRunnable
        );

        conversationExpiresAt = 0L;
    }

    private boolean isConversationWindowOpen() {
        return conversationModeActive
                && conversationExpiresAt > 0L
                && System.currentTimeMillis()
                < conversationExpiresAt;
    }

    private void scheduleFollowUpListening(
            long delayMs,
            boolean resetWindow
    ) {
        if (!conversationModeActive
                || !ConversationSettings.enabled(this)
                || !appVisible
                || chatBusy
                || assistantSpeaking) {
            return;
        }

        long blockedFor =
                normalListeningBlockedUntil
                        - System.currentTimeMillis();

        if (blockedFor > 0L) {
            delayMs = Math.max(
                    delayMs,
                    blockedFor + 25L
            );
        }

        if (resetWindow
                || conversationExpiresAt <= 0L) {
            conversationExpiresAt =
                    System.currentTimeMillis()
                            + ConversationSettings
                            .timeoutSeconds(this)
                            * 1000L;
        }

        long remaining =
                conversationExpiresAt
                        - System.currentTimeMillis();

        if (remaining <= 0L) {
            endConversationSession();
            scheduleWakeListening(250);
            return;
        }

        mainHandler.removeCallbacks(
                conversationTimeoutRunnable
        );

        mainHandler.postDelayed(
                conversationTimeoutRunnable,
                remaining
        );

        stateText.setText(
                "CONVERSATION • WAITING"
        );

        mainHandler.postDelayed(
                () -> {
                    if (conversationModeActive
                            && isConversationWindowOpen()
                            && !chatBusy
                            && !assistantSpeaking
                            && mediaPlayer == null
                            && System.currentTimeMillis()
                            >= normalListeningBlockedUntil) {
                        startCommandListeningInternal();
                    }
                },
                delayMs
        );
    }

    private final Runnable conversationTimeoutRunnable =
            new Runnable() {
                @Override
                public void run() {
                    if (!conversationModeActive) {
                        return;
                    }

                    if (isConversationWindowOpen()) {
                        long remaining =
                                conversationExpiresAt
                                        - System.currentTimeMillis();

                        mainHandler.postDelayed(
                                this,
                                Math.max(
                                        100L,
                                        remaining
                                )
                        );
                        return;
                    }

                    endConversationSession();
                    stateText.setText(
                            "STANDBY • HEY MAATJE"
                    );
                    scheduleWakeListening(250);
                }
            };

    private void endConversationSession() {
        mainHandler.removeCallbacks(
                conversationTimeoutRunnable
        );

        conversationModeActive = false;
        conversationExpiresAt = 0L;

        if (commandListening
                && speechRecognizer != null) {
            ignoreNextRecognitionError = true;
            commandListening = false;

            try {
                speechRecognizer.cancel();
            } catch (Exception ignored) {}
        }
    }

    private void resumeAfterAssistant(
            long delayMs
    ) {
        if (conversationModeActive
                && ConversationSettings.enabled(this)) {
            scheduleFollowUpListening(
                    delayMs,
                    true
            );
        } else {
            scheduleWakeListening(
                    delayMs
            );
        }
    }

    private void startStopWordListening() {
        if (mediaPlayer == null
                || offlineWakeWord == null
                || !offlineWakeWord.isReady()) {
            return;
        }

        offlineWakeWord.startStopListening();

        mainHandler.postDelayed(
                () -> {
                    if (wakeDebugText != null
                            && WakeWordSettings.debugEnabled(this)
                            && assistantSpeaking) {
                        wakeDebugText.setText(
                                offlineWakeWord.isEchoCancellationActive()
                                        ? "AEC • ON • STOP WORD ACTIVE"
                                        : "AEC • FALLBACK • STOP WORD ACTIVE"
                        );
                    }
                },
                120
        );
    }

    private void handleSpokenStop() {
        if (mediaPlayer == null) {
            return;
        }

        stopPlayer();

        normalListeningBlockedUntil =
                System.currentTimeMillis()
                        + 200L;

        stateText.setText("GESTOPT");

        if (conversationModeActive) {
            scheduleFollowUpListening(
                    250,
                    true
            );
        } else {
            scheduleWakeListening(300);
        }
    }

    private String formatRecognizedSpeech(
            String value
    ) {
        if (value == null) {
            return "";
        }

        String text =
                value.trim()
                        .replaceAll("\\s+", " ");

        if (text.isEmpty()) {
            return text;
        }

        // Spoken punctuation remains useful even on recognizers that
        // ignore Android's automatic formatting request.
        text = text
                .replaceAll(
                        "(?i)\\s+komma(?=\\s|$)",
                        ","
                )
                .replaceAll(
                        "(?i)\\s+punt(?=\\s|$)",
                        "."
                )
                .replaceAll(
                        "(?i)\\s+vraagteken(?=\\s|$)",
                        "?"
                )
                .replaceAll(
                        "(?i)\\s+uitroepteken(?=\\s|$)",
                        "!"
                )
                .replaceAll("\\s+([,.?!])", "$1")
                .replaceAll("([,.?!])(?=\\p{L})", "$1 ")
                .replaceAll("\\s+", " ")
                .trim();

        // Capitalize the first actual letter.
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            if (Character.isLetter(c)) {
                text =
                        text.substring(0, i)
                                + Character.toUpperCase(c)
                                + text.substring(i + 1);
                break;
            }
        }

        if (hasSentenceEnding(text)) {
            return text;
        }

        String lower =
                text.toLowerCase(
                        java.util.Locale.ROOT
                );

        boolean looksLikeQuestion =
                startsWithQuestionPhrase(lower);

        return text
                + (looksLikeQuestion
                        ? "?"
                        : ".");
    }

    private boolean hasSentenceEnding(
            String value
    ) {
        if (value == null
                || value.isEmpty()) {
            return false;
        }

        char last =
                value.charAt(
                        value.length() - 1
                );

        return last == '.'
                || last == '?'
                || last == '!';
    }

    private boolean startsWithQuestionPhrase(
            String value
    ) {
        String[] questionStarts = {
                "wie ",
                "wat ",
                "waar ",
                "wanneer ",
                "waarom ",
                "hoe ",
                "welke ",
                "welk ",
                "hoeveel ",
                "kan ",
                "kun ",
                "kunnen ",
                "mag ",
                "moet ",
                "moeten ",
                "is ",
                "zijn ",
                "ben ",
                "heb ",
                "heeft ",
                "hebben ",
                "weet ",
                "weten ",
                "wil ",
                "willen ",
                "zou ",
                "zouden ",
                "zal ",
                "zullen ",
                "wordt ",
                "worden ",
                "doe ",
                "doet ",
                "klopt ",
                "bestaat ",
                "krijg ",
                "krijgen "
        };

        for (String start : questionStarts) {
            if (value.startsWith(start)) {
                return true;
            }
        }

        return false;
    }

    private boolean isConversationEndCommand(
            String value
    ) {
        if (value == null) return false;

        String normalized =
                value.toLowerCase(java.util.Locale.ROOT)
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

        if (normalized.isEmpty()) {
            return false;
        }

        if (normalized.contains("stop gesprek")
                || normalized.contains("stop het gesprek")
                || normalized.contains("slaap maar")
                || normalized.contains("ga maar slapen")) {
            return true;
        }

        boolean mentionsMaatje =
                normalized.contains("maatje")
                        || normalized.contains("maartje")
                        || normalized.contains("maatie");

        boolean endWord =
                normalized.contains(" klaar")
                        || normalized.startsWith("klaar ")
                        || normalized.endsWith(" klaar")
                        || normalized.contains(" standby")
                        || normalized.contains(" slapen")
                        || normalized.contains(" stop")
                        || normalized.startsWith("stop ");

        return mentionsMaatje && endWord;
    }

    private void updateWakeDebug(
            String text
    ) {
        if (wakeDebugText == null) return;

        if (!WakeWordSettings.debugEnabled(this)) {
            wakeDebugText.setVisibility(
                    View.GONE
            );
            return;
        }

        wakeDebugText.setVisibility(
                View.VISIBLE
        );

        wakeDebugText.setText(
                "HEARD • " + text
        );
    }

    private void updateWakeDebugVisibility() {
        if (wakeDebugText == null) return;

        boolean visible =
                WakeWordSettings.debugEnabled(this);

        wakeDebugText.setVisibility(
                visible
                        ? View.VISIBLE
                        : View.GONE
        );

        if (!visible) {
            wakeDebugText.setText("");
        }
    }

    private void setBusy(
            boolean busy,
            String label
    ) {
        chatBusy = busy;
        stateText.setText(label);
        micButton.setEnabled(!busy);
        sendButton.setEnabled(!busy);
        input.setEnabled(!busy);
    }

    private void append(String text) {
        transcript.append(text);

        transcript.post(() -> {
            if (transcript.getLayout() == null
                    || transcript.getLineCount()
                    == 0) {
                return;
            }

            int contentHeight =
                    transcript.getLayout()
                            .getLineBottom(
                                    transcript
                                            .getLineCount()
                                            - 1
                            );

            int visibleHeight =
                    transcript.getHeight()
                            - transcript
                            .getPaddingTop()
                            - transcript
                            .getPaddingBottom();

            transcript.scrollTo(
                    0,
                    Math.max(
                            0,
                            contentHeight
                                    - visibleHeight
                    )
            );
        });
    }

    private void speak(String text) {
        speak(text, false);
    }

    private void speak(
            String text,
            boolean forcePlayback
    ) {
        if (!forcePlayback
                && !VoiceSettings.autoSpeak(this)) {
            resumeAfterAssistant(350);
            return;
        }

        String key =
                SecurePrefs.loadApiKey(this);

        if (key.isEmpty()) {
            resumeAfterAssistant(350);
            return;
        }

        String cleaned =
                text.replace("```", "")
                        .replace("**", "")
                        .trim();

        String voice =
                VoiceSettings.voice(this);
        float speed =
                VoiceSettings.speed(this);
        String style =
                VoiceSettings.style(this)
                        + "\n"
                        + PersonalitySettings.voiceStyle(this);

        voiceExecutor.submit(() -> {
            try {
                File audio =
                        OpenAiSpeech.synthesize(
                                MainActivity.this,
                                key,
                                cleaned,
                                voice,
                                style,
                                speed
                        );

                if (audio == null
                        || !audio.exists()
                        || audio.length() <= 0L) {
                    if (audio != null) {
                        audio.delete();
                    }

                    throw new Exception(
                            "TTS leverde geen bruikbaar audiobestand."
                    );
                }

                runOnUiThread(
                        () -> playAudio(audio)
                );

            } catch (Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(
                            MainActivity.this,
                            "Stemfout: "
                                    + e.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();

                    recoverAfterAssistantFailure(700);
                });
            }
        });
    }

    private void testCloudVoice(
            String voice,
            float speed,
            String instructions
    ) {
        String key =
                SecurePrefs.loadApiKey(this);

        if (key.isEmpty()) {
            Toast.makeText(
                    this,
                    "Vul eerst een API-key in.",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        stopPlayer();

        voiceExecutor.submit(() -> {
            try {
                File audio =
                        OpenAiSpeech.synthesize(
                                MainActivity.this,
                                key,
                                "Goedemiddag. MAATJE is online. "
                                        + "Alle systemen functioneren normaal.",
                                voice,
                                instructions,
                                speed
                        );

                runOnUiThread(
                        () -> playAudio(audio)
                );

            } catch (Exception e) {
                runOnUiThread(() ->
                        Toast.makeText(
                                MainActivity.this,
                                "Stemtest mislukt: "
                                        + e.getMessage(),
                                Toast.LENGTH_LONG
                        ).show()
                );
            }
        });
    }

    private void playAudio(File file) {
        stopPlayer();

        try {
            beginCommunicationAudio();

            mediaPlayer = new MediaPlayer();
            mediaPlayer.setAudioAttributes(
                    new AudioAttributes.Builder()
                            .setUsage(
                                    AudioAttributes.USAGE_VOICE_COMMUNICATION
                            )
                            .setContentType(
                                    AudioAttributes.CONTENT_TYPE_SPEECH
                            )
                            .build()
            );
            mediaPlayer.setVolume(
                    1.0f,
                    1.0f
            );
            mediaPlayer.setDataSource(
                    file.getAbsolutePath()
            );

            mediaPlayer.setOnPreparedListener(
                    mp -> {
                        assistantSpeaking = true;

                        stopRecognitionSession();

                        mp.start();

                        mainHandler.postDelayed(
                                this::startStopWordListening,
                                250
                        );
                    }
            );

            mediaPlayer.setOnCompletionListener(
                    mp -> {
                        mp.release();

                        if (mediaPlayer == mp) {
                            mediaPlayer = null;
                        }

                        if (offlineWakeWord != null) {
                            offlineWakeWord.stop();
                        }

                        assistantSpeaking = false;
                        endCommunicationAudio();

                        normalListeningBlockedUntil =
                                System.currentTimeMillis()
                                        + POST_TTS_COOLDOWN_MS;

                        file.delete();
                        resumeAfterAssistant(
                                POST_TTS_COOLDOWN_MS
                        );
                    }
            );

            mediaPlayer.setOnErrorListener(
                    (mp, what, extra) -> {
                        mp.release();

                        if (mediaPlayer == mp) {
                            mediaPlayer = null;
                        }

                        if (offlineWakeWord != null) {
                            offlineWakeWord.stop();
                        }

                        assistantSpeaking = false;
                        endCommunicationAudio();

                        file.delete();
                        recoverAfterAssistantFailure(550);
                        return true;
                    }
            );

            mediaPlayer.prepareAsync();

        } catch (Exception e) {
            assistantSpeaking = false;
            endCommunicationAudio();
            file.delete();

            Toast.makeText(
                    this,
                    "Audio afspelen mislukt: "
                            + e.getMessage(),
                    Toast.LENGTH_LONG
            ).show();

            recoverAfterAssistantFailure(550);
        }
    }

    private void recoverAfterAssistantFailure(
            long delayMs
    ) {
        stopPlayer();

        normalListeningBlockedUntil =
                System.currentTimeMillis()
                        + Math.max(250L, delayMs);

        resumeAfterAssistant(
                Math.max(250L, delayMs)
        );
    }

    @SuppressWarnings("deprecation")
    private void beginCommunicationAudio() {
        if (audioManager == null) {
            return;
        }

        if (!communicationAudioActive) {
            previousAudioMode =
                    audioManager.getMode();
            previousSpeakerphoneOn =
                    audioManager.isSpeakerphoneOn();

            if (Build.VERSION.SDK_INT
                    >= Build.VERSION_CODES.S) {
                previousCommunicationDevice =
                        audioManager.getCommunicationDevice();
            }

            try {
                previousVoiceCallVolume =
                        audioManager.getStreamVolume(
                                AudioManager.STREAM_VOICE_CALL
                        );

                int maxVoiceVolume =
                        audioManager.getStreamMaxVolume(
                                AudioManager.STREAM_VOICE_CALL
                        );

                if (maxVoiceVolume > 0
                        && previousVoiceCallVolume
                        < maxVoiceVolume) {
                    audioManager.setStreamVolume(
                            AudioManager.STREAM_VOICE_CALL,
                            maxVoiceVolume,
                            0
                    );
                    voiceCallVolumeBoosted = true;
                }
            } catch (Exception ignored) {
                previousVoiceCallVolume = -1;
                voiceCallVolumeBoosted = false;
            }

            communicationAudioActive = true;
        }

        try {
            audioManager.setMode(
                    AudioManager.MODE_IN_COMMUNICATION
            );

            if (Build.VERSION.SDK_INT
                    >= Build.VERSION_CODES.S) {
                AudioDeviceInfo speaker = null;

                for (AudioDeviceInfo device :
                        audioManager
                                .getAvailableCommunicationDevices()) {
                    if (device.getType()
                            == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
                        speaker = device;
                        break;
                    }
                }

                if (speaker != null) {
                    audioManager.setCommunicationDevice(
                            speaker
                    );
                }
            } else {
                audioManager.setSpeakerphoneOn(
                        true
                );
            }
        } catch (Exception ignored) {}
    }

    @SuppressWarnings("deprecation")
    private void endCommunicationAudio() {
        if (!communicationAudioActive
                || audioManager == null) {
            return;
        }

        try {
            if (Build.VERSION.SDK_INT
                    >= Build.VERSION_CODES.S) {
                if (previousCommunicationDevice != null) {
                    audioManager.setCommunicationDevice(
                            previousCommunicationDevice
                    );
                } else {
                    audioManager.clearCommunicationDevice();
                }
            } else {
                audioManager.setSpeakerphoneOn(
                        previousSpeakerphoneOn
                );
            }
        } catch (Exception ignored) {}

        try {
            audioManager.setMode(
                    previousAudioMode
            );
        } catch (Exception ignored) {}

        if (voiceCallVolumeBoosted
                && previousVoiceCallVolume >= 0) {
            try {
                audioManager.setStreamVolume(
                        AudioManager.STREAM_VOICE_CALL,
                        previousVoiceCallVolume,
                        0
                );
            } catch (Exception ignored) {}
        }

        previousVoiceCallVolume = -1;
        voiceCallVolumeBoosted = false;
        previousCommunicationDevice = null;
        communicationAudioActive = false;
    }

    private void stopPlayer() {
        assistantSpeaking = false;

        if (offlineWakeWord != null) {
            offlineWakeWord.stop();
        }

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

        endCommunicationAudio();
    }

    @SuppressWarnings("deprecation")
    private void installDedicatedNavigationGuards() {
        View decor =
                getWindow().getDecorView();

        decor.setOnSystemUiVisibilityChangeListener(
                visibility ->
                        mainHandler.postDelayed(
                                this::enforceDedicatedUi,
                                45L
                        )
        );

        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher()
                    .registerOnBackInvokedCallback(
                            android.window.OnBackInvokedDispatcher
                                    .PRIORITY_DEFAULT,
                            this::enforceDedicatedUi
                    );
        }
    }

    private void ensureDedicatedLockTask() {
        try {
            DevicePolicyManager dpm =
                    (DevicePolicyManager)
                            getSystemService(
                                    DEVICE_POLICY_SERVICE
                            );

            if (dpm != null
                    && dpm.isLockTaskPermitted(
                    getPackageName()
            )) {
                startLockTask();
            }
        } catch (Exception ignored) {}
    }

    @SuppressWarnings("deprecation")
    private void enforceDedicatedUi() {
        View decor =
                getWindow().getDecorView();

        int flags =
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;

        decor.setSystemUiVisibility(
                flags
        );

        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(
                    false
            );

            WindowInsetsController controller =
                    getWindow().getInsetsController();

            if (controller != null) {
                controller.hide(
                        WindowInsets.Type.statusBars()
                                | WindowInsets.Type.navigationBars()
                );
            }
        }

        if (Build.VERSION.SDK_INT >= 29) {
            decor.post(
                    () -> {
                        int width =
                                decor.getWidth();
                        int height =
                                decor.getHeight();

                        if (width <= 0
                                || height <= 0) {
                            return;
                        }

                        int edge =
                                Math.max(
                                        dp(36),
                                        width / 12
                                );

                        ArrayList<Rect> exclusions =
                                new ArrayList<>();

                        exclusions.add(
                                new Rect(
                                        0,
                                        0,
                                        edge,
                                        height
                                )
                        );

                        exclusions.add(
                                new Rect(
                                        width - edge,
                                        0,
                                        width,
                                        height
                                )
                        );

                        decor.setSystemGestureExclusionRects(
                                exclusions
                        );
                    }
            );
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        // Dedicated terminal: back navigation is intentionally disabled.
        enforceDedicatedUi();
    }

    @Override
    public void onWindowFocusChanged(
            boolean hasFocus
    ) {
        super.onWindowFocusChanged(
                hasFocus
        );

        if (hasFocus) {
            ensureDedicatedLockTask();
            enforceDedicatedUi();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        ensureDedicatedLockTask();
        enforceDedicatedUi();

        appVisible = true;
        wakeWordEnabled =
                WakeWordSettings.enabled(this);

        MaatjeVoiceInteractionService
                .setActivityVisible(true);

        if (AssistantSettings.isSelected(this)
                && stateText != null
                && !pendingAssistantInvocation) {
            stateText.setText(
                    "READY • DEFAULT ASSISTANT"
            );
        }

        if (pendingCameraInvocation) {
            pendingCameraInvocation = false;
            stateText.setText(
                    "CAMERA • STARTING"
            );
            mainHandler.postDelayed(
                    this::startCameraVision,
                    300L
            );
        } else if (pendingAssistantInvocation) {
            pendingAssistantInvocation = false;

            stateText.setText(
                    "ASSISTANT • LISTENING"
            );

            mainHandler.postDelayed(
                    this::startRealtimeVoice,
                    350L
            );
        } else {
            scheduleWakeListening(600);
        }
    }

    @Override
    protected void onPause() {
        appVisible = false;
        stopCameraVision();
        stopRealtimeVoice();
        endConversationSession();
        stopRecognitionSession();

        MaatjeVoiceInteractionService
                .setActivityVisible(false);

        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        mainHandler.removeCallbacksAndMessages(null);

        chatExecutor.shutdownNow();
        voiceExecutor.shutdownNow();

        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }

        if (offlineWakeWord != null) {
            offlineWakeWord.destroy();
        }

        stopCameraVision();
        stopPlayer();
    }

    private Button button(
            String text,
            int bg,
            int fg
    ) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(text);
        b.setTextColor(fg);
        b.setTextSize(18);
        b.setGravity(Gravity.CENTER);
        b.setPadding(0, 0, 0, 0);
        b.setBackground(
                roundRect(
                        bg,
                        18,
                        bg == PANEL
                                ? BORDER
                                : bg
                )
        );
        return b;
    }

    private GradientDrawable roundRect(
            int color,
            int radiusDp,
            int strokeColor
    ) {
        GradientDrawable d =
                new GradientDrawable();

        d.setColor(color);
        d.setCornerRadius(
                dp(radiusDp)
        );
        d.setStroke(
                dp(1),
                strokeColor
        );

        return d;
    }

    private int dp(int value) {
        return Math.round(
                value
                        * getResources()
                        .getDisplayMetrics()
                        .density
        );
    }
}
