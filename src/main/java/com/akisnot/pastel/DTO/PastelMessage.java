package com.akisnot.pastel.DTO;

import org.stringtemplate.v4.compiler.CodeGenerator.region_return;

import com.github.f4b6a3.ulid.Ulid;
import com.github.f4b6a3.ulid.UlidCreator;

public record PastelMessage(
        String userId, // パーティションキー
        String messageId, // ソートキー（ULID）
        String role, // "user" か "assistant"
        String content, // 本文
        String createdAt, // 作成時刻
        Integer inputTokens, // 入力トークン数
        Integer outputTokens, // 出力トークン数
        String useModel // 使用モデル
) {

    // 会話の記録用にPastelMessageを作るメソッド
    public static PastelMessage makeOfUser(String content) {
        return new PastelMessage("Owner", newId(), "user", content, null,
                null, null, null);
    }

    public static PastelMessage makeOfAssistant(String content,
            Integer inputTokens, Integer outputTokens, String model) {
        return new PastelMessage("Owner", newId(), "assistant", content, null,
                inputTokens, outputTokens, model);
    }

    // message_id作成メソッド
    private static String newId() {
        // UULD取得
        Ulid ulid = UlidCreator.getMonotonicUlid();
        return ulid.toString();
    }

}
