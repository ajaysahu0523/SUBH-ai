package com.example.assistant;

import com.example.model.ChatMessage;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

public class ConversationMemory {
    private static final int MAX_RECENT_RESPONSES = 12;
    private static final int MAX_RECENT_TURNS = 16;

    public static class Turn {
        public final String userText;
        public final String assistantText;
        public final String topic;

        public Turn(String userText, String assistantText, String topic) {
            this.userText = userText;
            this.assistantText = assistantText;
            this.topic = topic;
        }
    }

    private final List<Turn> turns = new ArrayList<>();
    private final Deque<String> recentAssistantResponses = new ArrayDeque<>();
    private String currentTopic = "";
    private String previousTopic = "";
    private String lastUserQuery = "";
    private String lastAssistantReply = "";

    public synchronized void hydrateFromHistory(List<ChatMessage> savedMessages) {
        clear();
        if (savedMessages == null || savedMessages.isEmpty()) {
            return;
        }
        String pendingUser = null;
        for (ChatMessage msg : savedMessages) {
            if (msg.isUser()) {
                pendingUser = msg.getText();
            } else {
                String userPart = pendingUser != null ? pendingUser : "";
                String topic = inferTopicFromText(userPart);
                if (!userPart.isEmpty()) {
                    recordTurn(userPart, msg.getText(), topic);
                    pendingUser = null;
                } else {
                    lastAssistantReply = msg.getText();
                }
            }
        }
    }

    public synchronized void recordTurn(String userText, String assistantText, String detectedTopic) {
        if (detectedTopic != null && !detectedTopic.isEmpty() && !detectedTopic.equals(currentTopic)) {
            if (!currentTopic.isEmpty() && !"general".equals(currentTopic)) {
                previousTopic = currentTopic;
            }
            currentTopic = detectedTopic;
        }
        lastUserQuery = userText == null ? "" : userText;
        lastAssistantReply = assistantText == null ? "" : assistantText;
        turns.add(new Turn(lastUserQuery, lastAssistantReply, currentTopic));
        if (turns.size() > MAX_RECENT_TURNS) {
            turns.remove(0);
        }

        if (!lastAssistantReply.isEmpty()) {
            recentAssistantResponses.addLast(lastAssistantReply.trim());
            while (recentAssistantResponses.size() > MAX_RECENT_RESPONSES) {
                recentAssistantResponses.removeFirst();
            }
        }
    }

    public synchronized String getCurrentTopic() {
        return currentTopic;
    }

    public synchronized String getPreviousTopic() {
        return previousTopic;
    }

    public synchronized String getLastUserQuery() {
        return lastUserQuery;
    }

    public synchronized String getLastAssistantReply() {
        return lastAssistantReply;
    }

    public synchronized List<Turn> getTurns() {
        return new ArrayList<>(turns);
    }

    public synchronized boolean wasRecentlySaid(String candidate) {
        if (candidate == null) {
            return false;
        }
        String normalized = candidate.trim().toLowerCase(Locale.ROOT);
        for (String recent : recentAssistantResponses) {
            if (recent.toLowerCase(Locale.ROOT).equals(normalized)) {
                return true;
            }
        }
        return false;
    }

    public synchronized String pickNonRepeating(String[] options) {
        if (options == null || options.length == 0) {
            return "";
        }
        for (String option : options) {
            if (!wasRecentlySaid(option)) {
                return option;
            }
        }
        int idx = (turns.size() + recentAssistantResponses.size()) % options.length;
        return options[idx];
    }

    public synchronized void clear() {
        turns.clear();
        recentAssistantResponses.clear();
        currentTopic = "";
        previousTopic = "";
        lastUserQuery = "";
        lastAssistantReply = "";
    }

    public static String inferTopicFromText(String message) {
        if (message == null || message.trim().isEmpty()) return "";
        String lower = message.toLowerCase(Locale.ROOT);
        if (lower.contains("joke") || lower.contains("funny") || lower.contains("चुटकुला") || lower.contains("hasao")) {
            return "humor";
        }
        if (lower.contains("time") || lower.contains("date") || lower.contains("समय") || lower.contains("samay")) {
            return "time & schedule";
        }
        if (lower.contains("science") || lower.contains("space") || lower.contains("quantum")
                || lower.matches(".*\\b(ai|artificial intelligence)\\b.*")) {
            return "science & tech";
        }
        if (lower.contains("code") || lower.contains("java") || lower.contains("android")) {
            return "programming";
        }
        if (lower.contains("cricket") || lower.contains("music") || lower.contains("song")) {
            return "entertainment";
        }
        String trimmed = message.trim();
        return trimmed.length() > 32 ? trimmed.substring(0, 32) + "…" : trimmed;
    }
}
