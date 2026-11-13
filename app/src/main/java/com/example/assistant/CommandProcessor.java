package com.example.assistant;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.provider.CalendarContract;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.provider.Settings;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CommandProcessor {

    public enum SensitiveActionType {
        NONE,
        OPEN_CONTACTS,
        CREATE_REMINDER,
        SAVE_NOTE,
        OPEN_CALENDAR
    }

    public static class CommandResult {
        public final boolean handled;
        public final boolean requiresConfirmation;
        public final SensitiveActionType sensitiveAction;
        public final String actionPayload;
        public final String responseText;
        public final String languageCode;

        public CommandResult(boolean handled, String responseText, String languageCode) {
            this(handled, false, SensitiveActionType.NONE, "", responseText, languageCode);
        }

        public CommandResult(
                boolean handled,
                boolean requiresConfirmation,
                SensitiveActionType sensitiveAction,
                String actionPayload,
                String responseText,
                String languageCode
        ) {
            this.handled = handled;
            this.requiresConfirmation = requiresConfirmation;
            this.sensitiveAction = sensitiveAction;
            this.actionPayload = actionPayload == null ? "" : actionPayload;
            this.responseText = responseText;
            this.languageCode = languageCode;
        }
    }

    private static final Map<String, String> HINDI_APP_ALIASES = new HashMap<>();
    static {
        HINDI_APP_ALIASES.put("कैमरा", "camera");
        HINDI_APP_ALIASES.put("फोटो", "photos");
        HINDI_APP_ALIASES.put("गैलरी", "gallery");
        HINDI_APP_ALIASES.put("कैलकुलेटर", "calculator");
        HINDI_APP_ALIASES.put("मैप्स", "maps");
        HINDI_APP_ALIASES.put("नक्शा", "maps");
        HINDI_APP_ALIASES.put("घड़ी", "clock");
        HINDI_APP_ALIASES.put("अलार्म", "clock");
        HINDI_APP_ALIASES.put("कैलेंडर", "calendar");
        HINDI_APP_ALIASES.put("क्रोम", "chrome");
        HINDI_APP_ALIASES.put("व्हाट्सएप", "whatsapp");
        HINDI_APP_ALIASES.put("इंस्टाग्राम", "instagram");
        HINDI_APP_ALIASES.put("स्पॉटिफाई", "spotify");
        HINDI_APP_ALIASES.put("जीमेल", "gmail");
        HINDI_APP_ALIASES.put("प्ले स्टोर", "play store");
    }

    private static final Pattern YOUTUBE_SEARCH_EN = Pattern.compile(
            "(?i)^(?:please\\s+)?(?:search|play|find|watch)\\s+(?:for\\s+)?(.+?)\\s+(?:on|in)\\s+youtube$"
    );
    private static final Pattern YOUTUBE_SEARCH_PREFIX = Pattern.compile(
            "(?i)^(?:search\\s+youtube\\s+(?:for\\s+)?|youtube\\s+search\\s+|youtube\\s+par\\s+)(.+?)(?:\\s+(?:search\\s*karo|kholo|chalao|dikhao|खोजो|चलाओ|सर्च\\s*करो))?$"
    );
    private static final Pattern YOUTUBE_SEARCH_HI = Pattern.compile(
            "(?i)^यूट्यूब\\s*(?:पर|में)?\\s*(.+?)\\s*(?:सर्च\\s*करो|खोजो|चलाओ|दिखाओ)?$"
    );

    private static final Pattern GOOGLE_SEARCH_EN = Pattern.compile(
            "(?i)^(?:please\\s+)?(?:search\\s+(?:on\\s+)?google\\s+(?:for\\s+)?|google\\s+search\\s+(?:for\\s+)?|search\\s+(?:for\\s+)?|google\\s+)(.+)$"
    );
    private static final Pattern GOOGLE_SEARCH_HI = Pattern.compile(
            "(?i)^(?:गूगल|google)\\s*(?:पर|में|par)?\\s*(.+?)\\s*(?:सर्च\\s*करो|खोजो|search\\s*karo|khojo|batao)$"
    );

    private static final Pattern REMINDER_PATTERN = Pattern.compile(
            "(?i)(?:remind me to|create a reminder(?: to| for)?|set a reminder(?: to| for)?|add reminder|reminder:?|रिमाइंडर सेट करो|मुझे याद दिलाना)\\s+(.+)"
    );
    private static final Pattern NOTE_SAVE_PATTERN = Pattern.compile(
            "(?i)(?:save (?:a )?note:?|take a note:?|remember note:?|note that|मेरा नोट सेव करो|नोट लिखो)\\s+(.+)"
    );

    private static final Pattern OPEN_APP_EN = Pattern.compile(
            "(?i)^(?:please\\s+)?(?:open|launch|start)\\s+(?:the\\s+)?(.+?)(?:\\s+app)?$"
    );
    private static final Pattern OPEN_APP_HI = Pattern.compile(
            "(?i)^(.+?)\\s*(?:ऐप\\s*)?(?:खोलो|खोलिये|चलाओ|kholo|open\\s*karo|chalao)$"
    );

    private CommandProcessor() {
    }

    public static CommandResult tryExecute(Context context, String rawInput, String preferredName) {
        if (rawInput == null || rawInput.trim().isEmpty()) {
            return new CommandResult(false, "", LanguageUtils.LANG_ENGLISH);
        }

        String text = rawInput.trim();
        String lang = LanguageUtils.detectLanguage(text);
        String lower = text.toLowerCase(Locale.ROOT);
        String nameSuffix = (preferredName != null && !preferredName.isEmpty()) ? ", " + preferredName : "";

        // 1. Sensitive action: Save Note (requires explicit confirmation)
        Matcher noteMatcher = NOTE_SAVE_PATTERN.matcher(text);
        if (noteMatcher.find() && noteMatcher.group(1) != null) {
            String noteContent = noteMatcher.group(1).trim();
            String prompt = LanguageUtils.LANG_HINDI.equals(lang)
                    ? "क्या मैं आपकी अनुमति से यह नोट सेव करूँ: \"" + noteContent + "\"?"
                    : "With your permission, should I save this note: \"" + noteContent + "\"?";
            return new CommandResult(true, true, SensitiveActionType.SAVE_NOTE, noteContent, prompt, lang);
        }

        // 2. Sensitive action: Create Reminder / Task (requires explicit confirmation)
        Matcher remMatcher = REMINDER_PATTERN.matcher(text);
        if (remMatcher.find() && remMatcher.group(1) != null) {
            String title = remMatcher.group(1).trim();
            String prompt = LanguageUtils.LANG_HINDI.equals(lang)
                    ? "क्या मैं यह रिमाइंडर जोड़ दूँ: \"" + title + "\"?"
                    : "Please confirm if I should create a reminder for: \"" + title + "\".";
            return new CommandResult(true, true, SensitiveActionType.CREATE_REMINDER, title, prompt, lang);
        }

        // 3. Sensitive action: Open Contacts (requires explicit confirmation)
        if (lower.contains("open contacts") || lower.contains("show contacts")
                || lower.equals("contacts") || lower.contains("कॉन्टैक्ट्स खोलो")
                || lower.contains("contacts kholo")) {
            String prompt = LanguageUtils.LANG_HINDI.equals(lang)
                    ? "कॉन्टैक्ट्स खोलने से पहले कृपया पुष्टि करें।"
                    : "Please confirm before I open your Contacts app.";
            return new CommandResult(true, true, SensitiveActionType.OPEN_CONTACTS, "", prompt, lang);
        }

        // 4. Sensitive action: Open / Add to Calendar (requires explicit confirmation)
        if (lower.contains("open calendar") || lower.contains("schedule event")
                || lower.contains("add calendar event") || lower.contains("कैलेंडर खोलो")
                || lower.contains("calendar kholo")) {
            String prompt = LanguageUtils.LANG_HINDI.equals(lang)
                    ? "क्या मैं आपका कैलेंडर खोल दूँ?"
                    : "Confirm if you would like me to open your Calendar.";
            return new CommandResult(true, true, SensitiveActionType.OPEN_CALENDAR, "", prompt, lang);
        }

        // 5. Direct YouTube open
        if (lower.matches("^(please\\s+)?(open|launch|start)\\s+(the\\s+)?youtube(\\s+app)?$")
                || lower.equals("youtube")
                || lower.equals("यूट्यूब खोलो")
                || lower.equals("यूट्यूब चलाओ")
                || lower.equals("youtube kholo")
                || lower.equals("youtube open karo")) {
            if (!NetworkUtils.isNetworkAvailable(context)) {
                return new CommandResult(true, offlineMessage(lang), lang);
            }
            boolean opened = openYouTubeAppOrWeb(context, null);
            if (opened) {
                return new CommandResult(true, formatYouTubeOpenMsg(lang, nameSuffix), lang);
            }
            return new CommandResult(true, appNotFoundMsg(lang, "YouTube"), lang);
        }

        // 6. YouTube search commands
        String ytQuery = extractMatch(YOUTUBE_SEARCH_EN, text);
        if (ytQuery == null) ytQuery = extractMatch(YOUTUBE_SEARCH_PREFIX, text);
        if (ytQuery == null) ytQuery = extractMatch(YOUTUBE_SEARCH_HI, text);
        if (ytQuery != null && !ytQuery.isEmpty()
                && !ytQuery.equalsIgnoreCase("खोलो")
                && !ytQuery.equalsIgnoreCase("चलाओ")) {
            if (!NetworkUtils.isNetworkAvailable(context)) {
                return new CommandResult(true, offlineMessage(lang), lang);
            }
            boolean opened = openYouTubeAppOrWeb(context, ytQuery);
            if (opened) {
                return new CommandResult(true, formatYouTubeSearchMsg(lang, ytQuery), lang);
            }
            return new CommandResult(true, appNotFoundMsg(lang, "YouTube"), lang);
        }

        // 7. Direct Google open
        if (lower.matches("^(please\\s+)?(open|launch)\\s+(the\\s+)?google$")
                || lower.equals("गूगल खोलो")
                || lower.equals("google kholo")) {
            if (!NetworkUtils.isNetworkAvailable(context)) {
                return new CommandResult(true, offlineMessage(lang), lang);
            }
            openUrl(context, "https://www.google.com");
            return new CommandResult(true, formatGoogleOpenMsg(lang, nameSuffix), lang);
        }

        // 8. Google search commands
        String googleQuery = extractMatch(GOOGLE_SEARCH_HI, text);
        if (googleQuery == null) googleQuery = extractMatch(GOOGLE_SEARCH_EN, text);
        if (googleQuery != null && !googleQuery.isEmpty()) {
            if (!NetworkUtils.isNetworkAvailable(context)) {
                return new CommandResult(true, offlineMessage(lang), lang);
            }
            openGoogleSearch(context, googleQuery);
            return new CommandResult(true, formatGoogleSearchMsg(lang, googleQuery), lang);
        }

        // 9. Camera & Calculator explicit intents
        if (lower.matches("^(please\\s+)?(open|launch|start)\\s+(the\\s+)?camera$")
                || lower.equals("कैमरा खोलो") || lower.equals("camera kholo")) {
            if (openCamera(context)) {
                return new CommandResult(true, formatAppOpenMsg(lang, "Camera", nameSuffix), lang);
            }
            return new CommandResult(true, appNotFoundMsg(lang, "Camera"), lang);
        }
        if (lower.matches("^(please\\s+)?(open|launch|start)\\s+(the\\s+)?calculator$")
                || lower.equals("कैलकुलेटर खोलो") || lower.equals("calculator kholo")) {
            if (openCalculator(context)) {
                return new CommandResult(true, formatAppOpenMsg(lang, "Calculator", nameSuffix), lang);
            }
            return new CommandResult(true, appNotFoundMsg(lang, "Calculator"), lang);
        }

        // 10. Real Android System Settings panels
        if (lower.contains("wi-fi") || lower.contains("wifi") || lower.contains("वाई-फाई") || lower.contains("वाईफाई")) {
            if (lower.contains("open") || lower.contains("setting") || lower.contains("turn ")
                    || lower.contains("खोलो") || lower.contains("kholo")) {
                launchSystemSettings(context, Settings.ACTION_WIFI_SETTINGS);
                return new CommandResult(true, formatSettingsPanelMsg(lang, "Wi-Fi Settings", nameSuffix), lang);
            }
        }
        if (lower.contains("bluetooth") || lower.contains("ब्लूटूथ")) {
            if (lower.contains("open") || lower.contains("setting") || lower.contains("turn ")
                    || lower.contains("खोलो") || lower.contains("kholo")) {
                launchSystemSettings(context, Settings.ACTION_BLUETOOTH_SETTINGS);
                return new CommandResult(true, formatSettingsPanelMsg(lang, "Bluetooth Settings", nameSuffix), lang);
            }
        }
        if (lower.contains("display settings") || lower.contains("brightness settings")) {
            launchSystemSettings(context, Settings.ACTION_DISPLAY_SETTINGS);
            return new CommandResult(true, formatSettingsPanelMsg(lang, "Display Settings", nameSuffix), lang);
        }
        if (lower.contains("sound settings") || lower.contains("volume settings")) {
            launchSystemSettings(context, Settings.ACTION_SOUND_SETTINGS);
            return new CommandResult(true, formatSettingsPanelMsg(lang, "Sound Settings", nameSuffix), lang);
        }
        if (lower.contains("battery settings")) {
            launchSystemSettings(context, Intent.ACTION_POWER_USAGE_SUMMARY);
            return new CommandResult(true, formatSettingsPanelMsg(lang, "Battery Settings", nameSuffix), lang);
        }
        if (lower.contains("location settings")) {
            launchSystemSettings(context, Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            return new CommandResult(true, formatSettingsPanelMsg(lang, "Location Settings", nameSuffix), lang);
        }

        // 11. Open installed Android application commands
        String appTarget = extractMatch(OPEN_APP_EN, text);
        if (appTarget == null) {
            appTarget = extractMatch(OPEN_APP_HI, text);
        }
        if (appTarget != null && !appTarget.isEmpty()) {
            String cleanTarget = appTarget.trim();
            if (HINDI_APP_ALIASES.containsKey(cleanTarget)) {
                cleanTarget = HINDI_APP_ALIASES.get(cleanTarget);
            }
            if (cleanTarget.equalsIgnoreCase("settings") || cleanTarget.equals("सेटिंग्स")) {
                launchSystemSettings(context, Settings.ACTION_SETTINGS);
                return new CommandResult(true, formatAppOpenMsg(lang, "Settings", nameSuffix), lang);
            }
            String launchedLabel = launchInstalledApp(context, cleanTarget);
            if (launchedLabel != null) {
                return new CommandResult(true, formatAppOpenMsg(lang, launchedLabel, nameSuffix), lang);
            }
            if (lower.contains("open ") || lower.contains("launch ") || lower.contains("start ")
                    || lower.contains("kholo") || lower.contains("खोलो") || lower.contains("खोलिये")) {
                return new CommandResult(true, appNotFoundMsg(lang, appTarget.trim()), lang);
            }
        }

        return new CommandResult(false, "", lang);
    }

    public static boolean executeConfirmedContactsOpen(Context context) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return true;
        } catch (ActivityNotFoundException e) {
            return launchInstalledApp(context, "contacts") != null;
        }
    }

    public static boolean executeConfirmedCalendarOpen(Context context, String optionalTitle) {
        try {
            Intent intent;
            if (optionalTitle != null && !optionalTitle.trim().isEmpty()) {
                intent = new Intent(Intent.ACTION_INSERT)
                        .setData(CalendarContract.Events.CONTENT_URI)
                        .putExtra(CalendarContract.Events.TITLE, optionalTitle.trim());
            } else {
                intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR);
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return true;
        } catch (ActivityNotFoundException e) {
            return launchInstalledApp(context, "calendar") != null;
        }
    }

    private static boolean openCamera(Context context) {
        try {
            Intent cam = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA);
            cam.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(cam);
            return true;
        } catch (ActivityNotFoundException e) {
            return launchInstalledApp(context, "camera") != null;
        }
    }

    private static boolean openCalculator(Context context) {
        try {
            Intent calc = new Intent(Intent.ACTION_MAIN);
            calc.addCategory(Intent.CATEGORY_APP_CALCULATOR);
            calc.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(calc);
            return true;
        } catch (ActivityNotFoundException e) {
            return launchInstalledApp(context, "calculator") != null;
        }
    }

    private static void launchSystemSettings(Context context, String action) {
        try {
            Intent intent = new Intent(action);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Intent fallback = new Intent(Settings.ACTION_SETTINGS);
            fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(fallback);
        }
    }

    private static String extractMatch(Pattern pattern, String input) {
        Matcher m = pattern.matcher(input);
        if (m.find() && m.groupCount() >= 1) {
            String group = m.group(1);
            return group == null ? null : group.trim();
        }
        return null;
    }

    private static boolean openYouTubeAppOrWeb(Context context, String query) {
        try {
            if (query == null || query.isEmpty()) {
                Intent ytIntent = context.getPackageManager().getLaunchIntentForPackage("com.google.android.youtube");
                if (ytIntent != null) {
                    ytIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(ytIntent);
                    return true;
                }
                openUrl(context, "https://www.youtube.com");
                return true;
            } else {
                String url = "https://www.youtube.com/results?search_query=" + Uri.encode(query);
                Intent webIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                webIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(webIntent);
                return true;
            }
        } catch (ActivityNotFoundException e) {
            return false;
        }
    }

    private static void openGoogleSearch(Context context, String query) {
        try {
            String url = "https://www.google.com/search?q=" + Uri.encode(query);
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Intent webSearch = new Intent(android.app.SearchManager.QUERY);
            webSearch.setAction(Intent.ACTION_WEB_SEARCH);
            webSearch.putExtra(android.app.SearchManager.QUERY, query);
            webSearch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(webSearch);
        }
    }

    private static void openUrl(Context context, String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    private static String launchInstalledApp(Context context, String requestedName) {
        PackageManager pm = context.getPackageManager();
        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> apps = pm.queryIntentActivities(mainIntent, 0);
        String queryLower = requestedName.toLowerCase(Locale.ROOT).trim();

        ResolveInfo bestMatch = null;
        String bestLabel = null;
        int bestScore = 0;

        for (ResolveInfo info : apps) {
            CharSequence labelSeq = info.loadLabel(pm);
            if (labelSeq == null) continue;
            String label = labelSeq.toString();
            String labelLower = label.toLowerCase(Locale.ROOT).trim();
            String pkgLower = info.activityInfo.packageName.toLowerCase(Locale.ROOT);

            int score = 0;
            if (labelLower.equals(queryLower)) {
                score = 100;
            } else if (labelLower.startsWith(queryLower)) {
                score = 80;
            } else if (labelLower.contains(queryLower) || pkgLower.contains(queryLower.replace(" ", ""))) {
                score = 60;
            }

            if (score > bestScore) {
                bestScore = score;
                bestMatch = info;
                bestLabel = label;
            }
        }

        if (bestMatch != null) {
            Intent launchIntent = pm.getLaunchIntentForPackage(bestMatch.activityInfo.packageName);
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(launchIntent);
                return bestLabel;
            }
        }
        return null;
    }

    private static String offlineMessage(String lang) {
        if (LanguageUtils.LANG_HINDI.equals(lang)) {
            return "इंटरनेट कनेक्शन उपलब्ध नहीं है। कृपया नेटवर्क जांचें और फिर प्रयास करें।";
        } else if (LanguageUtils.LANG_HINGLISH.equals(lang)) {
            return "Internet connection abhi available nahi hai. Please apna network check kijiye.";
        }
        return "No internet connection detected. Please check your network and try again.";
    }

    private static String formatYouTubeOpenMsg(String lang, String nameSuffix) {
        if (LanguageUtils.LANG_HINDI.equals(lang)) {
            return "जी" + nameSuffix + ", अभी YouTube खोल रही हूँ।";
        } else if (LanguageUtils.LANG_HINGLISH.equals(lang)) {
            return "Sure" + nameSuffix + ", abhi YouTube open kar rahi hoon.";
        }
        return "Right away" + nameSuffix + ". Opening YouTube now.";
    }

    private static String formatYouTubeSearchMsg(String lang, String query) {
        if (LanguageUtils.LANG_HINDI.equals(lang)) {
            return "YouTube पर \"" + query + "\" खोज रही हूँ।";
        } else if (LanguageUtils.LANG_HINGLISH.equals(lang)) {
            return "YouTube par \"" + query + "\" search kar rahi hoon.";
        }
        return "Searching YouTube for \"" + query + "\".";
    }

    private static String formatGoogleOpenMsg(String lang, String nameSuffix) {
        if (LanguageUtils.LANG_HINDI.equals(lang)) {
            return "जी" + nameSuffix + ", Google खोल रही हूँ।";
        } else if (LanguageUtils.LANG_HINGLISH.equals(lang)) {
            return "Theek hai" + nameSuffix + ", Google open kar rahi hoon.";
        }
        return "Opening Google" + nameSuffix + ".";
    }

    private static String formatGoogleSearchMsg(String lang, String query) {
        if (LanguageUtils.LANG_HINDI.equals(lang)) {
            return "Google पर \"" + query + "\" खोज रही हूँ।";
        } else if (LanguageUtils.LANG_HINGLISH.equals(lang)) {
            return "Google par \"" + query + "\" search kar rahi hoon.";
        }
        return "Searching Google for \"" + query + "\".";
    }

    private static String formatAppOpenMsg(String lang, String appLabel, String nameSuffix) {
        if (LanguageUtils.LANG_HINDI.equals(lang)) {
            return "जी" + nameSuffix + ", " + appLabel + " खोल रही हूँ।";
        } else if (LanguageUtils.LANG_HINGLISH.equals(lang)) {
            return "Sure" + nameSuffix + ", " + appLabel + " launch kar rahi hoon.";
        }
        return "Launching " + appLabel + nameSuffix + ".";
    }

    private static String formatSettingsPanelMsg(String lang, String panelName, String nameSuffix) {
        if (LanguageUtils.LANG_HINDI.equals(lang)) {
            return "मैं सीधे सिस्टम टॉगल नहीं बदल सकती" + nameSuffix + ", इसलिए आपके लिए " + panelName + " खोल रही हूँ।";
        } else if (LanguageUtils.LANG_HINGLISH.equals(lang)) {
            return "Main direct system toggle change nahi kar sakti" + nameSuffix + ", isliye aapke liye " + panelName + " khol rahi hoon.";
        }
        return "I cannot toggle system hardware directly, so I am opening " + panelName + " for you" + nameSuffix + ".";
    }

    private static String appNotFoundMsg(String lang, String appName) {
        if (LanguageUtils.LANG_HINDI.equals(lang)) {
            return "माफ़ कीजिए, आपके फ़ोन में \"" + appName + "\" ऐप नहीं मिला।";
        } else if (LanguageUtils.LANG_HINGLISH.equals(lang)) {
            return "Sorry, aapke phone mein \"" + appName + "\" app install nahi mila.";
        }
        return "I couldn't find \"" + appName + "\" installed on this device.";
    }
}
