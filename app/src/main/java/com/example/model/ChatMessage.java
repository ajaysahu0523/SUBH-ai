package com.example.model;

public class ChatMessage {
    public static final int TYPE_USER = 0;
    public static final int TYPE_ASSISTANT = 1;

    private final long id;
    private final String text;
    private final boolean isUser;
    private final String languageCode;
    private final long timestamp;
    private final boolean isSampleAi;
    private final String modeTag;

    public ChatMessage(
            long id,
            String text,
            boolean isUser,
            String languageCode,
            long timestamp,
            boolean isSampleAi,
            String modeTag
    ) {
        this.id = id;
        this.text = text;
        this.isUser = isUser;
        this.languageCode = languageCode;
        this.timestamp = timestamp;
        this.isSampleAi = isSampleAi;
        this.modeTag = modeTag;
    }

    public long getId() {
        return id;
    }

    public String getText() {
        return text;
    }

    public boolean isUser() {
        return isUser;
    }

    public String getLanguageCode() {
        return languageCode;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public boolean isSampleAi() {
        return isSampleAi;
    }

    public String getModeTag() {
        return modeTag;
    }
}
