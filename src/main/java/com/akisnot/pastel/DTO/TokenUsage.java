package com.akisnot.pastel.DTO;


public record TokenUsage(
    String messageId,
    Integer inputTokens,
    Integer outputTokens,
    Long cacheReadTokens,
    Long cacheWriteTokens
) {
    
}
