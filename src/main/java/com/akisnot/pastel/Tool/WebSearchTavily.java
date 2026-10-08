package com.akisnot.pastel.Tool;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.akisnot.pastel.DTO.ToolCalls;
import com.akisnot.pastel.Repository.ToolCallsRepository;

@Component
public class WebSearchTavily {

    ToolCallsRepository toolCallsRepository;

    // 外部のWebAPIを呼び出すためのクラス
    private final RestClient restClient;

    // jsonで検索結果が帰ってくるのでrecordクラスで包む。Tavilyの返事を受け取る型
    public record SearchResult(String title, String url, String content) {
    }

    public record SearchResultsList(List<SearchResult> results) {
    }

    public record ExtractResult(String url, String raw_content) {
    }

    public record ExtractResultList(List<ExtractResult> results) {
    }

    // ロガー
    private static final Logger log = LoggerFactory.getLogger(WebSearchTavily.class);

    // コンストラクタが最初に呼ばれるのでapikeyの受け取りは引数でやる
    public WebSearchTavily(@Value("${tavily.api-key}") String apiKey, ToolCallsRepository toolCallsRepository) {
        this.restClient = RestClient.builder().baseUrl("https://api.tavily.com")
                .defaultHeader("Authorization", "Bearer " + apiKey).build();
        this.toolCallsRepository = toolCallsRepository;
    }

    @Tool(description = "webを検索して、関連するページの情報を返します。調べたい事、調べてと指示されたこと、興味のある事を検索するときに使用します")
    public SearchResultsList webSearch(
            @ToolParam(description = "検索する言葉") String query,
            ToolContext toolContext) {
        // serviceから伝わってくるmessageId
        String messageId = (String) toolContext.getContext().get("messageId");

        // 送信
        SearchResultsList searchResultsList;
        try {

            // SearchResultsList=List<SearchResult> searchResultsList
            // つまり複数の検索結果をまとめて一つのクラスで受け取ってると理解
            searchResultsList = restClient
                    .post()
                    .uri("/search")
                    .body(Map.of("query", query, "max_results", 5, "country", "japan"))
                    .retrieve()
                    .body(SearchResultsList.class);

        } catch (RestClientException e) {
            toolCallsRepository.save(ToolCalls.make(messageId,
                    "webSearch", query, 0));
            throw e;
        }

        // Serviceから引数のtoolContextを通して渡ってくるsearchQueriesに検索した内容を足す？
        @SuppressWarnings("unchecked")
        List<String> searchQueries = (List<String>) toolContext.getContext().get("queries");
        searchQueries.add(query);

        // ぱすてるが何を検索したかログに出す
        log.info("Tavily検索: query={}", query);

        // DB保存
        toolCallsRepository.save(ToolCalls.make(messageId,
                "webSearch", query, 1));

        return searchResultsList;
    }

    @Tool(description = "webサイトを指定して読むことが出来ます。知りたいことを指定しておくことで、ページの全文ではなくそれに関連した情報が抜粋されて返却されます。urlはwebSearchの結果に出たURLをそのまま入れてください")
    public String readWebPage(
            @ToolParam(description = "知りたいこと") String query,
            @ToolParam(description = "閲覧するURL") String url,
            ToolContext toolContext) {
        // serviceから伝わってくるmessageId
        String messageId = (String) toolContext.getContext().get("messageId");

        // 送信
        ExtractResultList extractResultList;
        try {

            extractResultList = restClient
                    .post()
                    .uri("/extract")
                    .body(Map.of("query", query, "urls", url, "chunks_per_source", 5))
                    .retrieve()
                    .body(ExtractResultList.class);

        } catch (RestClientException e) {
            toolCallsRepository.save(ToolCalls.make(messageId,
                    "readWebPage", url, 0));
            log.error("ページを読めませんでした：{}", url, e);
            return "ページを読めませんでした";
        }

        if (extractResultList.results.isEmpty()) {
            toolCallsRepository.save(ToolCalls.make(messageId,
                    "readWebPage", url, 0));
            log.error("ページを読めませんでした：{}", url);
            return "ページを読めませんでした";
        }

        // ぱすてるが何を検索したかログに出す
        log.info("Tavily検索: query={} url={}", query, url);

        // DB保存
        toolCallsRepository.save(ToolCalls.make(messageId,
                "readWebPage", url, 1));

        // 結果
        return "ページのうち、知りたいことに関係する部分の抜粋（全文ではありません）：" + extractResultList.results.getFirst().raw_content;
    }
}
