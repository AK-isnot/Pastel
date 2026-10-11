package com.akisnot.pastel.Tool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

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
public class EditMemoryNote {

    ToolCallsRepository toolCallsRepository;

    // memoryのフォルダパス指定
    private final Path folderPath;

    public EditMemoryNote(@Value("${pastel.vault.memory-dir}") String folderPathString,
            ToolCallsRepository toolCallsRepository) {
        this.folderPath = Paths.get(folderPathString);
        this.toolCallsRepository = toolCallsRepository;
    }

    // ロガー
    private static final Logger log = LoggerFactory.getLogger(EditMemoryNote.class);

    @Tool(description = "メモリのファイルの一部を書き換えることが出来ます。元の文章はメモリに出ている文章をそのまま写しましょう。消したいときには新しい文章を空にしてください。新しいファイルを作る時や、全体を書きなおすときにはwriteMemoryNoteをつかいましょう")
    public String editMemoryNote(
            @ToolParam(description = "書き換えるファイル名") String title,
            @ToolParam(description = "書き換える元の文") String oldText,
            @ToolParam(description = "書き換える新しい文") String newText,
            ToolContext toolContext) {

        // serviceから伝わってくるmessageId
        String messageId = (String) toolContext.getContext().get("messageId");

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

        // titleのファイルが存在するか判定する
        // フォルダパスとファイル名を結合する
        Path p = folderPath.resolve(title);
        if (!Files.exists(p)) {
            log.error("存在しないファイルが指定されました");
            // Toolが使用されたことを記録する
            toolCallsRepository.save(ToolCalls.make(messageId, "editMemoryNote",
                    title, 0));
            return "そのファイルはありません";
        }

        // oldTextが空の場合は返却する
        if (oldText.isBlank()) {
            log.error("書き換える文章が指定されませんでした");
            // Toolが使用されたことを記録する
            toolCallsRepository.save(ToolCalls.make(messageId, "editMemoryNote",
                    title, 0));
            return "書き換える文章を指定して下さい";
        }

        // 本文を読む
        String content = "";
        try {
            content = Files.readString(p);
        } catch (IOException e) {
            log.error("ファイルの読み込みに失敗しました", e);
            // Toolが使用されたことを記録する
            toolCallsRepository.save(ToolCalls.make(messageId, "editMemoryNote",
                    title, 0));
            return "ファイルの読み込みに失敗しました";
        }

        // 本文が空の場合は返却する
        if (content.isBlank()) {
            log.error("読み込んだファイルが空でした");
            // Toolが使用されたことを記録する
            toolCallsRepository.save(ToolCalls.make(messageId, "editMemoryNote",
                    title, 0));
            return "空のファイルを指定しています";
        }

        // oldTextとの一致箇所で分岐する
        int pos = content.indexOf(oldText);
        int lastPos = content.lastIndexOf(oldText);

        // 一致箇所が存在しない
        if (pos == -1) {
            log.error("一致箇所が存在しません");
            // Toolが使用されたことを記録する
            toolCallsRepository.save(ToolCalls.make(messageId, "editMemoryNote",
                    title, 0));
            return "元の文が見つかりませんでした。メモリに出ている文をそのまま写してください";
        }

        // 一致箇所が二か所ある
        if (pos != lastPos) {
            log.error("一致箇所が複数あります");
            // Toolが使用されたことを記録する
            toolCallsRepository.save(ToolCalls.make(messageId, "editMemoryNote",
                    title, 0));
            return "元の文が2か所以上あります。前後の文も含めて1か所に絞ってください";
        }

        // 置き換えをする
        content = content.replace(oldText, newText);
        try {
            Files.writeString(p, content);
        } catch (Exception e) {
            log.error("ファイルへの書き込みに失敗しました", e);
            // Toolが使用されたことを記録する
            toolCallsRepository.save(ToolCalls.make(messageId, "editMemoryNote",
                    title, 0));
            return "ファイルへの書き込みに失敗しました";
        }

        log.info("文言を置き換えました：{}", title);
        // Toolが使用されたことを記録する
        toolCallsRepository.save(ToolCalls.make(messageId, "editMemoryNote",
                title, 1));

        return "書き換えに成功しました";

    }
}
