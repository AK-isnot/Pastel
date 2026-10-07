package com.akisnot.pastel.Tool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ReadResearchNote {
    // researchのフォルダパス指定
    private final Path folderPath;

    public ReadResearchNote(@Value("${pastel.vault.research-dir}") String folderPathString) {
        this.folderPath = Paths.get(folderPathString);
    }

    // ロガー
    private static final Logger log = LoggerFactory.getLogger(ReadResearchNote.class);

    @Tool(description = "調べものメモの一覧にある名前を指定して、そのメモの中身を読むことが出来ます")
    public String readResearchNote(@ToolParam(description = "調べものメモに出ている名前をそのまま入れてください。拡張子は付けません") String title) {

        // titleからファイル名に使えない文字（記号と制御文字）を省く
        title = title.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "");

        // md形式かチェックして、mdでない場合は.mdをつける
        if (!title.endsWith(".md")) {
            title = title + ".md";
        }
        try {
            String content = Files.readString(folderPath.resolve(title));
            log.info("読んだファイル:{}", title);
            return "ファイルの本文：" + content;
        } catch (IOException e) {
            return "読み出しに失敗しました" + title;
        }

    }
}
