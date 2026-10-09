package com.akisnot.pastel.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.akisnot.pastel.Repository.VaultRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;

@Component
public class SystemMessageBuilder {

    @Value("${pastel.vault.memory-dir}")
    private String pastelMemoryDir;
    @Value("${pastel.vault.research-dir}")
    private String pastelResearchDir;
    @Value("${pastel.vault.dailyNote-dir}")
    private String pastelDailyNoteDir;

    private final VaultRepository vaultRepository;

    public SystemMessageBuilder(VaultRepository vaultRepository) {
        this.vaultRepository = vaultRepository;
    }

    // ロガー
    private static final Logger log = LoggerFactory.getLogger(SystemMessageBuilder.class);

    public List<Message> build() throws IOException {

        // システムメッセージとして情報を渡す（返却値）
        List<Message> messages = new ArrayList<>();

        // PASTEL.mdを読み出す
        String pastelMd;
        try (InputStream in = getClass().getResourceAsStream("/PASTEL.md")) {
            pastelMd = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("PASTEL.mdの読み出しに失敗しました", e);
            throw e;
        } catch (Exception e) {
            log.error("PASTEL.mdの読み出しに失敗しました", e);
            throw e;
        }
        messages.add(new SystemMessage(pastelMd));

        // メモリの取得
        String memory;
        try {
            memory = vaultRepository.readVaultMemoryFile(Path.of(pastelMemoryDir));
        } catch (IOException e) {
            log.error("メモリの取得に失敗しました", e);
            throw e;
        }
        messages.add(new SystemMessage(memory));

        // 調べもの一覧の取得
        String researchIndex;
        try {
            researchIndex = vaultRepository.getFolderFileList(Path.of(pastelResearchDir), ".md");
        } catch (IOException e) {
            log.error("調べもの一覧の取得に失敗しました", e);
            throw e;
        }
        messages.add(new SystemMessage("# 調べものメモ\n" + researchIndex));

        // 今日のデイリーノートを読む
        String dailyNoteContent;
        try {
            dailyNoteContent = vaultRepository.readDailyNote(LocalDate.now(ZoneId.of("Asia/Tokyo")),
                    Path.of(pastelDailyNoteDir));
        } catch (IOException e) {
            log.error("デイリーノートの取得に失敗しました", e);
            throw e;
        }
        if (!dailyNoteContent.isEmpty()) {
            messages.add(new SystemMessage("# 今日のデイリーノート\n" + dailyNoteContent));
        } else {
            messages.add(new SystemMessage("# 今日のデイリーノート\n(まだなにもかいていません)"));
        }

        return messages;

    }

}
