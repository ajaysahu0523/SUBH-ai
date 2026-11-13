package com.example.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.R;
import com.example.assistant.VoiceAssistantManager;
import com.example.data.ChatHistoryDbHelper;
import com.example.data.UserPreferencesManager;
import com.example.model.ChatMessage;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class HistoryActivity extends AppCompatActivity implements VoiceAssistantManager.Listener {

    private static final int FILTER_ALL = 0;
    private static final int FILTER_COMMANDS = 1;
    private static final int FILTER_AI = 2;

    private ChatHistoryDbHelper dbHelper;
    private VoiceAssistantManager voiceManager;
    private ChatAdapter chatAdapter;

    private EditText historySearchInput;
    private TextView filterAllBtn;
    private TextView filterCommandsBtn;
    private TextView filterAiBtn;
    private TextView historyEmptyText;

    private List<ChatMessage> allMessages = new ArrayList<>();
    private int currentFilter = FILTER_ALL;
    private String currentQuery = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        new UserPreferencesManager(this).applySavedTheme();
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        dbHelper = new ChatHistoryDbHelper(this);
        voiceManager = new VoiceAssistantManager(this, this);

        applyWindowInsets();
        bindViews();
        loadHistory();
    }

    private void applyWindowInsets() {
        View root = findViewById(R.id.historyRoot);
        View header = findViewById(R.id.historyHeader);
        final int headerTop = header.getPaddingTop();
        final int rootBottom = root.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            header.setPadding(header.getPaddingLeft(), headerTop + bars.top, header.getPaddingRight(), header.getPaddingBottom());
            root.setPadding(root.getPaddingLeft(), root.getPaddingTop(), root.getPaddingRight(), rootBottom + bars.bottom);
            return insets;
        });
    }

    private void bindViews() {
        ImageButton backBtn = findViewById(R.id.historyBackButton);
        backBtn.setOnClickListener(v -> finish());

        ImageButton clearBtn = findViewById(R.id.historyClearButton);
        clearBtn.setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.clear_chat_title)
                .setMessage(R.string.clear_chat_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.clear_confirm, (d, w) -> {
                    dbHelper.clearAllMessages();
                    loadHistory();
                    Toast.makeText(this, "Conversation history cleared", Toast.LENGTH_SHORT).show();
                })
                .show());

        historySearchInput = findViewById(R.id.historySearchInput);
        filterAllBtn = findViewById(R.id.filterAllBtn);
        filterCommandsBtn = findViewById(R.id.filterCommandsBtn);
        filterAiBtn = findViewById(R.id.filterAiBtn);
        historyEmptyText = findViewById(R.id.historyEmptyText);

        RecyclerView recyclerView = findViewById(R.id.historyRecyclerView);
        chatAdapter = new ChatAdapter(new ChatAdapter.OnMessageActionListener() {
            @Override
            public void onSpeakMessage(ChatMessage message) {
                voiceManager.speak(message.getText(), message.getLanguageCode(), false, true);
            }

            @Override
            public void onCopyMessage(ChatMessage message) {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard != null) {
                    clipboard.setPrimaryClip(ClipData.newPlainText("SUBH AI", message.getText()));
                    Toast.makeText(HistoryActivity.this, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show();
                }
            }
        });
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(chatAdapter);

        historySearchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentQuery = s == null ? "" : s.toString().trim().toLowerCase(Locale.ROOT);
                applyFilters();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        filterAllBtn.setOnClickListener(v -> selectFilter(FILTER_ALL));
        filterCommandsBtn.setOnClickListener(v -> selectFilter(FILTER_COMMANDS));
        filterAiBtn.setOnClickListener(v -> selectFilter(FILTER_AI));
    }

    private void loadHistory() {
        allMessages = dbHelper.getAllMessages();
        applyFilters();
    }

    private void selectFilter(int filter) {
        currentFilter = filter;
        styleFilterButton(filterAllBtn, filter == FILTER_ALL);
        styleFilterButton(filterCommandsBtn, filter == FILTER_COMMANDS);
        styleFilterButton(filterAiBtn, filter == FILTER_AI);
        applyFilters();
    }

    private void styleFilterButton(TextView btn, boolean selected) {
        if (selected) {
            btn.setBackgroundResource(R.drawable.bg_lang_pill_selected);
            btn.setTextColor(ContextCompat.getColor(this, R.color.white));
        } else {
            btn.setBackgroundResource(R.drawable.bg_chip_pill);
            btn.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        }
    }

    private void applyFilters() {
        List<ChatMessage> filtered = new ArrayList<>();
        for (ChatMessage msg : allMessages) {
            if (currentFilter == FILTER_COMMANDS && !"COMMAND".equals(msg.getModeTag())) {
                continue;
            }
            if (currentFilter == FILTER_AI) {
                String tag = msg.getModeTag();
                if (!"SAMPLE AI".equals(tag) && !"GEMINI".equals(tag) && !"LIVE INFO".equals(tag) && !"MATH".equals(tag)) {
                    continue;
                }
            }
            if (!currentQuery.isEmpty() && (msg.getText() == null || !msg.getText().toLowerCase(Locale.ROOT).contains(currentQuery))) {
                continue;
            }
            filtered.add(msg);
        }
        chatAdapter.setMessages(filtered);
        historyEmptyText.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
    }

    @Override public void onListeningStarted(boolean isSleepStandby) {}
    @Override public void onPartialTranscript(String partialText) {}
    @Override public void onRmsChanged(float rmsDb) {}
    @Override public void onSpeechResult(String recognizedText, boolean fromSleepStandby) {}
    @Override public void onSpeechError(String friendlyMessage, boolean fatal) {}
    @Override public void onSpeakingStarted() {}
    @Override public void onSpeakingFinished(boolean shouldResumeListening) {}
    @Override public void onTtsReady() {}

    @Override
    protected void onDestroy() {
        super.onDestroy();
        voiceManager.destroy();
        dbHelper.close();
    }
}
