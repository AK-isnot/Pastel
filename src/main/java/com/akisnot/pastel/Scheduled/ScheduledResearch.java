package com.akisnot.pastel.Scheduled;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import com.akisnot.pastel.Repository.MessageRepository;
import com.akisnot.pastel.Service.ChatService;
import com.akisnot.pastel.Tool.WebSearchTavily;
import com.akisnot.pastel.Tool.WriteResearchNote;

@Configuration
@EnableScheduling
public class ScheduledResearch {
    private final ChatClient chatClient;
    private final WebSearchTavily webSearchTavily;
    private final WriteResearchNote writeResearchNote;

    public ScheduledResearch(MessageRepository messageRepository, ChatClient.Builder chatClientBuilder,
            WebSearchTavily webSearchTavily, WriteResearchNote writeResearchNote) {
        this.chatClient = chatClientBuilder.defaultTools(webSearchTavily, writeResearchNote).build();
        this.webSearchTavily = webSearchTavily;
        this.writeResearchNote = writeResearchNote;

    }

    // ロガー
    private static final Logger log = LoggerFactory.getLogger(ScheduledResearch.class);

    // 調べものの指示
    private static final String order = "自由に調べものをしてください、気になったことを調べましょう。調べた結果はメモに残してください。これはあなた用のメモなので分量は自由に、書き留めておきたいだけ書きましょう";

    @Scheduled(fixedRate = 1, timeUnit = TimeUnit.HOURS)
    public void webResearch() {

        // PASTEL.md読み込み
        String pastelMd;
        try (InputStream in = ChatService.class.getResourceAsStream("/PASTEL.md")) {
            pastelMd = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("PASTEL.mdの読み込みに失敗しました", e);
            return;
        } 

        // 送信
        ChatResponse response = chatClient.prompt().system(pastelMd).user(order).toolContext(Map.of("queries", new ArrayList<>()))
        .call().chatResponse();

        //ぱすてるの応答をログに出す
        log.info("ぱすてるの応答：{}",response.getResults().getLast().getOutput().getText().toString());

    }

}
