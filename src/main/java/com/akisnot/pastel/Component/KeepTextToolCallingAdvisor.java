package com.akisnot.pastel.Component;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.support.UsageCalculator;
import org.springframework.stereotype.Component;

// ツールの往復を回す部品（Spring AIのToolCallingAdvisor）を、返事の文を拾えるように差し替えたもの
// ぱすてるが返事の文と道具の呼び出しを同じ応答で出し、道具のあとに何も書かなかったとき、
// Spring AIは最後の（空の）応答だけを返すので、返事の文が消えてしまう
// それを防ぐため、往復の各回で文が書かれた応答をためておき、最後の応答が空ならその文を返事にする
@Component
public class KeepTextToolCallingAdvisor extends ToolCallingAdvisor {

    // ためておくリストを、リクエストのcontextに入れるときのキー
    private static final String ROUNDS_KEY = "pastel.roundsWithText";

    // ロガー
    private static final Logger log = LoggerFactory.getLogger(KeepTextToolCallingAdvisor.class);

    public KeepTextToolCallingAdvisor(ToolCallingManager toolCallingManager) {
        // 標準の部品と同じ設定で作る（道具を呼んだかの判定・順番は標準のもの、往復ごとに会話の履歴を渡す）
        super(toolCallingManager, DEFAULT_TOOL_EXECUTION_ELIGIBILITY_CHECKER, DEFAULT_ORDER, true);
    }

    // 往復を始める前：この1回の送信のために、空のリストを用意する
    @Override
    protected ChatClientRequest doInitializeLoop(ChatClientRequest chatClientRequest,
            CallAdvisorChain callAdvisorChain) {
        return chatClientRequest.mutate().context(ROUNDS_KEY, new ArrayList<ChatResponse>()).build();
    }

    // 往復の各回のあと：文が書かれていれば、その応答をためておく
    @Override
    protected ChatClientResponse doAfterCall(ChatClientResponse chatClientResponse,
            CallAdvisorChain callAdvisorChain) {
        ChatResponse chatResponse = chatClientResponse.chatResponse();
        List<ChatResponse> rounds = getRounds(chatClientResponse);
        if (chatResponse != null && rounds != null && !chatResponse.getResults().isEmpty()) {
            String text = chatResponse.getResults().getLast().getOutput().getText();
            if (text != null && !text.isBlank()) {
                rounds.add(chatResponse);
            }
        }
        return chatClientResponse;
    }

    // 往復が終わったあと：最後の応答が空なら、ためておいた文を返事にする
    @Override
    protected ChatClientResponse doFinalizeLoop(ChatClientResponse chatClientResponse,
            CallAdvisorChain callAdvisorChain) {
        ChatResponse lastResponse = chatClientResponse.chatResponse();

        // 最後の応答に中身があれば、今までどおりそのまま返す
        if (lastResponse == null || !lastResponse.getResults().isEmpty()) {
            return chatClientResponse;
        }

        // ためておいた文がなければ拾えるものがないので、そのまま返す（ChatService側でエラーになる）
        List<ChatResponse> rounds = getRounds(chatClientResponse);
        if (rounds == null || rounds.isEmpty()) {
            return chatClientResponse;
        }

        // ためておいた文をつなげて、1つの返事にする
        List<String> texts = new ArrayList<>();
        for (ChatResponse round : rounds) {
            texts.add(round.getResults().getLast().getOutput().getText());
        }
        String joinedText = String.join("\n\n", texts);

        // モデル名などは文があった最後の回から取る
        ChatResponse newResponse = ChatResponse.builder()
                .from(rounds.getLast())
                .generations(List.of(new Generation(new AssistantMessage(joinedText))))
                .build();
        // トークン数は往復全体の合計（最後の応答に入っている）に差し替える
        if (!UsageCalculator.isEmpty(lastResponse.getMetadata().getUsage())) {
            newResponse = UsageCalculator.withUsage(newResponse, lastResponse.getMetadata().getUsage());
        }

        log.info("最後の応答が空だったので、道具と一緒に書かれた文を返事にしました");

        return chatClientResponse.mutate().chatResponse(newResponse).build();
    }

    // contextから、ためておいたリストを取り出す
    @SuppressWarnings("unchecked")
    private List<ChatResponse> getRounds(ChatClientResponse chatClientResponse) {
        return (List<ChatResponse>) chatClientResponse.context().get(ROUNDS_KEY);
    }

}
