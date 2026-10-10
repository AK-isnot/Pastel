package com.akisnot.pastel.Tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.stringtemplate.v4.compiler.CodeGenerator.primary_return;

import com.akisnot.pastel.DTO.Messages;
import com.akisnot.pastel.DTO.ToolCalls;
import com.akisnot.pastel.Repository.MessageRepository;
import com.akisnot.pastel.Repository.ToolCallsRepository;

@Component
public class TalkToOwner {

    // PASTEL.mdのバージョンを読み込む
    @Value("${pastel.md-version}")
    private String pastelMdVersion;

    // modelの定数（プロパティから読み込むと既存のDBと値がずれるため）
    private static final String ModelVersion = "claude-sonnet-4-6";

    private final MessageRepository messageRepository;
    private final ToolCallsRepository toolCallsRepository;

    public TalkToOwner(
            MessageRepository messageRepository,
            ToolCallsRepository toolCallsRepository) {
        this.toolCallsRepository = toolCallsRepository;
        this.messageRepository = messageRepository;
    }

    // ロガー
    private static final Logger log = LoggerFactory.getLogger(TalkToOwner.class);

    @Tool(description = "オーナーに話しかけることが出来ます")
    public String talkToOwner(
            @ToolParam(description = "オーナーに話しかける内容") String content) {

        // ぱすてるが話しかける内容を保存する
        Messages message= Messages.makeOfAssistant(content, ModelVersion, pastelMdVersion, null);
        try {
            messageRepository.save(message);
        } catch (DataAccessException e) {
            log.error("DB保存に失敗しました", e);
            // Toolが使用されたことを記録する
            toolCallsRepository.save(ToolCalls.make(message.messageId(), "talkToOwner",
                    content.substring(0, Math.min(content.length(), 10)), 0));
            return "オーナーに話しかけることに失敗しました";
        }

        // Toolが使用されたことを記録する
        toolCallsRepository.save(ToolCalls.make(message.messageId(), "talkToOwner",
                content.substring(0, Math.min(content.length(), 10)), 1));

        return "オーナーに話しかけることが出来ました";

    }
}
