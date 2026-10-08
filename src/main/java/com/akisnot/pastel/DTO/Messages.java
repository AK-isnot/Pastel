package com.akisnot.pastel.DTO;

import com.github.f4b6a3.ulid.Ulid;
import com.github.f4b6a3.ulid.UlidCreator;

public record Messages(
        String userId, // パーティションキー
        String messageId, // ソートキー（ULID）
        String role, // "user" か "assistant"
        String content, // 本文
        String createdAt, // 作成時刻
        String useModel, // 使用モデル
        String pastelVersion, // pastel.mdのバージョン
        String searchQueries // pastelが検索した文字列
) {

    // 会話の記録用にMessageを作るメソッド
    public static Messages makeOfUser(String content, String pastelMdVersion) {
        return new Messages("Owner", newId(), "user", content, null, null, pastelMdVersion, null);
    }

    public static Messages makeOfAssistant(String content, String model, String pastelMdVersion, String searchQueries) {
        return new Messages("Owner", newId(), "assistant", content, null, model, pastelMdVersion, searchQueries);
    }

    // message_id作成メソッド
    private static String newId() {
        // UULD取得
        Ulid ulid = UlidCreator.getMonotonicUlid();
        return ulid.toString();
    }

}
