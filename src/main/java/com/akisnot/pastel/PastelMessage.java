package com.akisnot.pastel;

public class PastelMessage {
    
    private String userId; //パーティションキー
    private String messageId; // ソートキー(ULID)
    private String role; // "user" か "assistant"
    private String content; // 本文
    private String createdAt; // 作成時刻
    private Integer inputTokens; // 入力トークン数
    private Integer outputTokens; // 出力トークン数

    public String getUserId() {
        return userId;
    }
    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getMessageId() {
        return messageId;
    }
    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }
    public String getRole() {
        return role;
    }
    public void setRole(String role) {
        this.role = role;
    }
    public String getContent() {
        return content;
    }
    public void setContent(String content) {
        this.content = content;
    }
    public String getCreatedAt() {
        return createdAt;
    }
    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
    public Integer getInputTokens() {
        return inputTokens;
    }
    public void setInputTokens(Integer inputTokens) {
        this.inputTokens = inputTokens;
    }
    public Integer getOutputTokens() {
        return outputTokens;
    }
    public void setOutputTokens(Integer outputTokens) {
        this.outputTokens = outputTokens;
    }

    
}
