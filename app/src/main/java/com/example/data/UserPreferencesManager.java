package com.example.data;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class UserPreferencesManager {
    public static final String THEME_DARK = "dark";
    public static final String THEME_LIGHT = "light";
    public static final String THEME_SYSTEM = "system";

    private static final String PREFS_NAME = "subh_ai_user_prefs";
    private static final String KEY_MEMORY_PERMISSION = "memory_permission_granted";
    private static final String KEY_MEMORY_ENABLED = "memory_enabled";
    private static final String KEY_PREFERRED_NAME = "preferred_name";
    private static final String KEY_PREFERRED_LANGUAGE = "preferred_language";
    private static final String KEY_INTERESTS = "user_interests";
    private static final String KEY_CONVERSATION_MODE = "conversation_mode_on";
    private static final String KEY_TTS_MUTED = "tts_muted";
    private static final String KEY_MIC_ENABLED = "mic_enabled";
    private static final String KEY_MIC_RATIONALE_SHOWN = "mic_rationale_shown";
    private static final String KEY_SPEECH_RATE = "tts_speech_rate";
    private static final String KEY_SPEECH_PITCH = "tts_speech_pitch";
    private static final String KEY_SPEECH_VOLUME = "tts_speech_volume";
    private static final String KEY_AUTO_VOICE_REPLY = "tts_auto_voice_reply";
    private static final String KEY_HINDI_VOICE_NAME = "tts_hindi_voice_name";
    private static final String KEY_ENGLISH_VOICE_NAME = "tts_english_voice_name";
    private static final String KEY_THEME_MODE = "theme_mode";
    private static final String KEY_BATTERY_SAVER = "battery_saver_animations";

    private final SharedPreferences prefs;

    public UserPreferencesManager(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public boolean isMemoryEnabled() {
        return prefs.getBoolean(KEY_MEMORY_ENABLED, true);
    }

    public void setMemoryEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_MEMORY_ENABLED, enabled).apply();
    }

    public boolean hasMemoryPermission() {
        return isMemoryEnabled() && prefs.getBoolean(KEY_MEMORY_PERMISSION, false);
    }

    public void setMemoryPermission(boolean granted) {
        prefs.edit().putBoolean(KEY_MEMORY_PERMISSION, granted).apply();
    }

    public String getPreferredName() {
        if (!hasMemoryPermission()) {
            return "";
        }
        return prefs.getString(KEY_PREFERRED_NAME, "");
    }

    public void savePreferredName(String name) {
        if (!isMemoryEnabled() || name == null || name.trim().isEmpty()) {
            return;
        }
        prefs.edit()
                .putBoolean(KEY_MEMORY_PERMISSION, true)
                .putString(KEY_PREFERRED_NAME, name.trim())
                .apply();
    }

    public String getPreferredLanguage() {
        return prefs.getString(KEY_PREFERRED_LANGUAGE, "auto");
    }

    public void savePreferredLanguage(String langPref) {
        prefs.edit().putString(KEY_PREFERRED_LANGUAGE, langPref).apply();
    }

    public void addInterest(String interest) {
        if (!isMemoryEnabled() || interest == null || interest.trim().isEmpty()) {
            return;
        }
        Set<String> current = new HashSet<>(prefs.getStringSet(KEY_INTERESTS, new HashSet<>()));
        current.add(interest.trim());
        prefs.edit()
                .putBoolean(KEY_MEMORY_PERMISSION, true)
                .putStringSet(KEY_INTERESTS, current)
                .apply();
    }

    public List<String> getInterests() {
        if (!hasMemoryPermission()) {
            return new ArrayList<>();
        }
        Set<String> set = prefs.getStringSet(KEY_INTERESTS, new HashSet<>());
        return new ArrayList<>(set);
    }

    public boolean isConversationModeEnabled() {
        return prefs.getBoolean(KEY_CONVERSATION_MODE, true);
    }

    public void setConversationModeEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_CONVERSATION_MODE, enabled).apply();
    }

    public boolean isTtsMuted() {
        return prefs.getBoolean(KEY_TTS_MUTED, false);
    }

    public void setTtsMuted(boolean muted) {
        prefs.edit().putBoolean(KEY_TTS_MUTED, muted).apply();
    }

    public boolean isAutoVoiceReplyEnabled() {
        return prefs.getBoolean(KEY_AUTO_VOICE_REPLY, true);
    }

    public void setAutoVoiceReplyEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_AUTO_VOICE_REPLY, enabled).apply();
    }

    public boolean isMicEnabled() {
        return prefs.getBoolean(KEY_MIC_ENABLED, true);
    }

    public void setMicEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_MIC_ENABLED, enabled).apply();
    }

    public boolean hasSeenMicRationale() {
        return prefs.getBoolean(KEY_MIC_RATIONALE_SHOWN, false);
    }

    public void setSeenMicRationale(boolean seen) {
        prefs.edit().putBoolean(KEY_MIC_RATIONALE_SHOWN, seen).apply();
    }

    public float getSpeechRate() {
        return prefs.getFloat(KEY_SPEECH_RATE, 0.95f);
    }

    public void setSpeechRate(float rate) {
        prefs.edit().putFloat(KEY_SPEECH_RATE, Math.max(0.6f, Math.min(1.6f, rate))).apply();
    }

    public float getSpeechPitch() {
        return prefs.getFloat(KEY_SPEECH_PITCH, 1.08f);
    }

    public void setSpeechPitch(float pitch) {
        prefs.edit().putFloat(KEY_SPEECH_PITCH, Math.max(0.75f, Math.min(1.35f, pitch))).apply();
    }

    public float getSpeechVolume() {
        return prefs.getFloat(KEY_SPEECH_VOLUME, 1.0f);
    }

    public void setSpeechVolume(float volume) {
        prefs.edit().putFloat(KEY_SPEECH_VOLUME, Math.max(0.2f, Math.min(1.0f, volume))).apply();
    }

    public String getSelectedHindiVoiceName() {
        return prefs.getString(KEY_HINDI_VOICE_NAME, "");
    }

    public void setSelectedHindiVoiceName(String voiceName) {
        prefs.edit().putString(KEY_HINDI_VOICE_NAME, voiceName == null ? "" : voiceName).apply();
    }

    public String getSelectedEnglishVoiceName() {
        return prefs.getString(KEY_ENGLISH_VOICE_NAME, "");
    }

    public void setSelectedEnglishVoiceName(String voiceName) {
        prefs.edit().putString(KEY_ENGLISH_VOICE_NAME, voiceName == null ? "" : voiceName).apply();
    }

    public String getThemeMode() {
        return prefs.getString(KEY_THEME_MODE, THEME_DARK);
    }

    public void setThemeMode(String themeMode) {
        prefs.edit().putString(KEY_THEME_MODE, themeMode).apply();
        applySavedTheme();
    }

    public void applySavedTheme() {
        String mode = getThemeMode();
        if (THEME_LIGHT.equals(mode)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        } else if (THEME_SYSTEM.equals(mode)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        }
    }

    public boolean isBatterySaverMode() {
        return prefs.getBoolean(KEY_BATTERY_SAVER, false);
    }

    public void setBatterySaverMode(boolean enabled) {
        prefs.edit().putBoolean(KEY_BATTERY_SAVER, enabled).apply();
    }

    public void forgetUserMemory() {
        prefs.edit()
                .remove(KEY_MEMORY_PERMISSION)
                .remove(KEY_PREFERRED_NAME)
                .remove(KEY_INTERESTS)
                .apply();
    }
}
