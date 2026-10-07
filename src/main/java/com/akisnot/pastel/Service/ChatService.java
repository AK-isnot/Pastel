package com.akisnot.pastel.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
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
import com.akisnot.pastel.Repository.VaultRepository;
import com.akisnot.pastel.Tool.ReadResearchNote;
import com.akisnot.pastel.Tool.WebSearchTavily;
import com.akisnot.pastel.Tool.WriteMemoryNote;
import com.akisnot.pastel.Tool.WriteResearchNote;
import com.github.f4b6a3.ulid.Ulid;

@Service
public class ChatService {

    @Value("${pastel.md-version}")
    private String pastelMdVersion;
    @Value("${pastel.vault.memory-dir}")
    private String pastelMemoryDir;
    @Value("${pastel.vault.research-dir}")
    private String pastelResearchDir;

    private final MessageRepository messageRepository;
    private final ChatClient chatClient;
    private final VaultRepository vaultRepository;

    public ChatService(
            MessageRepository messageRepository,
            ChatClient.Builder chatClientBuilder,
            WebSearchTavily webSearchTavily,
            VaultRepository vaultRepository,
            WriteMemoryNote writeMemoryNote,
            WriteResearchNote writeResearchNote,
            ReadResearchNote readResearchNote) {
        this.messageRepository = messageRepository;
        this.chatClient = chatClientBuilder
                .defaultTools(
                        webSearchTavily,
                        writeMemoryNote,
                        writeResearchNote,
                        readResearchNote)
                .build();
        this.vaultRepository = vaultRepository;
    }

    public String chat(String inputText) {

        // システムプロンプト
        String pastelMd;
        try (InputStream in = ChatService.class.getResourceAsStream("/PASTEL.md")) {
            pastelMd = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "ERROR: failed to load PASTEL.md.";
        } catch (Exception e) {
            return "ERROR: An unexpected error occurred.";
        }

        // 現在時刻取得
        ZonedDateTime nowDateTime = ZonedDateTime.now(ZoneId.of("Asia/Tokyo"));
        DateTimeFormatter f1 = DateTimeFormatter.ofPattern("yyyy年M月d日E曜日 H時m分", Locale.JAPANESE);

        // 履歴の取得
        List<PastelMessage> history = getHistory(20);

        // メモリの取得
        String memory;
        try {
            memory = vaultRepository.readVaultMemoryFile(Path.of(pastelMemoryDir));
        } catch (IOException e) {
            return "ERROR: failed to load memory files.";
        }

        // 調べたことの取得
        String researchIndex;
        try {
            researchIndex = vaultRepository.getFolderFileList(Path.of(pastelResearchDir), ".md");
        } catch (IOException e) {
            return "ERROR: failed to load research files.";
        }

        // 履歴をつなげて渡す
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(pastelMd + "\n現在時刻: " + nowDateTime.format(f1)));
        messages.add(new SystemMessage(memory));
        messages.add(new SystemMessage("# 調べものメモ\n" + researchIndex));

        // 前回の会話からどれだけの時間が経ったのか計算してプロンプトに含める
        // 最後の会話時間をUlidから計算するために、履歴から取得する
        String lastMessageUlidString = history.getLast().messageId();

        // 初回は前回の会話の時間が存在しないので分岐する
        if (lastMessageUlidString.isEmpty()) {
            messages.add(new SystemMessage("# これが初めての起動です\n"));
        } else if (!lastMessageUlidString.isEmpty()) {
            Ulid lastMessageUlid = Ulid.from(lastMessageUlidString);
            Instant lastMessageTime = lastMessageUlid.getInstant();
            // 今回の時間と前回の時間で引き算をして、どれだけ時間が経ったか計算する
            Duration elapsedTime = Duration.between(lastMessageTime, nowDateTime);
            String elapsedTimeString = String
                    .format("%d日%d時間%d分",
                            elapsedTime.toDays(),
                            elapsedTime.toHoursPart(),
                            elapsedTime.toMinutesPart());
            messages.add(new SystemMessage("# 前回の会話から\n" + elapsedTimeString + "経過しました"));
        }

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
        // 整形：［システム記録］以下を削除する
        content = content.replaceAll("［システム記録.*", "");

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
