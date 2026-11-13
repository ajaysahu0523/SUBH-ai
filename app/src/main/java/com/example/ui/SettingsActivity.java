package com.example.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.R;
import com.example.assistant.LanguageUtils;
import com.example.assistant.VoiceAssistantManager;
import com.example.data.ChatHistoryDbHelper;
import com.example.data.UserPreferencesManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;

import java.util.List;
import java.util.Locale;

public class SettingsActivity extends AppCompatActivity implements VoiceAssistantManager.Listener {

    public static final String EXTRA_SECTION = "extra_section";
    public static final String SECTION_VOICE = "voice";
    public static final String SECTION_AI = "ai";
    public static final String SECTION_MEMORY = "memory";

    private UserPreferencesManager prefsManager;
    private ChatHistoryDbHelper dbHelper;
    private VoiceAssistantManager voiceManager;

    private ScrollView settingsScrollView;
    private View sectionVoiceAnchor;
    private View sectionAiAnchor;
    private View sectionMemoryAnchor;

    private MaterialSwitch autoVoiceReplySwitch;
    private Spinner hindiVoiceSpinner;
    private Spinner englishVoiceSpinner;
    private TextView speedValueLabel;
    private Slider speechSpeedSlider;
    private TextView pitchValueLabel;
    private Slider speechPitchSlider;
    private TextView volumeValueLabel;
    private Slider speechVolumeSlider;
    private TextView voiceDataStatusText;
    private MaterialButton testVoiceButton;
    private MaterialButton installVoiceDataButton;

    private MaterialSwitch settingConvModeSwitch;
    private MaterialSwitch settingMicEnabledSwitch;
    private RadioGroup themeRadioGroup;
    private MaterialSwitch batterySaverSwitch;
    private MaterialSwitch memoryEnabledSwitch;

    private List<VoiceAssistantManager.VoiceOption> hindiVoiceOptions;
    private List<VoiceAssistantManager.VoiceOption> englishVoiceOptions;
    private boolean spinnersBound = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        prefsManager = new UserPreferencesManager(this);
        prefsManager.applySavedTheme();
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        dbHelper = new ChatHistoryDbHelper(this);
        voiceManager = new VoiceAssistantManager(this, this);

        applyWindowInsets();
        bindAndSetupViews();
        scrollToRequestedSection();
    }

    private void applyWindowInsets() {
        View root = findViewById(R.id.settingsRoot);
        View header = findViewById(R.id.settingsHeader);
        final int headerTop = header.getPaddingTop();
        final int rootBottom = root.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            header.setPadding(header.getPaddingLeft(), headerTop + bars.top, header.getPaddingRight(), header.getPaddingBottom());
            root.setPadding(root.getPaddingLeft(), root.getPaddingTop(), root.getPaddingRight(), rootBottom + bars.bottom);
            return insets;
        });
    }

    private void bindAndSetupViews() {
        ImageButton backBtn = findViewById(R.id.settingsBackButton);
        backBtn.setOnClickListener(v -> finish());

        settingsScrollView = findViewById(R.id.settingsScrollView);
        sectionVoiceAnchor = findViewById(R.id.sectionVoiceAnchor);
        sectionAiAnchor = findViewById(R.id.sectionAiAnchor);
        sectionMemoryAnchor = findViewById(R.id.sectionMemoryAnchor);

        autoVoiceReplySwitch = findViewById(R.id.autoVoiceReplySwitch);
        hindiVoiceSpinner = findViewById(R.id.hindiVoiceSpinner);
        englishVoiceSpinner = findViewById(R.id.englishVoiceSpinner);
        speedValueLabel = findViewById(R.id.speedValueLabel);
        speechSpeedSlider = findViewById(R.id.speechSpeedSlider);
        pitchValueLabel = findViewById(R.id.pitchValueLabel);
        speechPitchSlider = findViewById(R.id.speechPitchSlider);
        volumeValueLabel = findViewById(R.id.volumeValueLabel);
        speechVolumeSlider = findViewById(R.id.speechVolumeSlider);
        voiceDataStatusText = findViewById(R.id.voiceDataStatusText);
        testVoiceButton = findViewById(R.id.testVoiceButton);
        installVoiceDataButton = findViewById(R.id.installVoiceDataButton);

        settingConvModeSwitch = findViewById(R.id.settingConvModeSwitch);
        settingMicEnabledSwitch = findViewById(R.id.settingMicEnabledSwitch);
        themeRadioGroup = findViewById(R.id.themeRadioGroup);
        batterySaverSwitch = findViewById(R.id.batterySaverSwitch);
        memoryEnabledSwitch = findViewById(R.id.memoryEnabledSwitch);

        // Voice settings
        autoVoiceReplySwitch.setChecked(prefsManager.isAutoVoiceReplyEnabled());
        autoVoiceReplySwitch.setOnCheckedChangeListener((b, checked) -> prefsManager.setAutoVoiceReplyEnabled(checked));

        float savedSpeed = prefsManager.getSpeechRate();
        speechSpeedSlider.setValue(clamp(savedSpeed, 0.6f, 1.6f));
        updateSpeedLabel(savedSpeed);
        speechSpeedSlider.addOnChangeListener((slider, value, fromUser) -> {
            prefsManager.setSpeechRate(value);
            updateSpeedLabel(value);
        });

        float savedPitch = prefsManager.getSpeechPitch();
        speechPitchSlider.setValue(clamp(savedPitch, 0.75f, 1.35f));
        updatePitchLabel(savedPitch);
        speechPitchSlider.addOnChangeListener((slider, value, fromUser) -> {
            prefsManager.setSpeechPitch(value);
            updatePitchLabel(value);
        });

        float savedVol = prefsManager.getSpeechVolume();
        speechVolumeSlider.setValue(clamp(savedVol, 0.2f, 1.0f));
        updateVolumeLabel(savedVol);
        speechVolumeSlider.addOnChangeListener((slider, value, fromUser) -> {
            prefsManager.setSpeechVolume(value);
            updateVolumeLabel(value);
        });

        testVoiceButton.setOnClickListener(v -> {
            voiceManager.setMuted(false);
            voiceManager.speak(getString(R.string.test_voice_sample), LanguageUtils.LANG_HINDI, false, true);
        });

        installVoiceDataButton.setOnClickListener(v -> {
            if (!VoiceAssistantManager.openInstallTtsData(this)) {
                Toast.makeText(this, R.string.voice_installer_unavailable, Toast.LENGTH_LONG).show();
            }
        });

        // AI Settings & Appearance
        settingConvModeSwitch.setChecked(prefsManager.isConversationModeEnabled());
        settingConvModeSwitch.setOnCheckedChangeListener((b, checked) -> prefsManager.setConversationModeEnabled(checked));

        settingMicEnabledSwitch.setChecked(prefsManager.isMicEnabled());
        settingMicEnabledSwitch.setOnCheckedChangeListener((b, checked) -> prefsManager.setMicEnabled(checked));

        String mode = prefsManager.getThemeMode();
        if (UserPreferencesManager.THEME_LIGHT.equals(mode)) {
            themeRadioGroup.check(R.id.radioThemeLight);
        } else if (UserPreferencesManager.THEME_SYSTEM.equals(mode)) {
            themeRadioGroup.check(R.id.radioThemeSystem);
        } else {
            themeRadioGroup.check(R.id.radioThemeDark);
        }

        themeRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.radioThemeLight) {
                prefsManager.setThemeMode(UserPreferencesManager.THEME_LIGHT);
            } else if (checkedId == R.id.radioThemeSystem) {
                prefsManager.setThemeMode(UserPreferencesManager.THEME_SYSTEM);
            } else {
                prefsManager.setThemeMode(UserPreferencesManager.THEME_DARK);
            }
        });

        batterySaverSwitch.setChecked(prefsManager.isBatterySaverMode());
        batterySaverSwitch.setOnCheckedChangeListener((b, checked) -> prefsManager.setBatterySaverMode(checked));

        // Memory & Privacy
        memoryEnabledSwitch.setChecked(prefsManager.isMemoryEnabled());
        memoryEnabledSwitch.setOnCheckedChangeListener((b, checked) -> prefsManager.setMemoryEnabled(checked));

        MaterialButton clearHistoryBtn = findViewById(R.id.clearHistorySettingButton);
        clearHistoryBtn.setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.clear_chat_title)
                .setMessage(R.string.clear_chat_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.clear_confirm, (d, w) -> {
                    dbHelper.clearAllMessages();
                    Toast.makeText(this, "Conversation history cleared", Toast.LENGTH_SHORT).show();
                })
                .show());

        MaterialButton clearMemoryBtn = findViewById(R.id.clearMemorySettingButton);
        clearMemoryBtn.setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.btn_clear_all_memory)
                .setMessage("This will erase your saved preferred name, interests, notes, and reminders from this device.")
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.clear_confirm, (d, w) -> {
                    prefsManager.forgetUserMemory();
                    dbHelper.clearAllNotes();
                    dbHelper.clearAllReminders();
                    Toast.makeText(this, "Personal memory, notes, and reminders cleared", Toast.LENGTH_SHORT).show();
                })
                .show());
    }

    private void scrollToRequestedSection() {
        String section = getIntent().getStringExtra(EXTRA_SECTION);
        if (section == null || settingsScrollView == null) return;
        settingsScrollView.post(() -> {
            View target = null;
            if (SECTION_AI.equals(section)) {
                target = sectionAiAnchor;
            } else if (SECTION_MEMORY.equals(section)) {
                target = sectionMemoryAnchor;
            } else if (SECTION_VOICE.equals(section)) {
                target = sectionVoiceAnchor;
            }
            if (target != null) {
                settingsScrollView.smoothScrollTo(0, target.getTop());
            }
        });
    }

    private void populateVoiceSpinnersAndStatus() {
        boolean hiOk = voiceManager.isLanguageVoiceAvailable(new Locale("hi", "IN"));
        boolean enOk = voiceManager.isLanguageVoiceAvailable(Locale.US);
        voiceDataStatusText.setText(String.format(
                Locale.US,
                "Voice Data Status • Hindi (hi-IN): %s • English (en): %s",
                hiOk ? "Installed" : "Needs Download",
                enOk ? "Installed" : "Needs Download"
        ));

        spinnersBound = false;
        hindiVoiceOptions = voiceManager.getAvailableVoicesForLanguage("hi");
        englishVoiceOptions = voiceManager.getAvailableVoicesForLanguage("en");

        ArrayAdapter<VoiceAssistantManager.VoiceOption> hiAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, hindiVoiceOptions
        );
        hiAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        hindiVoiceSpinner.setAdapter(hiAdapter);
        hindiVoiceSpinner.setSelection(findSelectedIndex(hindiVoiceOptions, prefsManager.getSelectedHindiVoiceName()));

        ArrayAdapter<VoiceAssistantManager.VoiceOption> enAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, englishVoiceOptions
        );
        enAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        englishVoiceSpinner.setAdapter(enAdapter);
        englishVoiceSpinner.setSelection(findSelectedIndex(englishVoiceOptions, prefsManager.getSelectedEnglishVoiceName()));

        hindiVoiceSpinner.post(() -> spinnersBound = true);

        hindiVoiceSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                if (spinnersBound && hindiVoiceOptions != null && pos < hindiVoiceOptions.size()) {
                    prefsManager.setSelectedHindiVoiceName(hindiVoiceOptions.get(pos).rawName);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        englishVoiceSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                if (spinnersBound && englishVoiceOptions != null && pos < englishVoiceOptions.size()) {
                    prefsManager.setSelectedEnglishVoiceName(englishVoiceOptions.get(pos).rawName);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private int findSelectedIndex(List<VoiceAssistantManager.VoiceOption> list, String savedName) {
        if (savedName == null || savedName.isEmpty() || list == null) return 0;
        for (int i = 0; i < list.size(); i++) {
            if (savedName.equals(list.get(i).rawName)) {
                return i;
            }
        }
        return 0;
    }

    private void updateSpeedLabel(float speed) {
        speedValueLabel.setText(String.format(Locale.US, "%s: %.2fx", getString(R.string.tts_speed_label), speed));
    }

    private void updatePitchLabel(float pitch) {
        pitchValueLabel.setText(String.format(Locale.US, "%s: %.2fx", getString(R.string.tts_pitch_label), pitch));
    }

    private void updateVolumeLabel(float volume) {
        int percent = Math.round(volume * 100f);
        volumeValueLabel.setText(String.format(Locale.US, "%s: %d%%", getString(R.string.tts_volume_label), percent));
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    public void onTtsReady() {
        populateVoiceSpinnersAndStatus();
    }

    @Override public void onListeningStarted(boolean isSleepStandby) {}
    @Override public void onPartialTranscript(String partialText) {}
    @Override public void onRmsChanged(float rmsDb) {}
    @Override public void onSpeechResult(String recognizedText, boolean fromSleepStandby) {}
    @Override public void onSpeechError(String friendlyMessage, boolean fatal) {}
    @Override public void onSpeakingStarted() {}
    @Override public void onSpeakingFinished(boolean shouldResumeListening) {}

    @Override
    protected void onDestroy() {
        super.onDestroy();
        voiceManager.destroy();
        dbHelper.close();
    }
}
