package com.example.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.R;
import com.example.assistant.CommandProcessor;
import com.example.data.ChatHistoryDbHelper;
import com.example.data.UserPreferencesManager;
import com.example.model.ReminderItem;
import com.example.model.SavedNote;
import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ProfileActivity extends AppCompatActivity {

    private UserPreferencesManager prefsManager;
    private ChatHistoryDbHelper dbHelper;

    private EditText preferredNameInput;
    private TextView savedInterestsText;
    private EditText addNoteInput;
    private LinearLayout notesListContainer;
    private EditText addReminderInput;
    private LinearLayout remindersListContainer;

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        prefsManager = new UserPreferencesManager(this);
        prefsManager.applySavedTheme();
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        dbHelper = new ChatHistoryDbHelper(this);

        applyWindowInsets();
        bindViews();
        refreshAll();
    }

    private void applyWindowInsets() {
        View root = findViewById(R.id.profileRoot);
        View header = findViewById(R.id.profileHeader);
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
        ImageButton backBtn = findViewById(R.id.profileBackButton);
        backBtn.setOnClickListener(v -> finish());

        preferredNameInput = findViewById(R.id.preferredNameInput);
        MaterialButton saveNameButton = findViewById(R.id.saveNameButton);
        savedInterestsText = findViewById(R.id.savedInterestsText);
        addNoteInput = findViewById(R.id.addNoteInput);
        MaterialButton addNoteButton = findViewById(R.id.addNoteButton);
        notesListContainer = findViewById(R.id.notesListContainer);
        addReminderInput = findViewById(R.id.addReminderInput);
        MaterialButton addReminderButton = findViewById(R.id.addReminderButton);
        remindersListContainer = findViewById(R.id.remindersListContainer);

        saveNameButton.setOnClickListener(v -> {
            String name = preferredNameInput.getText().toString().trim();
            if (name.isEmpty()) return;
            prefsManager.setMemoryEnabled(true);
            prefsManager.savePreferredName(name);
            Toast.makeText(this, "Saved preferred name: " + name, Toast.LENGTH_SHORT).show();
            refreshProfileInfo();
        });

        addNoteButton.setOnClickListener(v -> {
            String content = addNoteInput.getText().toString().trim();
            if (content.isEmpty()) return;
            if (!prefsManager.isMemoryEnabled()) {
                Toast.makeText(this, "Personal Memory is OFF in Settings.", Toast.LENGTH_SHORT).show();
                return;
            }
            dbHelper.insertNote(content, System.currentTimeMillis());
            addNoteInput.setText("");
            refreshNotes();
        });

        addReminderButton.setOnClickListener(v -> {
            String title = addReminderInput.getText().toString().trim();
            if (title.isEmpty()) return;
            dbHelper.insertReminder(title, System.currentTimeMillis());
            addReminderInput.setText("");
            refreshReminders();
        });
    }

    private void refreshAll() {
        refreshProfileInfo();
        refreshNotes();
        refreshReminders();
    }

    private void refreshProfileInfo() {
        preferredNameInput.setText(prefsManager.getPreferredName());
        List<String> interests = prefsManager.getInterests();
        if (interests.isEmpty()) {
            savedInterestsText.setText(prefsManager.isMemoryEnabled()
                    ? "None saved yet. Tell SUBH AI what you like!"
                    : "Personal Memory is currently OFF in Settings.");
        } else {
            savedInterestsText.setText(String.join(", ", interests));
        }
    }

    private void refreshNotes() {
        notesListContainer.removeAllViews();
        List<SavedNote> notes = dbHelper.getAllNotes();
        if (notes.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No saved notes yet.");
            empty.setTextSize(12f);
            notesListContainer.addView(empty);
            return;
        }
        LayoutInflater inflater = LayoutInflater.from(this);
        for (SavedNote note : notes) {
            View row = inflater.inflate(R.layout.item_simple_entry, notesListContainer, false);
            TextView title = row.findViewById(R.id.entryTitleText);
            TextView sub = row.findViewById(R.id.entrySubText);
            MaterialButton deleteBtn = row.findViewById(R.id.entryDeleteButton);

            title.setText(note.getContent());
            sub.setText(dateFormat.format(new Date(note.getCreatedAt())));
            deleteBtn.setOnClickListener(v -> {
                dbHelper.deleteNote(note.getId());
                refreshNotes();
            });
            notesListContainer.addView(row);
        }
    }

    private void refreshReminders() {
        remindersListContainer.removeAllViews();
        List<ReminderItem> reminders = dbHelper.getAllReminders();
        if (reminders.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No tasks or reminders added yet.");
            empty.setTextSize(12f);
            remindersListContainer.addView(empty);
            return;
        }
        LayoutInflater inflater = LayoutInflater.from(this);
        for (ReminderItem item : reminders) {
            View row = inflater.inflate(R.layout.item_simple_entry, remindersListContainer, false);
            TextView title = row.findViewById(R.id.entryTitleText);
            TextView sub = row.findViewById(R.id.entrySubText);
            MaterialButton calendarBtn = row.findViewById(R.id.entrySecondaryButton);
            MaterialButton deleteBtn = row.findViewById(R.id.entryDeleteButton);

            title.setText(item.getTitle());
            sub.setText(dateFormat.format(new Date(item.getCreatedAt())));
            calendarBtn.setVisibility(View.VISIBLE);
            calendarBtn.setOnClickListener(v -> {
                if (!CommandProcessor.executeConfirmedCalendarOpen(this, item.getTitle())) {
                    Toast.makeText(this, "No Calendar app found on this device.", Toast.LENGTH_SHORT).show();
                }
            });
            deleteBtn.setOnClickListener(v -> {
                dbHelper.deleteReminder(item.getId());
                refreshReminders();
            });
            remindersListContainer.addView(row);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        dbHelper.close();
    }
}
