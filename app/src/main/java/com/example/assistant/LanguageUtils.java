package com.example.assistant;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LanguageUtils {
    public static final String LANG_HINDI = "hi";
    public static final String LANG_HINGLISH = "hinglish";
    public static final String LANG_ENGLISH = "en";

    private static final Set<String> HINGLISH_KEYWORDS = new HashSet<>(Arrays.asList(
            "namaste", "namaskar", "kaise", "kaisa", "kaisi", "kya", "kaun", "kab",
            "kahan", "kyun", "kyon", "hai", "hain", "hoon", "hun", "ho", "tha", "thi",
            "mera", "meri", "mere", "tum", "tumhara", "aap", "aapka", "bhai", "yaar",
            "dost", "batao", "bataiye", "sunao", "sunaiye", "kholo", "khol", "chalao",
            "chala", "karo", "kar", "karna", "chahiye", "shukriya", "dhanyavaad",
            "dhanyawad", "theek", "thik", "accha", "acha", "bahut", "bohot", "mast",
            "nahi", "nahin", "haan", "han", "samjhao", "likho", "bolo", "bol", "par",
            "mein", "mujhe", "hum", "subah", "raat", "din", "gaana", "gana", "gaane",
            "mausam", "samay", "waqt", "khush", "madad", "naam", "pasand", "soja",
            "shubh", "subh", "ratri", "ruk", "ruko", "chup", "band"
    ));

    private static final Pattern WAKE_WORD_PATTERN = Pattern.compile(
            "(?i)(?:hey|hay|hi|hello|ok|okay|हे|हाय|हेलो)\\s+(?:subh|shubh|sub|shub|शुभ)\\b[,\\s]*"
    );

    private static final Pattern SLEEP_WORD_PATTERN = Pattern.compile(
            "(?i)(?:good\\s*night|goodnight|go\\s+to\\s+sleep|sleep\\s+mode|गुड\\s*नाइट|शुभ\\s*रात्रि|सो\\s*जाओ)"
                    + "(?:\\s+(?:subh|shubh|शुभ))?"
    );

    private LanguageUtils() {
    }

    public static String detectLanguage(String text) {
        if (text == null || text.trim().isEmpty()) {
            return LANG_ENGLISH;
        }
        int devanagariCount = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= '\u0900' && c <= '\u097F') {
                devanagariCount++;
            }
        }
        if (devanagariCount > 0) {
            return LANG_HINDI;
        }

        String lower = text.toLowerCase(Locale.ROOT).replaceAll("[^a-z\\s]", " ");
        String[] tokens = lower.trim().split("\\s+");
        int hinglishHits = 0;
        for (String token : tokens) {
            if (HINGLISH_KEYWORDS.contains(token)) {
                hinglishHits++;
            }
        }
        if (hinglishHits >= 2 || (tokens.length <= 4 && hinglishHits >= 1)) {
            return LANG_HINGLISH;
        }
        return LANG_ENGLISH;
    }

    public static boolean isHindiOrHinglish(String langCode) {
        return LANG_HINDI.equals(langCode) || LANG_HINGLISH.equals(langCode);
    }

    public static boolean containsWakeWord(String text) {
        if (text == null) {
            return false;
        }
        String normalized = text.trim().toLowerCase(Locale.ROOT);
        if (WAKE_WORD_PATTERN.matcher(normalized).find()) {
            return true;
        }
        return normalized.equals("hey subh")
                || normalized.equals("hey shubh")
                || normalized.equals("हे शुभ")
                || normalized.equals("हाय शुभ");
    }

    public static String stripWakeWord(String text) {
        if (text == null) {
            return "";
        }
        Matcher matcher = WAKE_WORD_PATTERN.matcher(text.trim());
        String stripped = matcher.replaceFirst("").trim();
        if (stripped.equalsIgnoreCase("hey subh") || stripped.equalsIgnoreCase("hey shubh")) {
            return "";
        }
        return stripped;
    }

    public static boolean isSleepCommand(String text) {
        if (text == null) {
            return false;
        }
        String trimmed = text.trim();
        return SLEEP_WORD_PATTERN.matcher(trimmed).find();
    }

    public static boolean isStopSpeechCommand(String text) {
        if (text == null) {
            return false;
        }
        String lower = text.trim().toLowerCase(Locale.ROOT);
        return lower.equals("stop")
                || lower.equals("stop speaking")
                || lower.equals("quiet")
                || lower.equals("be quiet")
                || lower.equals("ruko")
                || lower.equals("रुको")
                || lower.equals("चुप")
                || lower.equals("chup")
                || lower.equals("band karo");
    }
}
