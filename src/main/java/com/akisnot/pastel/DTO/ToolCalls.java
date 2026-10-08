package com.akisnot.pastel.DTO;

import com.github.f4b6a3.ulid.Ulid;
import com.github.f4b6a3.ulid.UlidCreator;

public record ToolCalls(
        String callId,
        String messageId,
        String toolName,
        String target,
        int succeeded,
        String createdAt // 作成日時
) {
    // ファクトリーメソッド
    public static ToolCalls make(String messageId, String toolName, String target, int succeeded) {
        return new ToolCalls(newId(), messageId, toolName, target, succeeded, null);
    }

    // callId作成メソッド
    private static String newId() {
        // UULD取得
        Ulid ulid = UlidCreator.getMonotonicUlid();
        return ulid.toString();
    }
}
