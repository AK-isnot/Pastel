package com.akisnot.pastel.Repository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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
}
