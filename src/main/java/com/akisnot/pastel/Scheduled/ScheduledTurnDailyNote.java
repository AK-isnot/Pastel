package com.akisnot.pastel.Scheduled;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import com.akisnot.pastel.Component.HistoryMessageBuilder;
import com.akisnot.pastel.Component.SystemMessageBuilder;
import com.akisnot.pastel.DTO.Messages;
import com.akisnot.pastel.Repository.MessageRepository;
import com.akisnot.pastel.Repository.VaultRepository;

@Configuration
@EnableScheduling
public class ScheduledTurnDailyNote {

    @Value("${pastel.vault.dailyNote-dir}")
    private String pastelDailyNoteDir;

    private final VaultRepository vaultRepository;
    private final SystemMessageBuilder systemMessageBuilder;
    private final MessageRepository messageRepository;
    private final HistoryMessageBuilder historyMessageBuilder;
    private final ChatClient chatClient;

    public ScheduledTurnDailyNote(
            VaultRepository vaultRepository,
            SystemMessageBuilder systemMessageBuilder,
            MessageRepository messageRepository, HistoryMessageBuilder historyMessageBuilder,
            ChatClient.Builder chatClientBuilder) {
        this.vaultRepository = vaultRepository;
        this.systemMessageBuilder = systemMessageBuilder;
        this.messageRepository = messageRepository;
        this.historyMessageBuilder = historyMessageBuilder;
        this.chatClient = chatClientBuilder.build();
    }

    // ロガー
    private static final Logger log = LoggerFactory.getLogger(ScheduledTurnDailyNote.class);

    // デイリーノートをめくる指示を書く
    private static final String order = """
            （システムからの合図）新しい一日になりました。
             デイリーノートを新しくしましょう。最近のノートの内容が渡されるので、新しいデイリーノートにうつしたい内容を、日記の形式で出力しましょう。それが今日のノートに書き込まれます。写さなかった内容も、昨日のノートに残ります。
             """;;

    // サーバが起動したときかつデイリーノートが存在しないとき
    // または日付が変わってデイリーノートが存在しないときに起動する
    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Tokyo")
    public void turnDailyNote() {

        log.info("デイリーノートをめくる処理を開始します");

        // 今日のページがあるか確認する
        // ファイル名を作成する
        LocalDate localDate = LocalDate.now(ZoneId.of("Asia/Tokyo"));
        DateTimeFormatter f1 = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        String filename = localDate.format(f1) + ".md";

        // フォルダパスとファイル名を結合する
        Path p = Path.of(pastelDailyNoteDir + "/" + filename);

        // デイリーノートの存在を確認する
        // あれば処理を終了する
        if (Files.exists(p)) {
            log.info("今日のデイリーノートは既に存在します。処理を終了します");
            return;
        }

        // 以前のデイリーノートのうち最新のノートを探す
        // デイリーノートのフォルダのリストを取得する
        List<Path> dailyNoteList = new ArrayList<>();
        try {
            dailyNoteList = vaultRepository.getlistDailyNote(Path.of(pastelDailyNoteDir));
        } catch (Exception e) {
            log.error("デイリーノートのリスト取得に失敗しました", e);
            log.info("処理を終了します");
            return;
        }

        // ファイル名の降順で並び替える
        List<Path> sortedDailyNoteList = dailyNoteList.stream()
                .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                .toList();

        // 今日以前で最新のデイリーノートのファイル名を取得する
        if (sortedDailyNoteList.isEmpty()) {
            log.error("デイリーノートが一つもありません");
            log.info("処理を終了します");
            return;
        }
        Path latestDailyNote = sortedDailyNoteList.getLast();

        // ぱすてるに送る内容をまとめる
        // システムプロンプトを作る
        // PASTEL.md,メモリ,調べもの一覧のリストを取得
        List<Message> messages = new ArrayList<>();
        try {
            messages.addAll(systemMessageBuilder.build());
        } catch (IOException e) {
            log.error("システムプロンプトの作成に失敗しました", e);
            log.info("処理を終了します");
            return;
        }

        // 最近の会話の履歴
        // 履歴の取得
        List<Messages> history = messageRepository.getRecentHistory(20);
        // 履歴をユーザーとアシスタントに分けてリスト化して送信プロンプトに混ぜる
        messages.addAll(historyMessageBuilder.build(history));

        // 前のページ（最新のファイル）を渡す
        String content;
        try {
            content = Files.readString(latestDailyNote);
        } catch (IOException e) {
            log.error("最新のデイリーノートの読み出しに失敗しました：{}", latestDailyNote.getFileName().toString(), e);
            log.info("処理を終了します");
            return;
        }
        messages.add(new SystemMessage("# 最近のノート\n" + content));

        // ぱすてるに送信
        ChatResponse response;
        try {
            response = chatClient.prompt(new Prompt(messages)).user(order).call().chatResponse();
        } catch (Exception e) {
            log.error("ぱすてるへの送信に失敗しました", e);
            log.info("処理を終了します");
            return;
        }

        // 返事を今日のデイリーノートに書き込む
        try {
            vaultRepository.writeDailyNote(localDate, Path.of(pastelDailyNoteDir),
                    response.getResults().getLast().getOutput().getText().toString());
        } catch (IOException e) {
            log.error("最新のデイリーノートの書き込みに失敗しました：{}", filename, e);
            log.info("処理を終了します");
            return;
        }

    }

}
