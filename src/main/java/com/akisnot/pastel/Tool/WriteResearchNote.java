package com.akisnot.pastel.Tool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class WriteResearchNote {
    // researchのフォルダパス指定
    private final Path folderPath;

    public WriteResearchNote(@Value("${pastel.vault.research-dir}") String folderPathString) {
        this.folderPath = Paths.get(folderPathString);
    }

    // ロガー
    private static final Logger log = LoggerFactory.getLogger(WriteResearchNote.class);

    @Tool(description = "WEBを調べた結果、記録しておきたいことを記録します。ファイルを開いて、書き込み保存することが出来ます")
    public String write(
        @ToolParam(description = "ファイル名になる短い見出し。30文字以内。[[ ]] や記号は使わない") String title, 
        @ToolParam(description = "メモの本文。Markdownで書く") String body) {
        // ファイル名を作成する（yyyy-MM-dd_HHmm_見出し.md）
        // 現在時刻取得
        ZonedDateTime nowDateTime = ZonedDateTime.now(ZoneId.of("Asia/Tokyo"));
        DateTimeFormatter f1 = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm_", Locale.JAPANESE);
        // ファイル名作成
        String fileName = nowDateTime.format(f1) + title + ".md";

        try {
            // フォルダ作成
            Files.createDirectories(folderPath);

            // 書き込むファイルのパス作成
            Path filePath = folderPath.resolve(fileName);

            // 書き込む
            Files.writeString(filePath, body, StandardOpenOption.CREATE_NEW);

            // ぱすてるが何を保存したかログに出す
            log.info("保存したファイル：{}", filePath);

            return "保存に成功しました：" + fileName;
        } catch (IOException e) {
            log.info("保存に失敗しました：" + e.getMessage());
            return "保存に失敗しました：" + e.getMessage();
        }

    }
}
