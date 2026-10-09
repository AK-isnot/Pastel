package com.akisnot.pastel.Repository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

@Repository
public class VaultRepository {
    // ロガー
    private static final Logger log = LoggerFactory.getLogger(VaultRepository.class);

    // 指定したフォルダ配下のファイルをまとめて読む
    // 出力の形式は以下
    // # メモリ（覚えていること）
    // <memory name="(ファイル名)">
    // （ファイルの中身）
    // </memory>
    public String readVaultMemoryFile(Path folderPath) throws IOException {

        // 引数のディレクトリに何のファイルがあるかを調べる
        // Files::isRegularFileで通常ファイルだけ取得する
        List<Path> fileList;
        try (var stream = Files.list(folderPath)) {
            fileList = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".md"))
                    .toList();
        }

        // 返却用変数
        String readResults = "# メモリ（覚えていること）\n";
        // listのファイルを全て読んで、返却形式を整える
        for (Path p : fileList) {
            // ファイルを読む
            String content = Files.readString(p);

            // タグ作成
            String firstTag;
            String filename = p.getFileName().toString();
            if (filename.endsWith(".md")) {
                filename = filename.substring(0, filename.length() - 3); // "sample"
            }
            firstTag = "<memory name=\"" + filename + "\">\n";
            String lastTag = "</memory>\n";

            // タグ追加
            readResults = readResults + firstTag;

            // 本文追加
            readResults = readResults + content + "\n";

            // タグ追加
            readResults = readResults + lastTag;

        }

        return readResults;

    }

    // 指定したフォルダの配下のファイルを返す
    // 出力例
    // sampleA
    // sampleB
    // ...
    public String getFolderFileList(Path folderPath, String suffix) throws IOException {
        // 引数のディレクトリに何のファイルがあるかを調べる
        // Files::isRegularFileで通常ファイルだけ取得する
        List<Path> fileList;
        try (var stream = Files.list(folderPath)) {
            fileList = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".md"))
                    .toList();
        }

        // 整形
        String result = "";
        for (Path p : fileList) {
            // ファイル名（拡張子なしにする）
            String filename = p.getFileName().toString();
            if (filename.endsWith(suffix)) {
                filename = filename.substring(0, filename.length() - suffix.length());
            }

            result = result + filename + "\n";
        }

        return result;
    }

    // [[ (キーワード) ]]形式のキーワードを用いて関連するメモを返す
    // # つながっているメモ：（キーワード）
    // - 2026-10-06_2124_眠りと乾眠のこと
    // - 2026-10-07_1142_夢とレム睡眠で脳に起きていること
    public String searchHaveKeywordFiles(Path folderPath, String keyword, String filename) throws IOException {
        // 引数のディレクトリに何のファイルがあるかを調べる
        List<Path> fileList;
        try (var stream = Files.list(folderPath)) {
            fileList = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".md"))
                    .toList();
        }

        // キーワードが含まれるファイルをリスト化する
        List<Path> haveKeywordfileList = new ArrayList<>();
        Pattern pattern = Pattern.compile("\\[\\[" + Pattern.quote(keyword) + "\\]\\]");
        for (Path p : fileList) {
            // ファイルを読む
            String content = Files.readString(p);

            // キーワードが含まれるかを確認する
            // かつそのファイルが、読んだファイルの場合ははじく
            Matcher m = pattern.matcher(content);
            if (m.find()) {
                if (!filename.equals(p.getFileName().toString())) {
                    haveKeywordfileList.add(p);
                }

            }
        }

        // キーワードが含まれなかった場合には空文字を返す
        if (haveKeywordfileList.isEmpty()) {
            return "";
        }

        // 整形して返却する
        String result = "# つながっているメモ：" + keyword + "\n";
        for (Path p : haveKeywordfileList) {
            result = result + "- " + p.getFileName().toString() + "\n";
        }

        return result;

    }

    // yyyy-MM-dd.md形式のデイリーノートを読んで、中身を返却する
    public String readDailyNote(LocalDate localDate, Path folderPath) throws IOException {

        // ファイル名を作成する
        DateTimeFormatter f1 = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        String filename = localDate.format(f1) + ".md";

        // フォルダパスとファイル名を結合する
        Path p = folderPath.resolve(filename);

        // デイリーノートの存在を確認する
        // あればそれを読み、なければ空文字を返す
        String content;
        if (Files.exists(p)) {
            // ファイルを読む
            content = Files.readString(p);
        } else {
            log.warn("デイリーノートがまだありません：{}", filename);
            return "";
        }

        log.info("デイリーノートを読みました：{}", filename);

        // 返却
        return content;

    }

    // yyyy-MM-dd.md形式のデイリーノートに書き足す
    public void writeDailyNote(LocalDate localDate, Path folderPath, String addContent) throws IOException {

        // ファイル名を作成する
        DateTimeFormatter f1 = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        String filename = localDate.format(f1) + ".md";

        // フォルダパスとファイル名を結合する
        Path p = folderPath.resolve(filename);

        // デイリーノートに追記をする
        Files.writeString(p, addContent, StandardOpenOption.CREATE, StandardOpenOption.APPEND);

        log.info("デイリーノートに追記しました：{}", filename);
    }

    // デイリーノートの一覧を返す
    public List<Path> getlistDailyNote(Path folderPath) throws IOException {

        // デイリーノートのフォルダのリストを返す
        List<Path> fileList;
        try (var stream = Files.list(folderPath)) {
            fileList = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".md"))
                    .toList();
        } catch (IOException e) {
            throw e;
        }

        return fileList;
    }
}
