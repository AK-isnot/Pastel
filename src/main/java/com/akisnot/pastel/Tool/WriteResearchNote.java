package com.akisnot.pastel.Tool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.akisnot.pastel.DTO.ToolCalls;
import com.akisnot.pastel.Repository.ToolCallsRepository;

@Component
public class WriteResearchNote {

    ToolCallsRepository toolCallsRepository;

    // researchのフォルダパス指定
    private final Path folderPath;

    public WriteResearchNote(@Value("${pastel.vault.research-dir}") String folderPathString,
            ToolCallsRepository toolCallsRepository) {
        this.folderPath = Paths.get(folderPathString);
        this.toolCallsRepository = toolCallsRepository;
    }

    // ロガー
    private static final Logger log = LoggerFactory.getLogger(WriteResearchNote.class);

    @Tool(description = "WEBを調べた結果、記録しておきたいことを記録するために、新しいメモを作ります")
    public String writeResearchNote(
            @ToolParam(description = "ファイル名になる短い見出し。30文字以内。[[ ]] や記号は使わない。日付は自動で付くので、見出しだけ入れてください") String title,
            @ToolParam(description = "メモの本文。Markdownで書く") String body, ToolContext toolContext) {

        // serviceから伝わってくるmessageId
        String messageId = (String) toolContext.getContext().get("messageId");

        // titleからファイル名に使えない文字（記号と制御文字）を省く
        title = title.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "");

        // titleが30文字を超えていたら切る
        if (title.length() > 30) {
            title = title.substring(0, 30);
        }

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

            // DB保存
            toolCallsRepository.save(ToolCalls.make(messageId,
                    "writeResearchNote", fileName, 1));

            return "保存に成功しました：" + fileName;
        } catch (IOException e) {
            // DB保存
            toolCallsRepository.save(ToolCalls.make(messageId,
                    "writeResearchNote", fileName, 0));
            log.error("保存に失敗しました：" + e.getMessage());
            return "保存に失敗しました：" + e.getMessage();
        }

    }
}
