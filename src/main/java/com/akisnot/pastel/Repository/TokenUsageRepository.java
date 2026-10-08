package com.akisnot.pastel.Repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.akisnot.pastel.DTO.TokenUsage;

@Repository
public class TokenUsageRepository {
    // JDBC Client
    JdbcClient jdbcClient;

    public TokenUsageRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    // 回答・返答の保存メソッド
    public void save(TokenUsage tokenUsage) {

        // 保存
        jdbcClient.sql("""
                INSERT INTO token_usages (
                    message_id, input_tokens, output_tokens,
                    cache_read_tokens, cache_write_tokens
                ) VALUES (
                    :messageId, :inputTokens, :outputTokens,
                    :cacheReadTokens, :cacheWriteTokens
                )
                """)
                .paramSource(tokenUsage)
                .update();
    }
}
