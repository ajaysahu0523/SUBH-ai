package com.example.model;

public class SavedNote {
    private final long id;
    private final String content;
    private final long createdAt;

    public SavedNote(long id, String content, long createdAt) {
        this.id = id;
        this.content = content;
        this.createdAt = createdAt;
    }

    public long getId() {
        return id;
    }

    public String getContent() {
        return content;
    }

    public long getCreatedAt() {
        return createdAt;
    }
}
