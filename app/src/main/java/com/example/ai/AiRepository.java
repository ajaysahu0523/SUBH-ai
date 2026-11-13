package com.example.ai;

import com.example.model.ChatMessage;

import java.util.List;

public interface AiRepository {
    interface AiCallback {
        void onSuccess(String responseText, String languageCode, boolean isSampleResponse);
        void onError(String errorMessage, String languageCode);
    }

    void generateResponse(
            String userMessage,
            String detectedLanguage,
            List<ChatMessage> conversationHistory,
            AiCallback callback
    );

    void shutdown();
}
