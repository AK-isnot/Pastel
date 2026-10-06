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
import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.akisnot.pastel.DTO.PastelMessage;
import com.akisnot.pastel.Repository.MessageRepository;
import com.akisnot.pastel.Tool.WebSearchTavily;

@Service
public class ChatService {

    @Value("${pastel.md-version}")
    private String pastelMdVersion;

    private final MessageRepository messageRepository;
    private final ChatClient chatClient;
    private final WebSearchTavily webSearchTavily;

    public ChatService(MessageRepository messageRepository, ChatClient.Builder chatClientBuilder,
            WebSearchTavily webSearchTavily) {
        this.messageRepository = messageRepository;
        this.chatClient = chatClientBuilder.defaultTools(webSearchTavily).build();
        this.webSearchTavily = webSearchTavily;
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
            if (m.role().equals("user")) {
                messages.add(new UserMessage(m.content()));
            } else {
                if (m.searchQueries() == null) {
                    messages.add(new AssistantMessage(m.content()));
                } else {
                    messages.add(
                            new AssistantMessage(m.content() + "\n［システム記録：この返事の前に" + m.searchQueries() + "で検索した］"));
                }

            }
        }
        messages.add(new UserMessage(inputText));

        // 送信内容の保存
        messageRepository.save(PastelMessage.makeOfUser(inputText, pastelMdVersion));

        // ぱすてるが検索した場合の検索文字列を受け取るリスト
        List<String> searchQueries = new ArrayList<>();

        // 送信
        ChatResponse response = chatClient.prompt(new Prompt(messages)).toolContext(Map.of("queries", searchQueries))
                .call().chatResponse();

        // 返却内容の保存
        // 返却本文
        String content = response.getResults().getLast().getOutput().getText();
        // 使ったモデル
        String model = response.getMetadata().getModel();
        // トークン使用量
        Usage usage = response.getMetadata().getUsage();
        Integer promptTokens = usage.getPromptTokens(); // 入力
        Integer completionTokens = usage.getCompletionTokens(); // 出力

        // ぱすてるが検索をしたかで分岐する
        if (searchQueries.isEmpty()) {
            // 保存処理（検索がなかった場合）
            messageRepository
                    .save(PastelMessage.makeOfAssistant(content, promptTokens, completionTokens, model,
                            pastelMdVersion, null));
        } else {
            // 保存処理（検索があった場合）
            messageRepository
                    .save(PastelMessage.makeOfAssistant(content, promptTokens, completionTokens, model,
                            pastelMdVersion, searchQueries.toString()));
        }

        // 返却
        return content;

    }

    // 履歴の返却メソッド
    public List<PastelMessage> getHistory(int numberOfHistory) {
        return messageRepository.getRecentHistory(numberOfHistory);
    }

}
