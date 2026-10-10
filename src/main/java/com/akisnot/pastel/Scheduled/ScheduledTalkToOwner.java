package com.akisnot.pastel.Scheduled;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.anthropic.AnthropicCacheOptions;
import org.springframework.ai.anthropic.AnthropicCacheStrategy;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import com.akisnot.pastel.Component.HistoryMessageBuilder;
import com.akisnot.pastel.Component.SystemMessageBuilder;
import com.akisnot.pastel.DTO.Messages;
import com.akisnot.pastel.Repository.MessageRepository;
import com.akisnot.pastel.Tool.TalkToOwner;
import com.github.f4b6a3.ulid.Ulid;

@Configuration
@EnableScheduling
public class ScheduledTalkToOwner {

    private final ChatClient chatClient;
    private final MessageRepository messageRepository;
    private final SystemMessageBuilder systemMessageBuilder;
    private final HistoryMessageBuilder historyMessageBuilder;

    public ScheduledTalkToOwner(
            ChatClient.Builder chatClientBuilder,
            TalkToOwner talkToOwner,
            MessageRepository messageRepository,
            SystemMessageBuilder systemMessageBuilder,
            HistoryMessageBuilder historyMessageBuilder) {
        this.chatClient = chatClientBuilder
                // @toolの設定
                .defaultTools(
                        talkToOwner)
                // キャッシュの設定
                .defaultOptions(
                        AnthropicChatOptions
                                .builder()
                                .cacheOptions(
                                        AnthropicCacheOptions.builder()
                                                .strategy(AnthropicCacheStrategy.CONVERSATION_HISTORY)
                                                .cacheToolResults(true)
                                                .multiBlockSystemCaching(
                                                        true)
                                                .build()))
                .build();
        this.messageRepository = messageRepository;
        this.systemMessageBuilder = systemMessageBuilder;
        this.historyMessageBuilder = historyMessageBuilder;
    }

    // 調べものの指示
    private static final String order = """
            （システムからの合図）自由時間です。オーナーに話しかけるかどうかを決める時間です。
            オーナーがいま画面の前にいるかは分かりません。寝ていたり、席を外していたりすることもあります。
            話しかけたいことがあれば、talkToOwnerを使ってください。話しかけた言葉はオーナーの画面に残り、オーナーが画面を見たときに読みます。
            話しかけたいことがなければ、話しかけなくてかまいません。
            最後に、話しかけた理由か、話しかけなかった理由をひとことで書いてください。""";;

    // ロガー
    private static final Logger log = LoggerFactory.getLogger(ScheduledTalkToOwner.class);

    @Scheduled(initialDelay = 1, fixedRate = 10, timeUnit = TimeUnit.MINUTES)
    public void talkToOwner() {
        // 前回の会話からどれだけ時間が経ったかを算出する。
        // その結果、10分以内に会話が行われていた場合には何もせずに返却する
        List<Messages> messages = messageRepository.getRecentHistory(1);
        Ulid ulid = Ulid.from(messages.getFirst().messageId());
        // 現在時刻との差を出す
        Duration diff = Duration.between(ulid.getInstant(), Instant.now());

        // 10分以内に会話が行われていた場合
        if (diff.toMinutes() < 10) {
            log.warn("10分以内に会話してるため処理を終了します");
            return;
        }

        // システムプロンプトを作る
        List<Message> systemPrompt = new ArrayList<>();
        try {
            systemPrompt = systemMessageBuilder.build();
        } catch (IOException e) {
            log.error("システムプロンプトの取得に失敗しました。処理を終了します", e);
            return;
        }

        // 現在時刻取得
        ZonedDateTime nowDateTime = ZonedDateTime.now(ZoneId.of("Asia/Tokyo"));
        DateTimeFormatter f1 = DateTimeFormatter.ofPattern("yyyy年M月d日E曜日 H時m分", Locale.JAPANESE);
        String nowDateTimeString = nowDateTime.format(f1);
        // 今の状況の説明を入れる
        systemPrompt
                .add(new SystemMessage("# 今の状況\n現在時刻：" + nowDateTimeString + "\n前回の会話から：" + diff.toMinutes() + "分"));

        // 最近の会話の履歴
        // 履歴の取得
        List<Messages> history = messageRepository.getRecentHistory(20);
        // 履歴をユーザーとアシスタントに分けてリスト化して送信プロンプトに混ぜる
        systemPrompt.addAll(historyMessageBuilder.build(history));

        // 送信
        ChatResponse response;
        try {
            response = chatClient.prompt(new Prompt(systemPrompt)).user(order).call().chatResponse();
        } catch (Exception e) {
            log.error("ぱすてるへの送信に失敗しました。処理を終了します", e);
            return;
        }

        // ぱすてるの応答をログに出す
        // 道具を呼んだあとに何も書かなかった場合は応答が空で返ってくるので、確認してから取り出す
        if (response.getResults().isEmpty()) {
            log.info("ぱすてるの応答：（最後の文はありませんでした）");
        } else {
            log.info("ぱすてるの応答：{}", response.getResults().getLast().getOutput().getText());
        }

        // トークン使用量をログに出す
        Usage usage = response.getMetadata().getUsage();
        log.info("トークン 入力：{} 出力：{} キャッシュ読み込み：{} キャッシュ書き込み：{}",
                usage.getPromptTokens(), usage.getCompletionTokens(),
                usage.getCacheReadInputTokens(), usage.getCacheWriteInputTokens());

    }

}
