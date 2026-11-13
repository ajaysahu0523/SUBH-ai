package com.example.assistant;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import android.util.Log;

import com.example.data.UserPreferencesManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Manages real Android SpeechRecognizer and TextToSpeech with:
 * - Pleasant female-prioritized voice selection per language (Hindi, English, Hinglish)
 * - Natural sentence pauses, customizable speed, pitch, and volume
 * - Voice data availability check and system voice-pack installer action
 * - Continuous two-way loop & "Hey SUBH" wake-word standby loop
 * - Microphone ON/OFF control & bounded error recovery
 * - Voice/button interruption (barge-in) while speaking
 */
public class VoiceAssistantManager {
    private static final String TAG = "VoiceAssistantMgr";
    public static final String AUTO_FEMALE_OPTION = "Auto Female Voice (Recommended)";

    public interface Listener {
        void onListeningStarted(boolean isSleepStandby);
        void onPartialTranscript(String partialText);
        void onRmsChanged(float rmsDb);
        void onSpeechResult(String recognizedText, boolean fromSleepStandby);
        void onSpeechError(String friendlyMessage, boolean fatal);
        void onSpeakingStarted();
        void onSpeakingFinished(boolean shouldResumeListening);
        void onTtsReady();
    }

    public static class VoiceOption {
        public final String rawName;
        public final String displayLabel;
        public final int score;

        public VoiceOption(String rawName, String displayLabel, int score) {
            this.rawName = rawName;
            this.displayLabel = displayLabel;
            this.score = score;
        }

        @Override
        public String toString() {
            return displayLabel;
        }
    }

    private final Context appContext;
    private final Listener listener;
    private final UserPreferencesManager prefsManager;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private SpeechRecognizer speechRecognizer;
    private TextToSpeech textToSpeech;
    private boolean ttsInitialized = false;
    private boolean muted = false;
    private boolean micEnabled = true;
    private boolean isListening = false;
    private boolean isSpeaking = false;
    private boolean sleepStandbyMode = false;
    private boolean continuousMode = true;
    private boolean resumeAfterSpeechFlag = false;
    private boolean foregroundActive = true;
    private String currentLanguagePref = "auto";
    private String finalUtteranceId = "";
    private int consecutiveErrorCount = 0;

    private final Runnable pendingRestartRunnable = () -> {
        if (foregroundActive && micEnabled && !isSpeaking && (continuousMode || sleepStandbyMode)) {
            startListeningInternal(currentLanguagePref, sleepStandbyMode);
        }
    };

    public VoiceAssistantManager(Context context, Listener listener) {
        this.appContext = context.getApplicationContext();
        this.listener = listener;
        this.prefsManager = new UserPreferencesManager(appContext);
        this.micEnabled = prefsManager.isMicEnabled();
        this.muted = prefsManager.isTtsMuted();
        this.continuousMode = prefsManager.isConversationModeEnabled();
        initTts();
    }

    public void setContinuousMode(boolean enabled) {
        this.continuousMode = enabled;
        if (!enabled && !sleepStandbyMode) {
            mainHandler.removeCallbacks(pendingRestartRunnable);
        }
    }

    public void setMicEnabled(boolean enabled) {
        this.micEnabled = enabled;
        if (!enabled) {
            mainHandler.removeCallbacks(pendingRestartRunnable);
            stopListening();
        }
    }

    public boolean isMicEnabled() {
        return micEnabled;
    }

    private void initTts() {
        textToSpeech = new TextToSpeech(appContext, status -> {
            if (status == TextToSpeech.SUCCESS) {
                ttsInitialized = true;
                textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override
                    public void onStart(String utteranceId) {
                        mainHandler.post(() -> {
                            if (!isSpeaking) {
                                isSpeaking = true;
                                listener.onSpeakingStarted();
                                if (micEnabled && isHeadsetConnected()) {
                                    mainHandler.postDelayed(() -> {
                                        if (isSpeaking && foregroundActive && !isListening && micEnabled) {
                                            startListeningInternal(currentLanguagePref, false);
                                        }
                                    }, 450);
                                }
                            }
                        });
                    }

                    @Override
                    public void onDone(String utteranceId) {
                        if (utteranceId == null || !utteranceId.equals(finalUtteranceId)) {
                            return;
                        }
                        mainHandler.post(() -> {
                            isSpeaking = false;
                            listener.onSpeakingFinished(resumeAfterSpeechFlag);
                        });
                    }

                    @Override
                    public void onError(String utteranceId) {
                        mainHandler.post(() -> {
                            isSpeaking = false;
                            listener.onSpeakingFinished(resumeAfterSpeechFlag);
                        });
                    }
                });
                mainHandler.post(listener::onTtsReady);
            } else {
                Log.e(TAG, "TextToSpeech initialization failed with status: " + status);
            }
        });
    }

    public boolean isLanguageVoiceAvailable(Locale locale) {
        if (!ttsInitialized || textToSpeech == null) {
            return false;
        }
        int res = textToSpeech.isLanguageAvailable(locale);
        return res == TextToSpeech.LANG_AVAILABLE
                || res == TextToSpeech.LANG_COUNTRY_AVAILABLE
                || res == TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE;
    }

    public static boolean openInstallTtsData(Context context) {
        try {
            Intent installIntent = new Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
            if (!(context instanceof android.app.Activity)) {
                installIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            context.startActivity(installIntent);
            return true;
        } catch (ActivityNotFoundException e) {
            return false;
        }
    }

    public List<VoiceOption> getAvailableVoicesForLanguage(String langPrefix) {
        List<VoiceOption> options = new ArrayList<>();
        options.add(new VoiceOption("", AUTO_FEMALE_OPTION, 9999));
        if (!ttsInitialized || textToSpeech == null) {
            return options;
        }
        try {
            Set<Voice> voices = textToSpeech.getVoices();
            if (voices == null) return options;
            List<VoiceOption> matched = new ArrayList<>();
            for (Voice v : voices) {
                Locale loc = v.getLocale();
                if (loc == null || !langPrefix.equalsIgnoreCase(loc.getLanguage())) {
                    continue;
                }
                int score = scoreFemaleVoice(v);
                boolean isLikelyFemale = score >= 100;
                String tag = isLikelyFemale ? "Female" : "Voice";
                String quality = v.getQuality() >= Voice.QUALITY_HIGH ? "HD" : "Std";
                String offline = v.isNetworkConnectionRequired() ? "Online" : "Offline";
                String label = tag + " • " + loc.toLanguageTag() + " (" + quality + ", " + offline + ") [" + shortVoiceCode(v.getName()) + "]";
                matched.add(new VoiceOption(v.getName(), label, score));
            }
            Collections.sort(matched, Comparator.comparingInt((VoiceOption o) -> -o.score));
            int limit = Math.min(matched.size(), 15);
            for (int i = 0; i < limit; i++) {
                options.add(matched.get(i));
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to enumerate TTS voices", e);
        }
        return options;
    }

    private String shortVoiceCode(String rawName) {
        if (rawName == null) return "sys";
        String[] parts = rawName.split("-");
        if (parts.length >= 2) {
            return parts[parts.length - 2] + "-" + parts[parts.length - 1];
        }
        return rawName.length() > 12 ? rawName.substring(rawName.length() - 12) : rawName;
    }

    /**
     * Heuristic scoring that prioritizes soft, natural, high-quality female voices
     * across Android / Google TTS engines without mislabeling when engine metadata is omitted.
     */
    public static int scoreFemaleVoice(Voice v) {
        if (v == null) return 0;
        String name = v.getName() == null ? "" : v.getName().toLowerCase(Locale.ROOT);
        int score = 0;

        if (name.contains("female") || name.contains("#female") || name.contains("-f-")) {
            score += 200;
        }
        // Known Google TTS Hindi & English female voice identifiers
        if (name.contains("-hia-") || name.contains("-hic-") || name.contains("-hie-")
                || name.contains("-end-") || name.contains("-ene-") || name.contains("-sfg-")
                || name.contains("-tpf-") || name.contains("-iob-") || name.contains("-fis-")
                || name.contains("female_")) {
            score += 180;
        }
        // Deprioritize explicit male identifiers
        if (name.contains("male") && !name.contains("female")) {
            score -= 150;
        }
        if (name.contains("-hid-") || name.contains("-hib-") || name.contains("-iom-")
                || name.contains("-iol-") || name.contains("-tpd-")) {
            score -= 120;
        }

        if (v.getQuality() >= Voice.QUALITY_HIGH) {
            score += 40;
        }
        if (!v.isNetworkConnectionRequired()) {
            score += 20;
        }
        Locale loc = v.getLocale();
        if (loc != null && ("IN".equalsIgnoreCase(loc.getCountry()) || "US".equalsIgnoreCase(loc.getCountry()))) {
            score += 15;
        }
        return score;
    }

    public boolean isRecognitionAvailable() {
        return SpeechRecognizer.isRecognitionAvailable(appContext);
    }

    public void setForegroundActive(boolean active) {
        this.foregroundActive = active;
        if (!active) {
            mainHandler.removeCallbacks(pendingRestartRunnable);
            stopListening();
            stopSpeaking(false);
        }
    }

    public void setMuted(boolean muted) {
        this.muted = muted;
        if (muted && isSpeaking) {
            stopSpeaking(false);
        }
    }

    public boolean isMuted() {
        return muted;
    }

    public boolean isListening() {
        return isListening;
    }

    public boolean isSpeaking() {
        return isSpeaking;
    }

    public void startListening(String languagePref, boolean sleepStandby) {
        if (!micEnabled) {
            listener.onSpeechError("Microphone control is currently OFF.", true);
            return;
        }
        this.currentLanguagePref = languagePref == null ? "auto" : languagePref;
        this.sleepStandbyMode = sleepStandby;
        this.consecutiveErrorCount = 0;
        mainHandler.removeCallbacks(pendingRestartRunnable);

        if (isSpeaking) {
            stopSpeaking(false);
        }
        startListeningInternal(this.currentLanguagePref, sleepStandby);
    }

    public void scheduleAutoRestart(String languagePref, boolean sleepStandby, long delayMs) {
        this.currentLanguagePref = languagePref == null ? "auto" : languagePref;
        this.sleepStandbyMode = sleepStandby;
        mainHandler.removeCallbacks(pendingRestartRunnable);
        if (foregroundActive && micEnabled) {
            mainHandler.postDelayed(pendingRestartRunnable, delayMs);
        }
    }

    private void startListeningInternal(String languagePref, boolean sleepStandby) {
        if (!foregroundActive || !micEnabled) {
            return;
        }
        if (!isRecognitionAvailable()) {
            listener.onSpeechError("Speech recognition is not available on this device.", true);
            return;
        }

        try {
            destroyRecognizer();
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(appContext);
            speechRecognizer.setRecognitionListener(createRecognitionListener(sleepStandby));

            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1400L);
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L);

            String recognitionLocale = resolveRecognizerLocale(languagePref);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, recognitionLocale);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, recognitionLocale);

            isListening = true;
            listener.onListeningStarted(sleepStandby);
            speechRecognizer.startListening(intent);
        } catch (Exception e) {
            Log.e(TAG, "Failed to start SpeechRecognizer", e);
            isListening = false;
            listener.onSpeechError("Could not start microphone listening: " + e.getMessage(), false);
        }
    }

    private String resolveRecognizerLocale(String languagePref) {
        if ("hi".equals(languagePref)) {
            return "hi-IN";
        } else if ("en".equals(languagePref)) {
            return "en-US";
        }
        return "en-IN";
    }

    public void stopListening() {
        mainHandler.removeCallbacks(pendingRestartRunnable);
        isListening = false;
        if (speechRecognizer != null) {
            try {
                speechRecognizer.stopListening();
                speechRecognizer.cancel();
            } catch (Exception e) {
                Log.w(TAG, "Error stopping SpeechRecognizer", e);
            }
        }
    }

    public void speak(String text, String languageCode, boolean autoResumeListeningAfter) {
        speak(text, languageCode, autoResumeListeningAfter, false);
    }

    public void speak(String text, String languageCode, boolean autoResumeListeningAfter, boolean forceSpeak) {
        this.resumeAfterSpeechFlag = autoResumeListeningAfter && micEnabled;
        if (!forceSpeak && (muted || !prefsManager.isAutoVoiceReplyEnabled())) {
            isSpeaking = false;
            listener.onSpeakingFinished(this.resumeAfterSpeechFlag);
            return;
        }
        if (text == null || text.trim().isEmpty() || !ttsInitialized || textToSpeech == null) {
            isSpeaking = false;
            listener.onSpeakingFinished(this.resumeAfterSpeechFlag);
            return;
        }

        String spokenText = text.replace("[Sample Mode — Firebase Required]", "").trim();
        if (spokenText.isEmpty()) {
            listener.onSpeakingFinished(this.resumeAfterSpeechFlag);
            return;
        }

        Locale targetLocale;
        if (LanguageUtils.LANG_HINDI.equals(languageCode)) {
            targetLocale = new Locale("hi", "IN");
        } else if (LanguageUtils.LANG_HINGLISH.equals(languageCode)) {
            targetLocale = new Locale("en", "IN");
        } else {
            targetLocale = Locale.US;
        }

        int langResult = textToSpeech.setLanguage(targetLocale);
        if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            textToSpeech.setLanguage(Locale.US);
        }

        applyFemaleVoicePreferences(languageCode);

        Bundle params = new Bundle();
        params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, prefsManager.getSpeechVolume());

        List<String> sentences = splitIntoNaturalSentences(spokenText);
        if (sentences.isEmpty()) {
            listener.onSpeakingFinished(this.resumeAfterSpeechFlag);
            return;
        }

        String batchId = UUID.randomUUID().toString();
        finalUtteranceId = batchId + "_end";

        for (int i = 0; i < sentences.size(); i++) {
            String chunk = sentences.get(i);
            boolean isFirst = (i == 0);
            boolean isLast = (i == sentences.size() - 1);
            int queueMode = isFirst ? TextToSpeech.QUEUE_FLUSH : TextToSpeech.QUEUE_ADD;
            String uid = isLast ? finalUtteranceId : (batchId + "_" + i);

            textToSpeech.speak(chunk, queueMode, params, uid);
            if (!isLast) {
                textToSpeech.playSilentUtterance(210L, TextToSpeech.QUEUE_ADD, batchId + "_pause_" + i);
            }
        }
    }

    private List<String> splitIntoNaturalSentences(String text) {
        List<String> list = new ArrayList<>();
        String[] parts = text.split("(?<=[।.!?\\n])\\s+");
        for (String p : parts) {
            String clean = p.trim();
            if (!clean.isEmpty()) {
                list.add(clean);
            }
        }
        return list;
    }

    private void applyFemaleVoicePreferences(String languageCode) {
        textToSpeech.setSpeechRate(prefsManager.getSpeechRate());
        textToSpeech.setPitch(prefsManager.getSpeechPitch());

        boolean isHindi = LanguageUtils.LANG_HINDI.equals(languageCode);
        String preferredVoiceName = isHindi
                ? prefsManager.getSelectedHindiVoiceName()
                : prefsManager.getSelectedEnglishVoiceName();

        try {
            Set<Voice> voices = textToSpeech.getVoices();
            if (voices == null || voices.isEmpty()) return;

            if (preferredVoiceName != null && !preferredVoiceName.isEmpty()) {
                for (Voice v : voices) {
                    if (preferredVoiceName.equals(v.getName())) {
                        textToSpeech.setVoice(v);
                        return;
                    }
                }
            }

            // Automatic best female voice selection for the response language
            String targetLangPrefix = isHindi ? "hi" : "en";
            Voice bestFemale = null;
            int bestScore = Integer.MIN_VALUE;
            for (Voice v : voices) {
                Locale loc = v.getLocale();
                if (loc == null || !targetLangPrefix.equalsIgnoreCase(loc.getLanguage())) {
                    continue;
                }
                int score = scoreFemaleVoice(v);
                if (!isHindi && LanguageUtils.LANG_HINGLISH.equals(languageCode)
                        && "IN".equalsIgnoreCase(loc.getCountry())) {
                    score += 35;
                }
                if (score > bestScore) {
                    bestScore = score;
                    bestFemale = v;
                }
            }
            if (bestFemale != null) {
                textToSpeech.setVoice(bestFemale);
            }
        } catch (Exception ignored) {
        }
    }

    public void stopSpeaking(boolean allowAutoResume) {
        this.resumeAfterSpeechFlag = allowAutoResume && micEnabled;
        if (textToSpeech != null && isSpeaking) {
            textToSpeech.stop();
        }
        isSpeaking = false;
    }

    private RecognitionListener createRecognitionListener(boolean isSleepStandby) {
        return new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                consecutiveErrorCount = 0;
            }

            @Override
            public void onBeginningOfSpeech() {
            }

            @Override
            public void onRmsChanged(float rmsdB) {
                listener.onRmsChanged(rmsdB);
            }

            @Override
            public void onBufferReceived(byte[] buffer) {
            }

            @Override
            public void onEndOfSpeech() {
                isListening = false;
            }

            @Override
            public void onError(int error) {
                isListening = false;
                boolean transientError = isTransientRecognizerError(error);
                if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                    listener.onSpeechError("Microphone permission is required.", true);
                    return;
                }
                if (error == SpeechRecognizer.ERROR_NETWORK && !NetworkUtils.isNetworkAvailable(appContext)) {
                    listener.onSpeechError("Network connection unavailable for speech recognition.", true);
                    return;
                }

                if (transientError) {
                    consecutiveErrorCount++;
                    long retryDelay = Math.min(2000L, 500L + 300L * Math.min(consecutiveErrorCount, 5));
                    if (isSleepStandby || continuousMode || consecutiveErrorCount <= 3) {
                        listener.onSpeechError(mapErrorMessage(error), false);
                        if (isSleepStandby && foregroundActive && micEnabled) {
                            scheduleAutoRestart(currentLanguagePref, true, retryDelay);
                        }
                        return;
                    }
                }
                listener.onSpeechError(mapErrorMessage(error), true);
            }

            @Override
            public void onResults(Bundle results) {
                isListening = false;
                consecutiveErrorCount = 0;
                ArrayList<String> matches = results != null
                        ? results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        : null;
                if (matches != null && !matches.isEmpty()) {
                    String best = matches.get(0);
                    if (isSpeaking && LanguageUtils.isStopSpeechCommand(best)) {
                        stopSpeaking(false);
                    }
                    listener.onSpeechResult(best, isSleepStandby);
                } else if (isSleepStandby && foregroundActive && micEnabled) {
                    scheduleAutoRestart(currentLanguagePref, true, 400L);
                }
            }

            @Override
            public void onPartialResults(Bundle partialResults) {
                ArrayList<String> partial = partialResults != null
                        ? partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        : null;
                if (partial != null && !partial.isEmpty()) {
                    String partialText = partial.get(0);
                    if (isSpeaking && (LanguageUtils.isStopSpeechCommand(partialText)
                            || LanguageUtils.containsWakeWord(partialText))) {
                        stopSpeaking(false);
                        stopListening();
                        listener.onSpeechResult(partialText, isSleepStandby);
                        return;
                    }
                    listener.onPartialTranscript(partialText);
                }
            }

            @Override
            public void onEvent(int eventType, Bundle params) {
            }
        };
    }

    private boolean isTransientRecognizerError(int error) {
        return error == SpeechRecognizer.ERROR_NO_MATCH
                || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                || error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY
                || error == SpeechRecognizer.ERROR_CLIENT
                || error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT;
    }

    private String mapErrorMessage(int error) {
        switch (error) {
            case SpeechRecognizer.ERROR_AUDIO:
                return "Audio recording error. Check your microphone.";
            case SpeechRecognizer.ERROR_CLIENT:
                return "Reconnecting voice listener…";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                return "Microphone permission denied.";
            case SpeechRecognizer.ERROR_NETWORK:
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                return "Network error during voice recognition.";
            case SpeechRecognizer.ERROR_NO_MATCH:
                return "Didn't catch that. Listening…";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                return "Voice engine busy. Recovering…";
            case SpeechRecognizer.ERROR_SERVER:
                return "Speech server error. Please try again.";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                return "No speech detected. Waiting…";
            default:
                return "Speech recognition error (" + error + ").";
        }
    }

    private boolean isHeadsetConnected() {
        AudioManager audioManager = (AudioManager) appContext.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager == null) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            AudioDeviceInfo[] devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
            for (AudioDeviceInfo device : devices) {
                int type = device.getType();
                if (type == AudioDeviceInfo.TYPE_WIRED_HEADSET
                        || type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES
                        || type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                        || type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                        || type == AudioDeviceInfo.TYPE_USB_HEADSET) {
                    return true;
                }
            }
        }
        return false;
    }

    private void destroyRecognizer() {
        if (speechRecognizer != null) {
            try {
                speechRecognizer.destroy();
            } catch (Exception ignored) {
            }
            speechRecognizer = null;
        }
    }

    public void destroy() {
        mainHandler.removeCallbacksAndMessages(null);
        destroyRecognizer();
        if (textToSpeech != null) {
            try {
                textToSpeech.stop();
                textToSpeech.shutdown();
            } catch (Exception ignored) {
            }
            textToSpeech = null;
        }
    }
}
