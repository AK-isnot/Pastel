package com.akisnot.pastel.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.akisnot.pastel.DTO.PastelMessage;
import com.akisnot.pastel.Repository.MessageRepository;

@Service
public class ChatService {

    @Value("${pastel.md-version}")
    private String pastelMdVersion;

    private final MessageRepository messageRepository;
    private final ChatModel chatModel;

    public ChatService(MessageRepository messageRepository, ChatModel chatModel) {
        this.messageRepository = messageRepository;
        this.chatModel = chatModel;
    }

    public String chat(String inputText) {

        // システムプロンプト
        String pastelMd;
        try (InputStream in = ChatService.class.getResourceAsStream("/PASTEL.md")) {
            pastelMd = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "ERROR: failed to load PASTEL.md:";
        } catch (Exception e) {
            return "An unexpected error occurred.";
        }

        // 現在時刻取得
        ZonedDateTime nowDateTime = ZonedDateTime.now(ZoneId.of("Asia/Tokyo"));
        DateTimeFormatter f1 = DateTimeFormatter.ofPattern("yyyy年M月d日E曜日 H時m分", Locale.JAPANESE);

        // 履歴の取得
        List<PastelMessage> history = getHistory(20);

        // 履歴をつなげて渡す
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(pastelMd + "\n現在時刻: " + nowDateTime.format(f1)));
        for (PastelMessage m : history) {
            messages.add(m.role().equals("user")
                    ? new UserMessage(m.content())
                    : new AssistantMessage(m.content()));
        }
        messages.add(new UserMessage(inputText));

        // 送信内容の保存
        messageRepository.save(PastelMessage.makeOfUser(inputText, pastelMdVersion));

        // 送信
        ChatResponse response = chatModel.call(new Prompt(messages));

        // 返却内容の保存
        // 返却本文
        String content = response.getResults().getLast().getOutput().getText();
        // 使ったモデル
        String model = response.getMetadata().getModel();
        // トークン使用量
        Usage usage = response.getMetadata().getUsage();
        Integer promptTokens = usage.getPromptTokens(); // 入力
        Integer completionTokens = usage.getCompletionTokens(); // 出力
        // 保存処理
        messageRepository
                .save(PastelMessage.makeOfAssistant(content, promptTokens, completionTokens, model, pastelMdVersion));

        // 返却
        return content;

    }

    // 履歴の返却メソッド
    public List<PastelMessage> getHistory(int numberOfHistory) {
        return messageRepository.getRecentHistory(numberOfHistory);
    }

}
