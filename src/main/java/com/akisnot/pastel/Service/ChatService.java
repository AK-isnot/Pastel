package com.akisnot.pastel.Service;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.anthropic.AnthropicCacheOptions;
import org.springframework.ai.anthropic.AnthropicCacheStrategy;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.akisnot.pastel.Component.HistoryMessageBuilder;
import com.akisnot.pastel.Component.KeepTextToolCallingAdvisor;
import com.akisnot.pastel.Component.SystemMessageBuilder;
import com.akisnot.pastel.DTO.Messages;
import com.akisnot.pastel.DTO.TokenUsage;
import com.akisnot.pastel.Repository.MessageRepository;
import com.akisnot.pastel.Repository.TokenUsageRepository;
import com.akisnot.pastel.Scheduled.ScheduledReflectOn;
import com.akisnot.pastel.Tool.ReadResearchNote;
import com.akisnot.pastel.Tool.WebSearchTavily;
import com.akisnot.pastel.Tool.WriteMemoryNote;
import com.akisnot.pastel.Tool.WriteResearchNote;
import com.github.f4b6a3.ulid.Ulid;

@Service
public class ChatService {

    @Value("${pastel.md-version}")
    private String pastelMdVersion;

    private final MessageRepository messageRepository;
    private final TokenUsageRepository tokenUsageRepository;
    private final ChatClient chatClient;
    private final SystemMessageBuilder systemMessageBuilder;
    private final HistoryMessageBuilder historyMessageBuilder;

    public ChatService(
            MessageRepository messageRepository,
            TokenUsageRepository tokenUsageRepository,
            ChatClient.Builder chatClientBuilder,
            SystemMessageBuilder systemMessageBuilder,
            WebSearchTavily webSearchTavily,
            WriteMemoryNote writeMemoryNote,
            WriteResearchNote writeResearchNote,
            ReadResearchNote readResearchNote,
            HistoryMessageBuilder historyMessageBuilder,
            KeepTextToolCallingAdvisor keepTextToolCallingAdvisor) {
        this.messageRepository = messageRepository;
        this.tokenUsageRepository = tokenUsageRepository;
        this.historyMessageBuilder = historyMessageBuilder;
        this.chatClient = chatClientBuilder
                // @toolの設定
                .defaultTools(
                        webSearchTavily,
                        writeMemoryNote,
                        writeResearchNote,
                        readResearchNote)
                // ツールの往復を回す部品を、道具と一緒に書かれた返事の文を拾える版に差し替える
                .defaultAdvisors(keepTextToolCallingAdvisor)
                // キャッシュの設定
                .defaultOptions(
                        AnthropicChatOptions
                                .builder()
                                .cacheOptions(
                                        AnthropicCacheOptions.builder()
                                                .strategy(AnthropicCacheStrategy.CONVERSATION_HISTORY)
                                                .cacheToolResults(true).multiBlockSystemCaching(true).build()))
                .build();
        this.systemMessageBuilder = systemMessageBuilder;
    }

    // ロガー
    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    public String chat(String inputText) throws IOException {

        // ロガー用に処理開始日時を取っておく
        Instant startTime = Instant.now();

        // システムプロンプトを取得（PASTEL.md,メモリ,調べもの一覧のリスト）
        List<Message> messages = new ArrayList<>();
        messages.addAll(systemMessageBuilder.build());

        // 現在時刻取得
        ZonedDateTime nowDateTime = ZonedDateTime.now(ZoneId.of("Asia/Tokyo"));
        DateTimeFormatter f1 = DateTimeFormatter.ofPattern("yyyy年M月d日E曜日 H時m分", Locale.JAPANESE);

        // 履歴の取得
        List<Messages> history = getHistory(20);

        // 前回の会話からどれだけの時間が経ったのか計算してプロンプトに含める
        // 最後の会話時間をUlidから計算するために、履歴から取得する
        String lastMessageUlidString = history.getLast().messageId();
        // 初回は前回の会話の時間が存在しないので分岐する
        String statusText;
        if (lastMessageUlidString.isEmpty()) {
            statusText = "これが初めての起動です\n";
        } else {
            Ulid lastMessageUlid = Ulid.from(lastMessageUlidString);
            Instant lastMessageTime = lastMessageUlid.getInstant();
            // 今回の時間と前回の時間で引き算をして、どれだけ時間が経ったか計算する
            Duration elapsedTime = Duration.between(lastMessageTime, nowDateTime);
            String elapsedTimeString = String
                    .format("%d日%d時間%d分",
                            elapsedTime.toDays(),
                            elapsedTime.toHoursPart(),
                            elapsedTime.toMinutesPart());
            statusText = "前回の会話から" + elapsedTimeString + "経過しました";
        }
        // 今の状況（現在時刻、前回の会話からどれだけ時間が経ったか）を渡す
        messages.add(new SystemMessage("# 今の状況\n" + "現在時刻：" + nowDateTime.format(f1) + "\n" + statusText));

        // 会話履歴をまとめて、システムメッセージに渡す
        messages.addAll(historyMessageBuilder.build(history));

        // 今回のメッセージを付け足す
        messages.add(new UserMessage(inputText));

        // 送信内容の保存
        Messages ownerSaveValue = Messages.makeOfUser(inputText, pastelMdVersion);
        messageRepository.save(ownerSaveValue);

        // ぱすてるが検索した場合の検索文字列を受け取るリスト
        List<String> searchQueries = new ArrayList<>();

        // 送信
        ChatResponse response = chatClient.prompt(new Prompt(messages))
                .toolContext(Map.of("queries", searchQueries, "messageId", ownerSaveValue.messageId()))
                .call().chatResponse();

        // 道具を呼んだあとに何も書かなかった場合は応答が空で返ってくる
        // 返事の文が手元にないので、エラーとして画面にエラーの吹き出しを出す
        if (response.getResults().isEmpty()) {
            throw new IllegalStateException("ぱすてるの返事が空でした（道具を呼んだあとに文がありませんでした）");
        }

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
        Messages saveValue;
        if (searchQueries.isEmpty()) {
            // 保存処理（検索がなかった場合）
            saveValue = Messages.makeOfAssistant(content, model,
                    pastelMdVersion, null);
            messageRepository
                    .save(saveValue);
        } else {
            // 保存処理（検索があった場合）
            saveValue = Messages.makeOfAssistant(content, model,
                    pastelMdVersion, searchQueries.toString());
            messageRepository
                    .save(saveValue);
        }

        // トークン使用量を保存する
        tokenUsageRepository.save(new TokenUsage(saveValue.messageId(), promptTokens, completionTokens,
                usage.getCacheReadInputTokens(), usage.getCacheWriteInputTokens()));

        // ログ出力
        log.info("１ターン終了 所要時間：" + Duration.between(startTime, Instant.now()).toMillis() + " ms");

        // 返却
        return content;

    }

    // 履歴の返却メソッド
    public List<Messages> getHistory(int numberOfHistory) {
        return messageRepository.getRecentHistory(numberOfHistory);
    }

}
