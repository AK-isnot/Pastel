package com.akisnot.pastel.Repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.akisnot.pastel.DTO.ToolCalls;

@Repository
public class ToolCallsRepository {
    // JDBC Client
    JdbcClient jdbcClient;

    public ToolCallsRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    // 回答・返答の保存メソッド
    public void save(ToolCalls toolCalls) {

        // 保存
        jdbcClient.sql("""
                INSERT INTO tool_calls (
                    call_id, message_id, tool_name,
                    target, succeeded
                ) VALUES (
                    :callId, :messageId, :toolName,
                    :target, :succeeded
                )
                """)
                .paramSource(toolCalls)
                .update();
    }
}
