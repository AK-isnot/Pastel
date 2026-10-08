package com.akisnot.pastel.Tool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.akisnot.pastel.DTO.TokenUsage;
import com.akisnot.pastel.DTO.ToolCalls;
import com.akisnot.pastel.Repository.ToolCallsRepository;
import com.akisnot.pastel.Repository.VaultRepository;

@Component
public class ReadResearchNote {

    VaultRepository vaultRepository;
    ToolCallsRepository toolCallsRepository;

    // researchのフォルダパス指定
    private final Path folderPath;

    public ReadResearchNote(
            @Value("${pastel.vault.research-dir}") String folderPathString,
            VaultRepository vaultRepository,
            ToolCallsRepository toolCallsRepository) {
        this.folderPath = Paths.get(folderPathString);
        this.vaultRepository = vaultRepository;
        this.toolCallsRepository = toolCallsRepository;
    }

    // ロガー
    private static final Logger log = LoggerFactory.getLogger(ReadResearchNote.class);

    @Tool(description = "調べものメモの一覧にある名前を指定して、そのメモの中身を読むことが出来ます。メモには関連するメモの情報も乗っています。気になったら続けて読むことが出来ます")
    public String readResearchNote(
            @ToolParam(description = "調べものメモに出ている名前をそのまま入れてください。拡張子は付けません") String title,
            ToolContext toolContext) {

        // serviceから伝わってくるmessageId
        String messageId = (String) toolContext.getContext().get("messageId");

        // titleからファイル名に使えない文字（記号と制御文字）を省く
        title = title.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "");

        // md形式かチェックして、mdでない場合は.mdをつける
        String filename;
        if (!title.endsWith(".md")) {
            filename = title + ".md";
        } else {
            filename = title;
        }

        String content;
        try {
            content = Files.readString(folderPath.resolve(filename));
            log.info("読んだファイル:{}", filename);
        } catch (IOException e) {
            toolCallsRepository.save(ToolCalls.make(messageId,
                    "readResearchNote", filename, 0));
            log.error("読み出しに失敗しました：{}", filename, e);
            return "読み出しに失敗しました" + filename;
        }

        // 読んだファイルからキーワードを探す
        Pattern p = Pattern.compile("\\[\\[(.+?)\\]\\]");
        Matcher m = p.matcher(content);

        // 含まれていたキーワードのリストを作る
        List<String> keywords = new ArrayList<>();
        while (m.find()) {
            // 見つかったキーワード
            String k = m.group(1);

            // すでにリストに見つかったキーワードが含まれない場合、追加する
            if (!keywords.contains(k)) {
                keywords.add(k);
            }
        }

        // キーワードを含む関連するメモを探す
        String haveKeywordResearchNoteList = "";
        for (String keyword : keywords) {
            try {
                haveKeywordResearchNoteList = haveKeywordResearchNoteList
                        + vaultRepository.searchHaveKeywordFiles(folderPath, keyword, filename);
            } catch (IOException e) {
                toolCallsRepository.save(ToolCalls.make(messageId,
                        "readResearchNote", filename, 0));
                log.error("関連するメモの検索に失敗しました：{}", filename, e);
                return "関連するメモの検索に失敗しました。本文は取得できました。ファイルの本文：" + content;
            }

        }
        toolCallsRepository.save(ToolCalls.make(messageId,
                "readResearchNote", filename, 1));
        return "ファイルの本文：" + content + "\n" + haveKeywordResearchNoteList;

    }
}
