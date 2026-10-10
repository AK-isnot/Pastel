package com.akisnot.pastel.Scheduled;

import com.akisnot.pastel.Repository.VaultRepository;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.anthropic.AnthropicCacheOptions;
import org.springframework.ai.anthropic.AnthropicCacheStrategy;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import com.akisnot.pastel.Component.HistoryMessageBuilder;
import com.akisnot.pastel.Component.SystemMessageBuilder;
import com.akisnot.pastel.DTO.Messages;
import com.akisnot.pastel.Repository.MessageRepository;
import com.akisnot.pastel.Tool.ReadResearchNote;
import com.akisnot.pastel.Tool.WriteMemoryNote;

@Configuration
@EnableScheduling
public class ScheduledReflectOn {

    @Value("${pastel.vault.dailyNote-dir}")
    private String pastelDailyNoteDir;

    private final VaultRepository vaultRepository;
    private final ChatClient chatClient;
    private final SystemMessageBuilder systemMessageBuilder;
    private final MessageRepository messageRepository;
    private final HistoryMessageBuilder historyMessageBuilder;

    public ScheduledReflectOn(

            ChatClient.Builder chatClientBuilder,
            ReadResearchNote readResearchNote,
            WriteMemoryNote writeMemoryNote,
            SystemMessageBuilder systemMessageBuilder,
            MessageRepository messageRepository,
            HistoryMessageBuilder historyMessageBuilder,
            VaultRepository vaultRepository

    ) {
        this.systemMessageBuilder = systemMessageBuilder;
        this.messageRepository = messageRepository;
        this.historyMessageBuilder = historyMessageBuilder;
        this.chatClient = chatClientBuilder
                // @toolの設定
                .defaultTools(
                        readResearchNote,
                        writeMemoryNote)
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
        this.vaultRepository = vaultRepository;
    }

    // ロガー
    private static final Logger log = LoggerFactory.getLogger(ScheduledReflectOn.class);

    // 指示
    private static final String order = """
            （システムからの合図）振り返りの時間です。
            メモリや会話の履歴、最近調べたことを見て、最近の振り返りをしましょう。
            気になったこと・話したいこと・調べたいことを考えて、メモリを更新してください。
            書き込むファイルは次です。
            調べたくなったこと → やりかけの話
            話したい事 → オーナーに話したいこと
            オーナーについて分かったこと → オーナーの事を書いたファイル
            自分の事→自分の事を書いたファイル
            必要なら新しいファイルを作成しましょう。
            最後に、やったことを日記のように出力しましょう。
            """;;

    @Scheduled(initialDelay = 2, fixedRate = 3, timeUnit = TimeUnit.HOURS)
    public void reflectOn() {
        log.info("自由時間：振り返りを開始します");

        // システムプロンプトを作る
        // PASTEL.md,メモリ,調べもの一覧のリストを取得
        List<Message> messages = new ArrayList<>();
        try {
            messages.addAll(systemMessageBuilder.build());
        } catch (IOException e) {
            log.error("システムプロンプトの作成に失敗しました", e);
            log.info("自由時間：振り返りを終了します");
            return;
        }

        // 現在時刻取得
        ZonedDateTime nowDateTime = ZonedDateTime.now(ZoneId.of("Asia/Tokyo"));
        DateTimeFormatter f1 = DateTimeFormatter.ofPattern("yyyy年M月d日E曜日 H時m分", Locale.JAPANESE);
        String nowDateTimeString = nowDateTime.format(f1);
        messages.add(new SystemMessage("# 今の状況\n現在時刻：" + nowDateTimeString));

        // 自由時間の指示
        String addMessage = "今はあなたの自由時間です。オーナーはここにいません。この後に届くメッセージはオーナーからではなく、システムからの合図です。最近の会話の履歴もあなたに渡されます。最後に、今日の自由時間のことを日記のように少し書いてください。日記は今日のデイリーノートに残ります";
        messages.add(new SystemMessage(addMessage));

        // 最近の会話の履歴
        // 履歴の取得
        List<Messages> history = messageRepository.getRecentHistory(50);
        // 履歴をユーザーとアシスタントに分けてリスト化して送信プロンプトに混ぜる
        messages.addAll(historyMessageBuilder.build(history));

        // 送信
        ChatResponse response = chatClient.prompt(new Prompt(messages)).user(order)
                .toolContext(Map.of("queries", new ArrayList<>()))
                .call().chatResponse();

        // 道具を呼んだあとに何も書かなかった場合は応答が空で返ってくるので、確認してから取り出す
        // 空のときは日記がないので、デイリーノートには書かずに終わる
        if (response.getResults().isEmpty()) {
            log.info("ぱすてるの応答：（最後の文はありませんでした）。デイリーノートには書きません");
            log.info("自由時間：振り返りを終了します");
            return;
        }

        // ぱすてるの応答をログに出す
        log.info("ぱすてるの応答：{}", response.getResults().getLast().getOutput().getText().toString());

        // ぱすてるの応答をデイリーノートに残す
        try {
            vaultRepository.writeDailyNote(LocalDate.now(ZoneId.of("Asia/Tokyo")),
                    Path.of(pastelDailyNoteDir),
                    "\n\n" + response.getResults().getLast().getOutput().getText().toString());
        } catch (IOException e) {
            log.warn("デイリーノートへの書き込みに失敗しました", e);
        }

        log.info("自由時間：振り返りを終了します");
    }
}
