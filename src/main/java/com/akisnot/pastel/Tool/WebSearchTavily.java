package com.akisnot.pastel.Tool;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class WebSearchTavily {

    // 外部のWebAPIを呼び出すためのクラス
    private final RestClient restClient;

    // jsonで検索結果が帰ってくるのでrecordクラスで包む。Tavilyの返事を受け取る型
    public record SearchResult(String title, String url, String content) {
    }

    public record SearchResultsList(List<SearchResult> results) {
    }

    //ロガー
    private static final Logger log = LoggerFactory.getLogger(WebSearchTavily.class);

    // コンストラクタが最初に呼ばれるのでapikeyの受け取りは引数でやる
    public WebSearchTavily(@Value("${tavily.api-key}") String apiKey) {
        this.restClient = RestClient.builder().baseUrl("https://api.tavily.com")
                .defaultHeader("Authorization", "Bearer " + apiKey).build();
    }

    @Tool(description = "webを検索して、関連するページの情報を返します。調べたい事、調べてと指示されたこと、興味のある事を検索するときに使用します")
    public SearchResultsList webSearch(@ToolParam(description = "検索する言葉") String query) {
        // 送信
        // SearchResultsList=List<SearchResult> searchResultsList
        // つまり複数の検索結果をまとめて一つのクラスで受け取ってると理解
        SearchResultsList searchResultsList = restClient
                .post()
                .uri("/search")
                .body(Map.of("query", query, "max_results", 5, "country", "japan"))
                .retrieve()
                .body(SearchResultsList.class);

        //ぱすてるが何を検索したかログに出す
        log.info("Tavily検索: query={}", query);

        return searchResultsList;
    }

}
