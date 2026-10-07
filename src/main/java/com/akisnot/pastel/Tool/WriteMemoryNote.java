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
public class WriteMemoryNote {
    // memoryのフォルダパス指定
    private final Path folderPath;

    public WriteMemoryNote(@Value("${pastel.vault.memory-dir}") String folderPathString) {
        this.folderPath = Paths.get(folderPathString);
    }

    // ロガー
    private static final Logger log = LoggerFactory.getLogger(WriteMemoryNote.class);

    @Tool(description = "会話をしていく中で、あなたが覚えておきたいと思ったこと、オーナーが覚えて欲しいと指示したことを記録します。同じ見出しのファイルは丸ごと置き換わります。更新するときは、残したい内容も含めて全文を書いてください")
    public String writeMemoryNote(
            @ToolParam(description = "書き込むファイルの名称。30文字以内。更新するときは # メモリ に出ているのと同じ名前にしてください。拡張子は付けずに中身が変わっても使い続けられる短い見出しにしましょう") String title,
            @ToolParam(description = "メモの本文。Markdownで書く") String body) {

        // titleからファイル名に使えない文字（記号と制御文字）を省く
        title = title.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "");

        // titleが30文字を超えていたら切る
        if (title.length() > 30) {
            title = title.substring(0, 30);
        }

        // md形式かチェックして、mdでない場合は.mdをつける
        if (!title.endsWith(".md")) {
            title = title + ".md";
        }    

        try {
            // フォルダ作成
            Files.createDirectories(folderPath);

            // 書き込むファイルのパス作成
            Path filePath = folderPath.resolve(title);

            // 書き込む
            Files.writeString(filePath, body);

            // ぱすてるが何を記録したかログに出す
            log.info("保存したファイル：{}", filePath);

            return "保存に成功しました：" + title;
        } catch (IOException e) {
            log.error("保存に失敗しました：" + e.getMessage());
            return "保存に失敗しました：" + e.getMessage();
        }

    }
}
