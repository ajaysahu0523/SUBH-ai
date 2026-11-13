package com.example;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.ai.AiRepository;
import com.example.ai.PlaceholderAiRepository;
import com.example.assistant.CommandProcessor;
import com.example.assistant.ConversationMemory;
import com.example.assistant.LanguageUtils;
import com.example.assistant.LiveInfoService;
import com.example.assistant.MathAndUnitEvaluator;
import com.example.assistant.VoiceAssistantManager;
import com.example.data.ChatHistoryDbHelper;
import com.example.data.UserPreferencesManager;
import com.example.model.ChatMessage;
import com.example.model.ReminderItem;
import com.example.model.SavedNote;
import com.example.ui.ChatAdapter;
import com.example.ui.HistoryActivity;
import com.example.ui.JarvisOrbView;
import com.example.ui.ProfileActivity;
import com.example.ui.SettingsActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.snackbar.Snackbar;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity implements VoiceAssistantManager.Listener {

    private static final Pattern NAME_PATTERN_EN = Pattern.compile(
            "(?i)(?:my name is|call me|i am)\\s+([a-zA-Z][a-zA-Z\\s]{1,24})"
    );
    private static final Pattern NAME_PATTERN_HI = Pattern.compile(
            "(?i)(?:मेरा नाम|mera naam|mujhe)\\s+([\\p{L}][\\p{L}\\s]{1,24}?)(?:\\s+(?:है|hai|bulao|बुलाओ))?$"
    );
    private static final Pattern INTEREST_PATTERN = Pattern.compile(
            "(?i)(?:remember that i like|i really like|i like|mujhe pasand hai|मुझे पसंद है)\\s+(.{2,40})"
    );

    private ChatHistoryDbHelper dbHelper;
    private UserPreferencesManager prefsManager;
    private ConversationMemory conversationMemory;
    private AiRepository aiRepository;
    private LiveInfoService liveInfoService;
    private VoiceAssistantManager voiceManager;
    private ChatAdapter chatAdapter;

    private View rootContainer;
    private View headerContainer;
    private View bottomControlDock;
    private JarvisOrbView jarvisOrbView;
    private TextView assistantStatusText;
    private TextView liveTranscriptText;
    private TextView contextTopicBadge;
    private MaterialSwitch conversationModeSwitch;
    private TextView langAutoBtn;
    private TextView langHindiBtn;
    private TextView langEnglishBtn;
    private ImageButton historyButton;
    private ImageButton profileButton;
    private ImageButton muteToggleButton;
    private ImageButton settingsButton;
    private RecyclerView chatRecyclerView;
    private EditText messageInput;
    private ImageButton sendButton;
    private ImageButton micEnabledToggleButton;
    private View micGlowRing;
    private ImageButton startListeningButton;
    private LinearLayout stopListeningButton;
    private LinearLayout memoryConsentCard;
    private TextView memoryConsentPromptText;
    private MaterialButton memoryAllowButton;
    private MaterialButton memoryDenyButton;

    private ObjectAnimator micPulseAnimator;
    private boolean isSleepMode = false;
    private boolean isThinking = false;
    private boolean greetedOnStartup = false;
    private boolean pendingSleepStandby = false;
    private String selectedLangPref = "auto";
    private String pendingMemoryName = "";
    private String pendingMemoryInterest = "";
    private String pendingConsentLang = LanguageUtils.LANG_ENGLISH;
    private CommandProcessor.SensitiveActionType pendingSensitiveAction = CommandProcessor.SensitiveActionType.NONE;
    private String pendingActionPayload = "";

    private final ActivityResultLauncher<String> micPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    beginVoiceListening(pendingSleepStandby);
                } else {
                    showPermissionDeniedFeedback();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        prefsManager = new UserPreferencesManager(this);
        prefsManager.applySavedTheme();
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        dbHelper = new ChatHistoryDbHelper(this);
        conversationMemory = new ConversationMemory();
        aiRepository = new PlaceholderAiRepository(this, prefsManager, conversationMemory);
        liveInfoService = new LiveInfoService(this);
        voiceManager = new VoiceAssistantManager(this, this);

        bindViews();
        applyWindowInsets();
        setupRecyclerView();
        setupMicPulseAnimator();
        setupControls();
        restoreStateAndHistory();
    }

    private void bindViews() {
        rootContainer = findViewById(R.id.rootContainer);
        headerContainer = findViewById(R.id.headerContainer);
        bottomControlDock = findViewById(R.id.bottomControlDock);
        jarvisOrbView = findViewById(R.id.jarvisOrbView);
        assistantStatusText = findViewById(R.id.assistantStatusText);
        liveTranscriptText = findViewById(R.id.liveTranscriptText);
        contextTopicBadge = findViewById(R.id.contextTopicBadge);
        conversationModeSwitch = findViewById(R.id.conversationModeSwitch);
        langAutoBtn = findViewById(R.id.langAutoBtn);
        langHindiBtn = findViewById(R.id.langHindiBtn);
        langEnglishBtn = findViewById(R.id.langEnglishBtn);
        historyButton = findViewById(R.id.historyButton);
        profileButton = findViewById(R.id.profileButton);
        muteToggleButton = findViewById(R.id.muteToggleButton);
        settingsButton = findViewById(R.id.settingsButton);
        chatRecyclerView = findViewById(R.id.chatRecyclerView);
        messageInput = findViewById(R.id.messageInput);
        sendButton = findViewById(R.id.sendButton);
        micEnabledToggleButton = findViewById(R.id.micEnabledToggleButton);
        micGlowRing = findViewById(R.id.micGlowRing);
        startListeningButton = findViewById(R.id.startListeningButton);
        stopListeningButton = findViewById(R.id.stopListeningButton);
        memoryConsentCard = findViewById(R.id.memoryConsentCard);
        memoryConsentPromptText = findViewById(R.id.memoryConsentPromptText);
        memoryAllowButton = findViewById(R.id.memoryAllowButton);
        memoryDenyButton = findViewById(R.id.memoryDenyButton);
    }

    private void applyWindowInsets() {
        final int headerTopBase = headerContainer.getPaddingTop();
        final int dockBottomBase = bottomControlDock.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(rootContainer, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            headerContainer.setPadding(
                    headerContainer.getPaddingLeft(),
                    headerTopBase + systemBars.top,
                    headerContainer.getPaddingRight(),
                    headerContainer.getPaddingBottom()
            );
            int bottomPad = Math.max(systemBars.bottom, ime.bottom);
            bottomControlDock.setPadding(
                    bottomControlDock.getPaddingLeft(),
                    bottomControlDock.getPaddingTop(),
                    bottomControlDock.getPaddingRight(),
                    dockBottomBase + bottomPad
            );
            return insets;
        });
    }

    private void setupRecyclerView() {
        chatAdapter = new ChatAdapter(new ChatAdapter.OnMessageActionListener() {
            @Override
            public void onSpeakMessage(ChatMessage message) {
                if (voiceManager.isMuted()) {
                    Toast.makeText(MainActivity.this, R.string.voice_muted, Toast.LENGTH_SHORT).show();
                    return;
                }
                voiceManager.speak(message.getText(), message.getLanguageCode(), false, true);
            }

            @Override
            public void onCopyMessage(ChatMessage message) {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard != null) {
                    clipboard.setPrimaryClip(ClipData.newPlainText("SUBH AI", message.getText()));
                    Toast.makeText(MainActivity.this, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show();
                }
            }
        });
        LinearLayoutManager lm = new LinearLayoutManager(this);
        lm.setStackFromEnd(true);
        chatRecyclerView.setLayoutManager(lm);
        chatRecyclerView.setAdapter(chatAdapter);
    }

    private void setupMicPulseAnimator() {
        micPulseAnimator = ObjectAnimator.ofPropertyValuesHolder(
                micGlowRing,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 0.95f, 1.32f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.95f, 1.32f),
                PropertyValuesHolder.ofFloat(View.ALPHA, 0.35f, 0.95f)
        );
        micPulseAnimator.setDuration(900);
        micPulseAnimator.setRepeatCount(ValueAnimator.INFINITE);
        micPulseAnimator.setRepeatMode(ValueAnimator.REVERSE);
    }

    private void setupControls() {
        boolean convEnabled = prefsManager.isConversationModeEnabled();
        conversationModeSwitch.setChecked(convEnabled);
        voiceManager.setContinuousMode(convEnabled);
        updateConversationSwitchLabel(convEnabled);

        conversationModeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefsManager.setConversationModeEnabled(isChecked);
            voiceManager.setContinuousMode(isChecked);
            updateConversationSwitchLabel(isChecked);
            if (!isChecked && voiceManager.isListening() && !isSleepMode) {
                voiceManager.stopListening();
                updateUiState(JarvisOrbView.AssistantState.IDLE, getReadyStatusText());
            } else if (isChecked && prefsManager.isMicEnabled() && !isSleepMode
                    && !voiceManager.isListening() && !voiceManager.isSpeaking()) {
                requestMicAndStartListening(false);
            }
        });

        jarvisOrbView.setBatterySaverMode(prefsManager.isBatterySaverMode());
        jarvisOrbView.setOnClickListener(v -> {
            if (isSleepMode) {
                wakeUpFromSleep("");
            } else if (voiceManager.isSpeaking()) {
                voiceManager.stopSpeaking(false);
                if (prefsManager.isMicEnabled()) {
                    requestMicAndStartListening(false);
                } else {
                    updateUiState(JarvisOrbView.AssistantState.IDLE, getReadyStatusText());
                }
            } else if (voiceManager.isListening()) {
                voiceManager.stopListening();
                updateUiState(JarvisOrbView.AssistantState.IDLE, getReadyStatusText());
            } else {
                requestMicAndStartListening(false);
            }
        });

        selectedLangPref = prefsManager.getPreferredLanguage();
        updateLanguageSelectorUi();

        langAutoBtn.setOnClickListener(v -> selectLanguage("auto"));
        langHindiBtn.setOnClickListener(v -> selectLanguage("hi"));
        langEnglishBtn.setOnClickListener(v -> selectLanguage("en"));

        voiceManager.setMuted(prefsManager.isTtsMuted());
        updateMuteButtonUi();

        muteToggleButton.setOnClickListener(v -> {
            boolean newMuted = !voiceManager.isMuted();
            voiceManager.setMuted(newMuted);
            prefsManager.setTtsMuted(newMuted);
            updateMuteButtonUi();
            Toast.makeText(this, newMuted ? R.string.voice_muted : R.string.voice_unmuted, Toast.LENGTH_SHORT).show();
        });

        updateMicToggleUi();
        micEnabledToggleButton.setOnClickListener(v -> {
            boolean nextEnabled = !prefsManager.isMicEnabled();
            prefsManager.setMicEnabled(nextEnabled);
            voiceManager.setMicEnabled(nextEnabled);
            updateMicToggleUi();
            Toast.makeText(this, nextEnabled ? R.string.mic_enabled_msg : R.string.mic_disabled_msg, Toast.LENGTH_SHORT).show();
            updateUiState(
                    isSleepMode ? JarvisOrbView.AssistantState.SLEEPING : JarvisOrbView.AssistantState.IDLE,
                    getReadyStatusText()
            );
        });

        historyButton.setOnClickListener(v -> openScreen(new Intent(this, HistoryActivity.class)));
        profileButton.setOnClickListener(v -> openScreen(new Intent(this, ProfileActivity.class)));
        settingsButton.setOnClickListener(v -> openSettingsSection(SettingsActivity.SECTION_VOICE));

        // Home Quick Access Feature Strip
        findViewById(R.id.quickChatFocusBtn).setOnClickListener(v -> {
            scrollToBottom();
            messageInput.requestFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(messageInput, InputMethodManager.SHOW_IMPLICIT);
            }
        });
        findViewById(R.id.quickVoiceSettingsBtn).setOnClickListener(v -> openSettingsSection(SettingsActivity.SECTION_VOICE));
        findViewById(R.id.quickHistoryBtn).setOnClickListener(v -> openScreen(new Intent(this, HistoryActivity.class)));
        findViewById(R.id.quickAiSettingsBtn).setOnClickListener(v -> openSettingsSection(SettingsActivity.SECTION_AI));
        findViewById(R.id.quickMemorySettingsBtn).setOnClickListener(v -> openSettingsSection(SettingsActivity.SECTION_MEMORY));

        startListeningButton.setOnClickListener(v -> {
            if (isSleepMode) {
                wakeUpFromSleep("");
                return;
            }
            if (voiceManager.isSpeaking()) {
                voiceManager.stopSpeaking(false);
            }
            requestMicAndStartListening(false);
        });

        stopListeningButton.setOnClickListener(v -> {
            voiceManager.stopListening();
            voiceManager.stopSpeaking(false);
            isThinking = false;
            if (isSleepMode) {
                updateUiState(JarvisOrbView.AssistantState.SLEEPING, getString(R.string.status_sleeping));
            } else {
                updateUiState(JarvisOrbView.AssistantState.IDLE, getReadyStatusText());
            }
            liveTranscriptText.setText(R.string.assistant_subtitle_ready);
        });

        sendButton.setOnClickListener(v -> submitTypedMessage());
        messageInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                submitTypedMessage();
                return true;
            }
            return false;
        });

        findViewById(R.id.chipWeather).setOnClickListener(v -> handleUserInput(getString(R.string.chip_weather), false));
        findViewById(R.id.chipNews).setOnClickListener(v -> handleUserInput(getString(R.string.chip_news), false));
        findViewById(R.id.chipYoutube).setOnClickListener(v -> handleUserInput(getString(R.string.chip_youtube), false));
        findViewById(R.id.chipMath).setOnClickListener(v -> handleUserInput(getString(R.string.chip_math), false));
        findViewById(R.id.chipGoogle).setOnClickListener(v -> handleUserInput(getString(R.string.chip_google), false));
        findViewById(R.id.chipJoke).setOnClickListener(v -> handleUserInput(getString(R.string.chip_joke), false));
        findViewById(R.id.chipHindiAsk).setOnClickListener(v -> handleUserInput(getString(R.string.chip_hindi_ask), false));

        memoryAllowButton.setOnClickListener(v -> grantPendingConsent());
        memoryDenyButton.setOnClickListener(v -> denyPendingConsent());
    }

    private void openSettingsSection(String section) {
        Intent intent = new Intent(this, SettingsActivity.class);
        intent.putExtra(SettingsActivity.EXTRA_SECTION, section);
        openScreen(intent);
    }

    @SuppressWarnings("deprecation")
    private void openScreen(Intent intent) {
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    private void restoreStateAndHistory() {
        List<ChatMessage> saved = dbHelper.getAllMessages();
        if (saved.isEmpty()) {
            String greeting = getString(R.string.initial_greeting);
            long now = System.currentTimeMillis();
            long id = dbHelper.insertMessage(greeting, false, LanguageUtils.LANG_HINDI, now, false, "GREETING");
            saved.add(new ChatMessage(id, greeting, false, LanguageUtils.LANG_HINDI, now, false, "GREETING"));
        }
        chatAdapter.setMessages(saved);
        conversationMemory.hydrateFromHistory(saved);
        updateTopicBadge();
        scrollToBottom();
    }

    private void updateTopicBadge() {
        String topic = conversationMemory.getCurrentTopic();
        if (topic == null || topic.isEmpty() || "general".equals(topic)) {
            contextTopicBadge.setVisibility(View.GONE);
        } else {
            contextTopicBadge.setVisibility(View.VISIBLE);
            contextTopicBadge.setText("Active Context: " + topic.toUpperCase(Locale.ROOT));
        }
    }

    private void selectLanguage(String langPref) {
        selectedLangPref = langPref;
        prefsManager.savePreferredLanguage(langPref);
        updateLanguageSelectorUi();
        if (voiceManager.isListening()) {
            voiceManager.startListening(selectedLangPref, isSleepMode);
        }
    }

    private void updateLanguageSelectorUi() {
        styleLangPill(langAutoBtn, "auto".equals(selectedLangPref));
        styleLangPill(langHindiBtn, "hi".equals(selectedLangPref));
        styleLangPill(langEnglishBtn, "en".equals(selectedLangPref));
    }

    private void styleLangPill(TextView pill, boolean selected) {
        if (selected) {
            pill.setBackgroundResource(R.drawable.bg_lang_pill_selected);
            pill.setTextColor(ContextCompat.getColor(this, R.color.white));
        } else {
            pill.setBackground(null);
            pill.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        }
    }

    private void updateConversationSwitchLabel(boolean enabled) {
        conversationModeSwitch.setText(enabled ? R.string.conv_mode_on : R.string.conv_mode_off);
    }

    private void updateMuteButtonUi() {
        if (voiceManager.isMuted()) {
            muteToggleButton.setImageResource(R.drawable.ic_volume_off);
            muteToggleButton.setColorFilter(ContextCompat.getColor(this, R.color.text_muted));
        } else {
            muteToggleButton.setImageResource(R.drawable.ic_volume_up);
            muteToggleButton.setColorFilter(ContextCompat.getColor(this, R.color.cyan_glow));
        }
    }

    private void updateMicToggleUi() {
        boolean enabled = prefsManager.isMicEnabled();
        if (enabled) {
            micEnabledToggleButton.setImageResource(R.drawable.ic_mic);
            micEnabledToggleButton.setColorFilter(ContextCompat.getColor(this, R.color.cyan_glow));
            startListeningButton.setAlpha(1.0f);
        } else {
            micEnabledToggleButton.setImageResource(R.drawable.ic_mic_off);
            micEnabledToggleButton.setColorFilter(ContextCompat.getColor(this, R.color.coral_stop));
            startListeningButton.setAlpha(0.55f);
        }
    }

    private String getReadyStatusText() {
        if (!prefsManager.isMicEnabled()) {
            return getString(R.string.status_mic_off);
        }
        return getString(R.string.status_ready);
    }

    private void submitTypedMessage() {
        String text = messageInput.getText().toString().trim();
        if (text.isEmpty()) return;
        messageInput.setText("");
        if (voiceManager.isSpeaking()) {
            voiceManager.stopSpeaking(false);
        }
        handleUserInput(text, false);
    }

    private void requestMicAndStartListening(boolean sleepStandby) {
        if (!prefsManager.isMicEnabled()) {
            Toast.makeText(this, R.string.mic_disabled_msg, Toast.LENGTH_SHORT).show();
            updateUiState(JarvisOrbView.AssistantState.IDLE, getString(R.string.status_mic_off));
            return;
        }
        if (!voiceManager.isRecognitionAvailable()) {
            Snackbar.make(rootContainer, R.string.speech_not_available, Snackbar.LENGTH_LONG).show();
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            beginVoiceListening(sleepStandby);
        } else {
            pendingSleepStandby = sleepStandby;
            if (!prefsManager.hasSeenMicRationale()) {
                new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.mic_usage_title)
                        .setMessage(R.string.mic_usage_message)
                        .setNegativeButton(R.string.cancel, null)
                        .setPositiveButton(R.string.mic_usage_continue, (d, w) -> {
                            prefsManager.setSeenMicRationale(true);
                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
                        })
                        .show();
            } else {
                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
            }
        }
    }

    private void beginVoiceListening(boolean sleepStandby) {
        voiceManager.startListening(selectedLangPref, sleepStandby);
    }

    private void showPermissionDeniedFeedback() {
        boolean showRationale = shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO);
        Snackbar snackbar = Snackbar.make(rootContainer, R.string.mic_permission_needed, Snackbar.LENGTH_LONG);
        if (!showRationale) {
            snackbar.setAction(R.string.mic_permission_settings, v -> {
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", getPackageName(), null));
                startActivity(intent);
            });
        }
        snackbar.show();
    }

    private void handleUserInput(String rawInput, boolean viaVoice) {
        String cleaned = rawInput == null ? "" : rawInput.trim();
        if (cleaned.isEmpty()) return;

        if (LanguageUtils.isStopSpeechCommand(cleaned)) {
            voiceManager.stopSpeaking(false);
            voiceManager.stopListening();
            updateUiState(JarvisOrbView.AssistantState.IDLE, getReadyStatusText());
            return;
        }

        // Handle pending Memory or Sensitive Action confirmation by voice/text
        if (memoryConsentCard.getVisibility() == View.VISIBLE) {
            String lower = cleaned.toLowerCase(Locale.ROOT);
            if (lower.equals("yes") || lower.equals("allow") || lower.equals("confirm")
                    || lower.equals("ok") || lower.equals("haan") || lower.equals("हाँ")) {
                appendAndPersistMessage(cleaned, true, LanguageUtils.detectLanguage(cleaned), false, "");
                grantPendingConsent();
                return;
            } else if (lower.equals("no") || lower.equals("deny") || lower.equals("cancel")
                    || lower.equals("nahi") || lower.equals("नहीं")) {
                appendAndPersistMessage(cleaned, true, LanguageUtils.detectLanguage(cleaned), false, "");
                denyPendingConsent();
                return;
            }
        }

        if (LanguageUtils.isSleepCommand(cleaned)) {
            String lang = resolveEffectiveLanguage(cleaned);
            appendAndPersistMessage(cleaned, true, lang, false, "");
            enterSleepMode(lang);
            return;
        }

        if (LanguageUtils.containsWakeWord(cleaned)) {
            String stripped = LanguageUtils.stripWakeWord(cleaned);
            if (stripped.isEmpty()) {
                String lang = resolveEffectiveLanguage(cleaned);
                appendAndPersistMessage(cleaned, true, lang, false, "");
                wakeUpFromSleep("");
                return;
            }
            cleaned = stripped;
        }

        String detectedLang = resolveEffectiveLanguage(cleaned);
        appendAndPersistMessage(cleaned, true, detectedLang, false, "");
        final boolean shouldAutoResume = viaVoice && conversationModeSwitch.isChecked()
                && prefsManager.isMicEnabled() && !isSleepMode;

        // 1. Notes / Reminders recall
        if (checkAndHandleSavedDataRecall(cleaned, detectedLang, shouldAutoResume)) {
            return;
        }

        // 2. Personal Memory intent (Preferred Name / Interests)
        if (checkAndHandleMemoryIntent(cleaned, detectedLang, shouldAutoResume)) {
            return;
        }

        // 3. System / App / Sensitive Voice Commands
        CommandProcessor.CommandResult cmdResult =
                CommandProcessor.tryExecute(this, cleaned, prefsManager.getPreferredName());
        if (cmdResult.handled) {
            if (cmdResult.requiresConfirmation) {
                pendingSensitiveAction = cmdResult.sensitiveAction;
                pendingActionPayload = cmdResult.actionPayload;
                pendingConsentLang = cmdResult.languageCode;
                memoryConsentPromptText.setText(cmdResult.responseText);
                memoryAllowButton.setText(R.string.btn_confirm);
                memoryDenyButton.setText(R.string.btn_cancel);
                memoryConsentCard.setVisibility(View.VISIBLE);
                appendAndPersistMessage(cmdResult.responseText, false, cmdResult.languageCode, false, "CONFIRM");
                voiceManager.speak(cmdResult.responseText, cmdResult.languageCode, shouldAutoResume);
            } else {
                appendAndPersistMessage(cmdResult.responseText, false, cmdResult.languageCode, false, "COMMAND");
                voiceManager.speak(cmdResult.responseText, cmdResult.languageCode, shouldAutoResume);
            }
            return;
        }

        // 4. Math & Unit Conversions
        MathAndUnitEvaluator.EvalResult mathResult = MathAndUnitEvaluator.tryEvaluate(cleaned);
        if (mathResult.handled) {
            appendAndPersistMessage(mathResult.formattedResponse, false, mathResult.languageCode, false, "MATH");
            voiceManager.speak(mathResult.formattedResponse, mathResult.languageCode, shouldAutoResume);
            return;
        }

        // 5. Real-time Weather & News over HTTPS
        if (liveInfoService.canHandle(cleaned)) {
            isThinking = true;
            updateUiState(JarvisOrbView.AssistantState.THINKING, getString(R.string.status_thinking));
            liveInfoService.handleQuery(cleaned, new LiveInfoService.Callback() {
                @Override
                public void onResult(String text, String languageCode, String modeTag) {
                    isThinking = false;
                    appendAndPersistMessage(text, false, languageCode, false, modeTag);
                    voiceManager.speak(text, languageCode, shouldAutoResume);
                }

                @Override
                public void onError(String errorMessage, String languageCode) {
                    isThinking = false;
                    appendAndPersistMessage(errorMessage, false, languageCode, false, "LIVE INFO");
                    updateUiState(JarvisOrbView.AssistantState.IDLE, getReadyStatusText());
                    voiceManager.speak(errorMessage, languageCode, shouldAutoResume);
                }
            });
            return;
        }

        // 6. AiRepository (PlaceholderAiRepository clearly labeled while Firebase is disabled)
        isThinking = true;
        updateUiState(JarvisOrbView.AssistantState.THINKING, getString(R.string.status_thinking));

        aiRepository.generateResponse(
                cleaned,
                detectedLang,
                chatAdapter.getMessagesSnapshot(),
                new AiRepository.AiCallback() {
                    @Override
                    public void onSuccess(String responseText, String responseLang, boolean isSampleResponse) {
                        isThinking = false;
                        updateTopicBadge();
                        String tag = isSampleResponse ? "SAMPLE AI" : "GEMINI";
                        appendAndPersistMessage(responseText, false, responseLang, isSampleResponse, tag);
                        voiceManager.speak(responseText, responseLang, shouldAutoResume);
                    }

                    @Override
                    public void onError(String errorMessage, String errorLang) {
                        isThinking = false;
                        appendAndPersistMessage(errorMessage, false, errorLang, false, "NETWORK");
                        updateUiState(JarvisOrbView.AssistantState.IDLE, errorMessage);
                        voiceManager.speak(errorMessage, errorLang, false);
                    }
                }
        );
    }

    private boolean checkAndHandleSavedDataRecall(String text, String lang, boolean autoResume) {
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("show my notes") || lower.contains("read my notes")
                || lower.contains("saved notes") || lower.contains("मेरे नोट")) {
            if (!prefsManager.isMemoryEnabled()) {
                String offMsg = "Personal Memory is currently OFF in Settings.";
                appendAndPersistMessage(offMsg, false, lang, false, "MEMORY");
                voiceManager.speak(offMsg, lang, autoResume);
                return true;
            }
            List<SavedNote> notes = dbHelper.getAllNotes();
            String reply;
            if (notes.isEmpty()) {
                reply = LanguageUtils.LANG_HINDI.equals(lang)
                        ? "अभी कोई सेव किया हुआ नोट नहीं है।"
                        : "You do not have any saved notes yet.";
            } else {
                StringBuilder sb = new StringBuilder("Here are your latest notes:\n");
                int limit = Math.min(notes.size(), 5);
                for (int i = 0; i < limit; i++) {
                    sb.append(i + 1).append(". ").append(notes.get(i).getContent());
                    if (i < limit - 1) sb.append("\n");
                }
                reply = sb.toString();
            }
            appendAndPersistMessage(reply, false, lang, false, "NOTES");
            voiceManager.speak(reply, lang, autoResume);
            return true;
        }

        if (lower.contains("show my reminders") || lower.contains("my tasks")
                || lower.contains("read my reminders") || lower.contains("मेरे रिमाइंडर")) {
            List<ReminderItem> items = dbHelper.getAllReminders();
            String reply;
            if (items.isEmpty()) {
                reply = LanguageUtils.LANG_HINDI.equals(lang)
                        ? "अभी कोई रिमाइंडर सेव नहीं है।"
                        : "You have no saved reminders or tasks yet.";
            } else {
                StringBuilder sb = new StringBuilder("Your upcoming tasks & reminders:\n");
                int limit = Math.min(items.size(), 5);
                for (int i = 0; i < limit; i++) {
                    sb.append(i + 1).append(". ").append(items.get(i).getTitle());
                    if (i < limit - 1) sb.append("\n");
                }
                reply = sb.toString();
            }
            appendAndPersistMessage(reply, false, lang, false, "REMINDER");
            voiceManager.speak(reply, lang, autoResume);
            return true;
        }
        return false;
    }

    private String resolveEffectiveLanguage(String text) {
        if ("hi".equals(selectedLangPref)) {
            return LanguageUtils.LANG_HINDI;
        }
        String detected = LanguageUtils.detectLanguage(text);
        if ("en".equals(selectedLangPref) && !LanguageUtils.LANG_HINDI.equals(detected)) {
            return LanguageUtils.LANG_ENGLISH;
        }
        return detected;
    }

    private void enterSleepMode(String lang) {
        isSleepMode = true;
        String sleepReply;
        if (LanguageUtils.LANG_HINDI.equals(lang)) {
            sleepReply = "शुभ रात्रि! मैं स्लीप मोड में जा रही हूँ। जब भी ज़रूरत हो, बस \"Hey SUBH\" बोलें।";
        } else if (LanguageUtils.LANG_HINGLISH.equals(lang)) {
            sleepReply = "Good night! Main sleep mode mein ja rahi hoon. Jab zaroorat ho, bas \"Hey SUBH\" boliye.";
        } else {
            sleepReply = "Good night. Entering standby sleep mode. Say \"Hey SUBH\" whenever you need me.";
        }
        appendAndPersistMessage(sleepReply, false, lang, false, "SLEEP");
        updateUiState(JarvisOrbView.AssistantState.SLEEPING, getString(R.string.status_sleeping));
        boolean hasMic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
        voiceManager.speak(sleepReply, lang, hasMic && prefsManager.isMicEnabled());
    }

    private void wakeUpFromSleep(String trailingCommand) {
        isSleepMode = false;
        String name = prefsManager.getPreferredName();
        String callName = name.isEmpty() ? "" : (" " + name);

        if (trailingCommand != null && !trailingCommand.trim().isEmpty()) {
            updateUiState(JarvisOrbView.AssistantState.IDLE, getReadyStatusText());
            handleUserInput(trailingCommand, true);
            return;
        }

        String wakeReply = "hi".equals(selectedLangPref)
                ? "जी" + callName + ", मैं जाग गई हूँ और सुन रही हूँ। बताइए?"
                : "Yes" + callName + ", I'm awake and listening. How can I help?";
        String lang = "hi".equals(selectedLangPref) ? LanguageUtils.LANG_HINDI : LanguageUtils.LANG_ENGLISH;

        appendAndPersistMessage(wakeReply, false, lang, false, "AWAKE");
        boolean hasMic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
        voiceManager.speak(wakeReply, lang, hasMic && conversationModeSwitch.isChecked() && prefsManager.isMicEnabled());
    }

    private boolean checkAndHandleMemoryIntent(String text, String lang, boolean autoResume) {
        String extractedName = null;
        Matcher enMatcher = NAME_PATTERN_EN.matcher(text);
        if (enMatcher.find() && enMatcher.group(1) != null) {
            extractedName = enMatcher.group(1).trim();
        } else {
            Matcher hiMatcher = NAME_PATTERN_HI.matcher(text);
            if (hiMatcher.find() && hiMatcher.group(1) != null) {
                extractedName = hiMatcher.group(1).trim();
            }
        }

        String extractedInterest = null;
        Matcher interestMatcher = INTEREST_PATTERN.matcher(text);
        if (interestMatcher.find() && interestMatcher.group(1) != null) {
            extractedInterest = interestMatcher.group(1).trim();
        }

        if (extractedName == null && extractedInterest == null) {
            return false;
        }

        if (!prefsManager.isMemoryEnabled()) {
            String reply = LanguageUtils.LANG_HINDI.equals(lang)
                    ? "सेटिंग्स में पर्सनल मेमोरी बंद है, इसलिए मैंने इसे सेव नहीं किया।"
                    : "Personal Memory is currently turned OFF in Settings, so I didn't save that.";
            appendAndPersistMessage(reply, false, lang, false, "MEMORY");
            voiceManager.speak(reply, lang, autoResume);
            return true;
        }

        if (prefsManager.hasMemoryPermission()) {
            if (extractedName != null) {
                prefsManager.savePreferredName(extractedName);
            }
            if (extractedInterest != null) {
                prefsManager.addInterest(extractedInterest);
            }
            String reply = buildMemorySavedReply(lang, prefsManager.getPreferredName(), extractedInterest);
            appendAndPersistMessage(reply, false, lang, false, "MEMORY");
            voiceManager.speak(reply, lang, autoResume);
            return true;
        }

        pendingSensitiveAction = CommandProcessor.SensitiveActionType.NONE;
        pendingActionPayload = "";
        pendingMemoryName = extractedName == null ? "" : extractedName;
        pendingMemoryInterest = extractedInterest == null ? "" : extractedInterest;
        pendingConsentLang = lang;

        String itemDesc = !pendingMemoryName.isEmpty()
                ? ("your name \"" + pendingMemoryName + "\"")
                : ("your preference \"" + pendingMemoryInterest + "\"");
        String prompt = LanguageUtils.LANG_HINDI.equals(lang)
                ? "क्या मैं आपकी अनुमति से " + (!pendingMemoryName.isEmpty() ? pendingMemoryName : pendingMemoryInterest) + " को याद रखूँ?"
                : "May I remember " + itemDesc + " on this device for future conversations?";

        memoryAllowButton.setText(R.string.btn_allow);
        memoryDenyButton.setText(R.string.btn_deny);
        memoryConsentPromptText.setText(prompt);
        memoryConsentCard.setVisibility(View.VISIBLE);
        appendAndPersistMessage(prompt, false, lang, false, "PERMISSION");
        voiceManager.speak(prompt, lang, autoResume);
        return true;
    }

    private void grantPendingConsent() {
        memoryConsentCard.setVisibility(View.GONE);
        boolean resumeVoice = conversationModeSwitch.isChecked() && prefsManager.isMicEnabled();

        if (pendingSensitiveAction != CommandProcessor.SensitiveActionType.NONE) {
            CommandProcessor.SensitiveActionType action = pendingSensitiveAction;
            String payload = pendingActionPayload;
            pendingSensitiveAction = CommandProcessor.SensitiveActionType.NONE;
            pendingActionPayload = "";

            String reply;
            switch (action) {
                case OPEN_CONTACTS:
                    boolean openedContacts = CommandProcessor.executeConfirmedContactsOpen(this);
                    reply = openedContacts
                            ? "Opening your Contacts now."
                            : "Contacts app could not be opened on this device.";
                    break;
                case OPEN_CALENDAR:
                    boolean openedCal = CommandProcessor.executeConfirmedCalendarOpen(this, payload);
                    reply = openedCal
                            ? "Opening your Calendar now."
                            : "Calendar app could not be opened on this device.";
                    break;
                case CREATE_REMINDER:
                    dbHelper.insertReminder(payload, System.currentTimeMillis());
                    reply = LanguageUtils.LANG_HINDI.equals(pendingConsentLang)
                            ? "रिमाइंडर जोड़ दिया गया है: \"" + payload + "\"।"
                            : "Reminder saved: \"" + payload + "\". You can view or export it to Calendar in Profile.";
                    break;
                case SAVE_NOTE:
                    if (!prefsManager.isMemoryEnabled()) {
                        reply = "Personal Memory is currently OFF in Settings, so the note was not saved.";
                    } else {
                        dbHelper.insertNote(payload, System.currentTimeMillis());
                        reply = LanguageUtils.LANG_HINDI.equals(pendingConsentLang)
                                ? "आपका नोट सेव कर लिया गया है: \"" + payload + "\"।"
                                : "Note saved securely on this device: \"" + payload + "\".";
                    }
                    break;
                default:
                    reply = "Action confirmed.";
                    break;
            }
            appendAndPersistMessage(reply, false, pendingConsentLang, false, "COMMAND");
            voiceManager.speak(reply, pendingConsentLang, resumeVoice);
            return;
        }

        prefsManager.setMemoryPermission(true);
        if (!pendingMemoryName.isEmpty()) {
            prefsManager.savePreferredName(pendingMemoryName);
        }
        if (!pendingMemoryInterest.isEmpty()) {
            prefsManager.addInterest(pendingMemoryInterest);
        }
        String reply = buildMemorySavedReply(pendingConsentLang, prefsManager.getPreferredName(), pendingMemoryInterest);
        pendingMemoryName = "";
        pendingMemoryInterest = "";
        appendAndPersistMessage(reply, false, pendingConsentLang, false, "MEMORY");
        voiceManager.speak(reply, pendingConsentLang, resumeVoice);
    }

    private void denyPendingConsent() {
        memoryConsentCard.setVisibility(View.GONE);
        pendingMemoryName = "";
        pendingMemoryInterest = "";
        pendingSensitiveAction = CommandProcessor.SensitiveActionType.NONE;
        pendingActionPayload = "";

        String reply = LanguageUtils.LANG_HINDI.equals(pendingConsentLang)
                ? "ठीक है, मैंने इसे रद्द कर दिया है।"
                : "Understood. Action cancelled.";
        appendAndPersistMessage(reply, false, pendingConsentLang, false, "CONFIRM");
        voiceManager.speak(reply, pendingConsentLang, conversationModeSwitch.isChecked() && prefsManager.isMicEnabled());
    }

    private String buildMemorySavedReply(String lang, String name, String interest) {
        if (LanguageUtils.LANG_HINDI.equals(lang)) {
            if (!name.isEmpty()) {
                return "धन्यवाद " + name + "! अब से मैं आपको इसी नाम से संबोधित करूँगी।";
            }
            return "बिल्कुल! मैंने आपकी पसंद \"" + interest + "\" याद रख ली है।";
        } else if (LanguageUtils.LANG_HINGLISH.equals(lang)) {
            if (!name.isEmpty()) {
                return "Done! Main yaad rakhungi ki aapko " + name + " bulana hai.";
            }
            return "Got it! Maine yaad rakh liya hai ki aapko \"" + interest + "\" pasand hai.";
        }
        if (!name.isEmpty()) {
            return "Noted, " + name + ". I will remember your preferred name on this device.";
        }
        return "Got it. I have saved your preference for \"" + interest + "\".";
    }

    private void appendAndPersistMessage(
            String text,
            boolean isUser,
            String languageCode,
            boolean isSampleAi,
            String modeTag
    ) {
        long now = System.currentTimeMillis();
        long id = dbHelper.insertMessage(text, isUser, languageCode, now, isSampleAi, modeTag);
        chatAdapter.addMessage(new ChatMessage(id, text, isUser, languageCode, now, isSampleAi, modeTag));
        scrollToBottom();
    }

    private void scrollToBottom() {
        int count = chatAdapter.getItemCount();
        if (count > 0) {
            chatRecyclerView.smoothScrollToPosition(count - 1);
        }
    }

    private void updateUiState(JarvisOrbView.AssistantState state, String statusText) {
        jarvisOrbView.setState(state);
        assistantStatusText.setText(statusText);

        boolean activeStop = (state == JarvisOrbView.AssistantState.LISTENING
                || state == JarvisOrbView.AssistantState.SPEAKING
                || state == JarvisOrbView.AssistantState.THINKING);
        stopListeningButton.setBackgroundResource(
                activeStop ? R.drawable.bg_stop_button : R.drawable.bg_stop_button_idle
        );

        if (state == JarvisOrbView.AssistantState.LISTENING && !prefsManager.isBatterySaverMode()) {
            if (!micPulseAnimator.isStarted()) {
                micPulseAnimator.start();
            }
        } else {
            if (micPulseAnimator.isStarted()) {
                micPulseAnimator.cancel();
            }
            micGlowRing.setScaleX(1f);
            micGlowRing.setScaleY(1f);
            micGlowRing.setAlpha(state == JarvisOrbView.AssistantState.SLEEPING ? 0.15f : 0.45f);
        }
    }

    @Override
    public void onListeningStarted(boolean isSleepStandby) {
        if (isSleepStandby) {
            updateUiState(JarvisOrbView.AssistantState.SLEEPING, getString(R.string.status_sleeping));
            liveTranscriptText.setText(R.string.status_sleeping);
        } else {
            updateUiState(JarvisOrbView.AssistantState.LISTENING, getString(R.string.status_listening));
            liveTranscriptText.setText(R.string.status_listening);
        }
    }

    @Override
    public void onPartialTranscript(String partialText) {
        if (partialText != null && !partialText.trim().isEmpty() && !isSleepMode) {
            liveTranscriptText.setText("“" + partialText.trim() + "”");
        }
    }

    @Override
    public void onRmsChanged(float rmsDb) {
        jarvisOrbView.updateAudioRms(rmsDb);
    }

    @Override
    public void onSpeechResult(String recognizedText, boolean fromSleepStandby) {
        if (fromSleepStandby || isSleepMode) {
            if (LanguageUtils.containsWakeWord(recognizedText)) {
                String trailing = LanguageUtils.stripWakeWord(recognizedText);
                wakeUpFromSleep(trailing);
            } else if (prefsManager.isMicEnabled() && ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED) {
                voiceManager.scheduleAutoRestart(selectedLangPref, true, 350L);
            }
            return;
        }

        liveTranscriptText.setText(recognizedText);
        handleUserInput(recognizedText, true);
    }

    @Override
    public void onSpeechError(String friendlyMessage, boolean fatal) {
        if (isSleepMode && !fatal) {
            updateUiState(JarvisOrbView.AssistantState.SLEEPING, getString(R.string.status_sleeping));
            return;
        }
        if (fatal) {
            updateUiState(JarvisOrbView.AssistantState.IDLE, friendlyMessage);
            liveTranscriptText.setText(friendlyMessage);
        } else if (conversationModeSwitch.isChecked() && prefsManager.isMicEnabled()
                && !isThinking && !voiceManager.isSpeaking()) {
            updateUiState(JarvisOrbView.AssistantState.IDLE, friendlyMessage);
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED) {
                voiceManager.scheduleAutoRestart(selectedLangPref, false, 700L);
            }
        } else {
            updateUiState(JarvisOrbView.AssistantState.IDLE, friendlyMessage);
        }
    }

    @Override
    public void onSpeakingStarted() {
        updateUiState(JarvisOrbView.AssistantState.SPEAKING, getString(R.string.status_speaking));
    }

    @Override
    public void onSpeakingFinished(boolean shouldResumeListening) {
        if (isSleepMode) {
            updateUiState(JarvisOrbView.AssistantState.SLEEPING, getString(R.string.status_sleeping));
            if (shouldResumeListening && prefsManager.isMicEnabled()
                    && ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED) {
                voiceManager.scheduleAutoRestart(selectedLangPref, true, 400L);
            }
        } else {
            updateUiState(JarvisOrbView.AssistantState.IDLE, getReadyStatusText());
            if (shouldResumeListening && conversationModeSwitch.isChecked()
                    && prefsManager.isMicEnabled()
                    && ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED) {
                voiceManager.scheduleAutoRestart(selectedLangPref, false, 350L);
            }
        }
    }

    @Override
    public void onTtsReady() {
        if (!greetedOnStartup) {
            greetedOnStartup = true;
            if (chatAdapter.getItemCount() == 1 && !voiceManager.isMuted()) {
                voiceManager.speak(getString(R.string.initial_greeting), LanguageUtils.LANG_HINDI, false);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        jarvisOrbView.setBatterySaverMode(prefsManager.isBatterySaverMode());
        boolean convOn = prefsManager.isConversationModeEnabled();
        conversationModeSwitch.setChecked(convOn);
        voiceManager.setContinuousMode(convOn);
        updateConversationSwitchLabel(convOn);

        boolean micOn = prefsManager.isMicEnabled();
        voiceManager.setMicEnabled(micOn);
        updateMicToggleUi();

        List<ChatMessage> dbMessages = dbHelper.getAllMessages();
        if (dbMessages.isEmpty()) {
            restoreStateAndHistory();
        } else if (dbMessages.size() != chatAdapter.getItemCount()) {
            chatAdapter.setMessages(dbMessages);
            conversationMemory.hydrateFromHistory(dbMessages);
            updateTopicBadge();
            scrollToBottom();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        voiceManager.setForegroundActive(true);
        if (isSleepMode && prefsManager.isMicEnabled()
                && ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            voiceManager.scheduleAutoRestart(selectedLangPref, true, 400L);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        voiceManager.setForegroundActive(false);
        if (!isSleepMode) {
            updateUiState(JarvisOrbView.AssistantState.IDLE, getString(R.string.status_paused));
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (micPulseAnimator != null) {
            micPulseAnimator.cancel();
        }
        voiceManager.destroy();
        liveInfoService.shutdown();
        aiRepository.shutdown();
        dbHelper.close();
    }
}
