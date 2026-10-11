package com.akisnot.pastel.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class MessageNotifier {

    private List<SseEmitter> sseEmitter = new CopyOnWriteArrayList<>();

    // サーバと画面を繋いでおくメソッド
    public SseEmitter subscribe() {

        // Emitter一つにつき一つのブラウザと接続してる
        // このメソッドの中でつなぎに来たブラウザと接続を維持する
        // 最終的にはリストで管理する
        SseEmitter emitter = new SseEmitter(0L); // 時間切れにならないように
        sseEmitter.add(emitter);

        // 接続を切るイベントを設定しておく
        emitter.onCompletion(() -> sseEmitter.remove(emitter));
        emitter.onTimeout(() -> sseEmitter.remove(emitter));
        emitter.onError(e -> sseEmitter.remove(emitter));

        return emitter;

    }

    // つながってる画面側に知らせを送るメソッド
    public void notifyNewMessage() {

        for (SseEmitter s : sseEmitter) {
            try {
                s.send(SseEmitter.event().name("new-message").data("new"));
                
            } catch (Exception e) {
                //タブが閉じられている場合などもうつながっていないsseEmitterを外す
                sseEmitter.remove(s);
            }

        }

    }
}
