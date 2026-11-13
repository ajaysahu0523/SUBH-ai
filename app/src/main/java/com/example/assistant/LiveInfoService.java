package com.example.assistant;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Xml;

import org.json.JSONArray;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fetches real-time Weather (Open-Meteo API, no API key required) and News headlines
 * (Google News RSS) over HTTPS with proper offline & timeout handling.
 */
public class LiveInfoService {

    public interface Callback {
        void onResult(String text, String languageCode, String modeTag);
        void onError(String errorMessage, String languageCode);
    }

    private static final Pattern CITY_PATTERN = Pattern.compile(
            "(?i)(?:weather|mausam|मौसम)\\s+(?:in|of|at|for|mein|का|की)?\\s*([\\p{L}][\\p{L}\\s]{1,30})"
    );
    private static final Pattern NEWS_TOPIC_PATTERN = Pattern.compile(
            "(?i)(?:news|khabar|समाचार)\\s+(?:about|on|for|of|par|के बारे में)?\\s*([\\p{L}\\d\\s]{2,35})"
    );

    private final Context appContext;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public LiveInfoService(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public boolean canHandle(String text) {
        if (text == null) return false;
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("weather") || lower.contains("temperature")
                || lower.contains("mausam") || lower.contains("मौसम")
                || lower.contains("news") || lower.contains("headlines")
                || lower.contains("khabar") || lower.contains("समाचार");
    }

    public void handleQuery(String text, Callback callback) {
        String lang = LanguageUtils.detectLanguage(text);
        if (!NetworkUtils.isNetworkAvailable(appContext)) {
            String offline = LanguageUtils.LANG_HINDI.equals(lang)
                    ? "मौसम या समाचार प्राप्त करने के लिए इंटरनेट कनेक्शन आवश्यक है।"
                    : "An active internet connection is required to fetch live weather or news.";
            mainHandler.post(() -> callback.onError(offline, lang));
            return;
        }

        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("weather") || lower.contains("temperature")
                || lower.contains("mausam") || lower.contains("मौसम")) {
            String city = extractCity(text);
            fetchWeather(city, lang, callback);
        } else {
            String topic = extractNewsTopic(text);
            fetchNews(topic, lang, callback);
        }
    }

    private String extractCity(String text) {
        Matcher m = CITY_PATTERN.matcher(text);
        if (m.find() && m.group(1) != null) {
            String candidate = m.group(1).replaceAll("(?i)\\b(today|now|aaj|आज|kaisa hai|कैसा है)\\b", "").trim();
            if (!candidate.isEmpty()) return candidate;
        }
        return "New Delhi";
    }

    private String extractNewsTopic(String text) {
        Matcher m = NEWS_TOPIC_PATTERN.matcher(text);
        if (m.find() && m.group(1) != null) {
            String candidate = m.group(1).replaceAll("(?i)\\b(today|latest|aaj|आज|ki|ताज़ा)\\b", "").trim();
            if (!candidate.isEmpty()) return candidate;
        }
        return "";
    }

    private void fetchWeather(String city, String lang, Callback callback) {
        executor.execute(() -> {
            try {
                String geoUrl = "https://geocoding-api.open-meteo.com/v1/search?name="
                        + Uri.encode(city) + "&count=1&language=en&format=json";
                JSONObject geoJson = new JSONObject(httpGet(geoUrl));
                JSONArray results = geoJson.optJSONArray("results");
                if (results == null || results.length() == 0) {
                    String notFound = LanguageUtils.LANG_HINDI.equals(lang)
                            ? "\"" + city + "\" के लिए मौसम की जानकारी नहीं मिली।"
                            : "Could not find weather coordinates for \"" + city + "\".";
                    mainHandler.post(() -> callback.onError(notFound, lang));
                    return;
                }
                JSONObject place = results.getJSONObject(0);
                double lat = place.getDouble("latitude");
                double lon = place.getDouble("longitude");
                String resolvedName = place.optString("name", city);
                String country = place.optString("country", "");

                String weatherUrl = String.format(
                        Locale.US,
                        "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current=temperature_2m,relative_humidity_2m,wind_speed_10m,weather_code",
                        lat, lon
                );
                JSONObject weatherJson = new JSONObject(httpGet(weatherUrl));
                JSONObject current = weatherJson.getJSONObject("current");
                double tempC = current.getDouble("temperature_2m");
                int humidity = current.getInt("relative_humidity_2m");
                double windKmh = current.getDouble("wind_speed_10m");
                int code = current.getInt("weather_code");
                String condition = describeWeatherCode(code, lang);

                String locationLabel = country.isEmpty() ? resolvedName : (resolvedName + ", " + country);
                String reply;
                if (LanguageUtils.LANG_HINDI.equals(lang)) {
                    reply = String.format(
                            Locale.getDefault(),
                            "%s में अभी मौसम %s है। तापमान %.1f°C, नमी %d%% और हवा की गति %.1f km/h है। (Open-Meteo)",
                            locationLabel, condition, tempC, humidity, windKmh
                    );
                } else if (LanguageUtils.LANG_HINGLISH.equals(lang)) {
                    reply = String.format(
                            Locale.getDefault(),
                            "%s mein abhi weather %s hai. Temperature %.1f°C, humidity %d%% aur wind %.1f km/h chal rahi hai. (Open-Meteo)",
                            locationLabel, condition, tempC, humidity, windKmh
                    );
                } else {
                    reply = String.format(
                            Locale.US,
                            "Current weather in %s: %s, %.1f°C, %d%% humidity, wind %.1f km/h. (Source: Open-Meteo)",
                            locationLabel, condition, tempC, humidity, windKmh
                    );
                }
                mainHandler.post(() -> callback.onResult(reply, lang, "WEATHER"));
            } catch (Exception e) {
                String err = LanguageUtils.LANG_HINDI.equals(lang)
                        ? "मौसम की जानकारी लाने में नेटवर्क त्रुटि हुई। कृपया पुनः प्रयास करें।"
                        : "Unable to fetch live weather right now. Please try again.";
                mainHandler.post(() -> callback.onError(err, lang));
            }
        });
    }

    private void fetchNews(String topic, String lang, Callback callback) {
        executor.execute(() -> {
            try {
                String hl = LanguageUtils.LANG_HINDI.equals(lang) ? "hi" : "en-IN";
                String rssUrl = topic.isEmpty()
                        ? "https://news.google.com/rss?hl=" + hl + "&gl=IN&ceid=IN:" + (hl.equals("hi") ? "hi" : "en")
                        : "https://news.google.com/rss/search?q=" + Uri.encode(topic) + "&hl=" + hl + "&gl=IN&ceid=IN:en";

                List<String> headlines = parseRssTitles(rssUrl, 4);
                if (headlines.isEmpty()) {
                    String empty = LanguageUtils.LANG_HINDI.equals(lang)
                            ? "अभी कोई ताज़ा समाचार शीर्षक उपलब्ध नहीं हुआ।"
                            : "No live news headlines were returned right now.";
                    mainHandler.post(() -> callback.onError(empty, lang));
                    return;
                }
                StringBuilder sb = new StringBuilder();
                if (LanguageUtils.LANG_HINDI.equals(lang)) {
                    sb.append("ताज़ा मुख्य समाचार:\n");
                } else if (LanguageUtils.LANG_HINGLISH.equals(lang)) {
                    sb.append("Aaj ki top news headlines:\n");
                } else {
                    sb.append("Latest top headlines:\n");
                }
                for (int i = 0; i < headlines.size(); i++) {
                    sb.append(i + 1).append(". ").append(headlines.get(i));
                    if (i < headlines.size() - 1) sb.append("\n");
                }
                String finalReply = sb.toString();
                mainHandler.post(() -> callback.onResult(finalReply, lang, "NEWS"));
            } catch (Exception e) {
                String err = LanguageUtils.LANG_HINDI.equals(lang)
                        ? "समाचार लाने में नेटवर्क समस्या आई। कृपया पुनः प्रयास करें।"
                        : "Could not load live news headlines right now. Please check your connection.";
                mainHandler.post(() -> callback.onError(err, lang));
            }
        });
    }

    private String httpGet(String urlString) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlString).openConnection();
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(8000);
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", "SUBH-AI-Android/1.0");
        try (InputStream is = conn.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        } finally {
            conn.disconnect();
        }
    }

    private List<String> parseRssTitles(String urlString, int maxItems) throws Exception {
        List<String> titles = new ArrayList<>();
        HttpURLConnection conn = (HttpURLConnection) new URL(urlString).openConnection();
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(8000);
        conn.setRequestProperty("User-Agent", "SUBH-AI-Android/1.0");
        try (InputStream is = conn.getInputStream()) {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(is, "UTF-8");
            boolean insideItem = false;
            int eventType = parser.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT && titles.size() < maxItems) {
                if (eventType == XmlPullParser.START_TAG) {
                    String name = parser.getName();
                    if ("item".equalsIgnoreCase(name)) {
                        insideItem = true;
                    } else if (insideItem && "title".equalsIgnoreCase(name)) {
                        String title = parser.nextText();
                        if (title != null && !title.trim().isEmpty()) {
                            titles.add(title.trim());
                        }
                    }
                } else if (eventType == XmlPullParser.END_TAG && "item".equalsIgnoreCase(parser.getName())) {
                    insideItem = false;
                }
                eventType = parser.next();
            }
        } finally {
            conn.disconnect();
        }
        return titles;
    }

    private String describeWeatherCode(int code, String lang) {
        boolean hi = LanguageUtils.LANG_HINDI.equals(lang);
        if (code == 0) return hi ? "साफ़ आसमान" : "Clear sky";
        if (code <= 3) return hi ? "हल्के बादल" : "Partly cloudy";
        if (code == 45 || code == 48) return hi ? "कोहरा" : "Foggy";
        if (code >= 51 && code <= 67) return hi ? "बारिश" : "Rainy";
        if (code >= 71 && code <= 77) return hi ? "बर्फ़बारी" : "Snow";
        if (code >= 80 && code <= 82) return hi ? "तेज़ बौछारें" : "Rain showers";
        if (code >= 95) return hi ? "आंधी-तूफ़ान" : "Thunderstorm";
        return hi ? "सामान्य" : "Moderate conditions";
    }

    public void shutdown() {
        executor.shutdownNow();
    }
}
