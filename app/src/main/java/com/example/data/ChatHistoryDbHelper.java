package com.example.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.example.model.ChatMessage;
import com.example.model.ReminderItem;
import com.example.model.SavedNote;

import java.util.ArrayList;
import java.util.List;

public class ChatHistoryDbHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "subh_ai_history.db";
    private static final int DATABASE_VERSION = 2;

    private static final String TABLE_MESSAGES = "chat_messages";
    private static final String COL_ID = "id";
    private static final String COL_TEXT = "message_text";
    private static final String COL_IS_USER = "is_user";
    private static final String COL_LANG = "language_code";
    private static final String COL_TIMESTAMP = "timestamp";
    private static final String COL_IS_SAMPLE_AI = "is_sample_ai";
    private static final String COL_MODE_TAG = "mode_tag";

    private static final String TABLE_NOTES = "saved_notes";
    private static final String COL_NOTE_CONTENT = "content";

    private static final String TABLE_REMINDERS = "reminders";
    private static final String COL_REMINDER_TITLE = "title";
    private static final String COL_REMINDER_COMPLETED = "completed";

    public ChatHistoryDbHelper(Context context) {
        super(context.getApplicationContext(), DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_MESSAGES + " ("
                + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_TEXT + " TEXT NOT NULL, "
                + COL_IS_USER + " INTEGER NOT NULL, "
                + COL_LANG + " TEXT NOT NULL, "
                + COL_TIMESTAMP + " INTEGER NOT NULL, "
                + COL_IS_SAMPLE_AI + " INTEGER NOT NULL DEFAULT 0, "
                + COL_MODE_TAG + " TEXT"
                + ")");
        createNotesAndRemindersTables(db);
    }

    private void createNotesAndRemindersTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_NOTES + " ("
                + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_NOTE_CONTENT + " TEXT NOT NULL, "
                + COL_TIMESTAMP + " INTEGER NOT NULL"
                + ")");
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_REMINDERS + " ("
                + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_REMINDER_TITLE + " TEXT NOT NULL, "
                + COL_TIMESTAMP + " INTEGER NOT NULL, "
                + COL_REMINDER_COMPLETED + " INTEGER NOT NULL DEFAULT 0"
                + ")");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            createNotesAndRemindersTables(db);
        }
    }

    public long insertMessage(
            String text,
            boolean isUser,
            String languageCode,
            long timestamp,
            boolean isSampleAi,
            String modeTag
    ) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_TEXT, text);
        values.put(COL_IS_USER, isUser ? 1 : 0);
        values.put(COL_LANG, languageCode == null ? "en" : languageCode);
        values.put(COL_TIMESTAMP, timestamp);
        values.put(COL_IS_SAMPLE_AI, isSampleAi ? 1 : 0);
        values.put(COL_MODE_TAG, modeTag == null ? "" : modeTag);
        return db.insert(TABLE_MESSAGES, null, values);
    }

    public List<ChatMessage> getAllMessages() {
        List<ChatMessage> messages = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor cursor = db.query(
                TABLE_MESSAGES,
                null,
                null,
                null,
                null,
                null,
                COL_TIMESTAMP + " ASC, " + COL_ID + " ASC"
        )) {
            int idxId = cursor.getColumnIndexOrThrow(COL_ID);
            int idxText = cursor.getColumnIndexOrThrow(COL_TEXT);
            int idxIsUser = cursor.getColumnIndexOrThrow(COL_IS_USER);
            int idxLang = cursor.getColumnIndexOrThrow(COL_LANG);
            int idxTs = cursor.getColumnIndexOrThrow(COL_TIMESTAMP);
            int idxSample = cursor.getColumnIndexOrThrow(COL_IS_SAMPLE_AI);
            int idxTag = cursor.getColumnIndexOrThrow(COL_MODE_TAG);

            while (cursor.moveToNext()) {
                messages.add(new ChatMessage(
                        cursor.getLong(idxId),
                        cursor.getString(idxText),
                        cursor.getInt(idxIsUser) == 1,
                        cursor.getString(idxLang),
                        cursor.getLong(idxTs),
                        cursor.getInt(idxSample) == 1,
                        cursor.getString(idxTag)
                ));
            }
        }
        return messages;
    }

    public void clearAllMessages() {
        getWritableDatabase().delete(TABLE_MESSAGES, null, null);
    }

    // Notes CRUD
    public long insertNote(String content, long timestamp) {
        ContentValues values = new ContentValues();
        values.put(COL_NOTE_CONTENT, content);
        values.put(COL_TIMESTAMP, timestamp);
        return getWritableDatabase().insert(TABLE_NOTES, null, values);
    }

    public List<SavedNote> getAllNotes() {
        List<SavedNote> notes = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query(
                TABLE_NOTES,
                null,
                null,
                null,
                null,
                null,
                COL_TIMESTAMP + " DESC"
        )) {
            int idxId = cursor.getColumnIndexOrThrow(COL_ID);
            int idxContent = cursor.getColumnIndexOrThrow(COL_NOTE_CONTENT);
            int idxTs = cursor.getColumnIndexOrThrow(COL_TIMESTAMP);
            while (cursor.moveToNext()) {
                notes.add(new SavedNote(
                        cursor.getLong(idxId),
                        cursor.getString(idxContent),
                        cursor.getLong(idxTs)
                ));
            }
        }
        return notes;
    }

    public void deleteNote(long id) {
        getWritableDatabase().delete(TABLE_NOTES, COL_ID + "=?", new String[]{String.valueOf(id)});
    }

    public void clearAllNotes() {
        getWritableDatabase().delete(TABLE_NOTES, null, null);
    }

    // Reminders CRUD
    public long insertReminder(String title, long timestamp) {
        ContentValues values = new ContentValues();
        values.put(COL_REMINDER_TITLE, title);
        values.put(COL_TIMESTAMP, timestamp);
        values.put(COL_REMINDER_COMPLETED, 0);
        return getWritableDatabase().insert(TABLE_REMINDERS, null, values);
    }

    public List<ReminderItem> getAllReminders() {
        List<ReminderItem> reminders = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query(
                TABLE_REMINDERS,
                null,
                null,
                null,
                null,
                null,
                COL_TIMESTAMP + " DESC"
        )) {
            int idxId = cursor.getColumnIndexOrThrow(COL_ID);
            int idxTitle = cursor.getColumnIndexOrThrow(COL_REMINDER_TITLE);
            int idxTs = cursor.getColumnIndexOrThrow(COL_TIMESTAMP);
            int idxDone = cursor.getColumnIndexOrThrow(COL_REMINDER_COMPLETED);
            while (cursor.moveToNext()) {
                reminders.add(new ReminderItem(
                        cursor.getLong(idxId),
                        cursor.getString(idxTitle),
                        cursor.getLong(idxTs),
                        cursor.getInt(idxDone) == 1
                ));
            }
        }
        return reminders;
    }

    public void deleteReminder(long id) {
        getWritableDatabase().delete(TABLE_REMINDERS, COL_ID + "=?", new String[]{String.valueOf(id)});
    }

    public void clearAllReminders() {
        getWritableDatabase().delete(TABLE_REMINDERS, null, null);
    }
}
