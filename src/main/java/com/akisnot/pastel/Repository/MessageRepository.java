package com.akisnot.pastel.Repository;

import java.util.Collections;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.akisnot.pastel.DTO.PastelMessage;

@Repository
public class MessageRepository {

    // JDBC Client
    JdbcClient jdbcClient;

    public MessageRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    // 履歴の取得メソッド
    public List<PastelMessage> getRecentHistory(int numberOfHistory) {

        // 履歴２０件取得する
        List<PastelMessage> messages = jdbcClient.sql("select * from messages order by message_id DESC limit ?")
                .param(numberOfHistory).query(PastelMessage.class).list();

        // 古い順に戻す
        Collections.reverse(messages);

        return messages;

    }

    // 回答・返答の保存メソッド
    public void save(PastelMessage insertData) {

        // 保存
        jdbcClient.sql("""
                INSERT INTO messages (
                    user_id, message_id, role, content,
                    input_tokens, output_tokens, use_model, pastel_version
                ) VALUES (
                    :userId, :messageId, :role, :content,
                    :inputTokens, :outputTokens, :useModel, :pastelVersion
                )
                """)
                .paramSource(insertData)
                .update();
    }
}
