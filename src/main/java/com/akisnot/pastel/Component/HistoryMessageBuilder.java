package com.akisnot.pastel.Component;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

import com.akisnot.pastel.DTO.Messages;
import com.github.f4b6a3.ulid.Ulid;

@Component
public class HistoryMessageBuilder {

    public List<Message> build(List<Messages> history) {

        List<Message> messages = new ArrayList<>();

        // 会話履歴をまとめて、Userとassistantのメッセージを作る
        for (Messages m : history) {

            if (m.role().equals("user")) {
                // 履歴の日時を取得する
                ZonedDateTime messageDateTime = ZonedDateTime.ofInstant(Ulid.from(m.messageId()).getInstant(),
                        ZoneId.of("Asia/Tokyo"));
                DateTimeFormatter f1 = DateTimeFormatter.ofPattern("yyyy年M月d日E曜日 H時m分", Locale.JAPANESE);
                
                // 履歴の日時を含めて、履歴を入れる
                messages.add(new UserMessage(
                        "[" + messageDateTime.format(f1) + "]" + m.content()
                    ));
            } else {
                if (m.searchQueries() == null) {
                    messages.add(new AssistantMessage(m.content()));
                } else {
                    messages.add(
                            new AssistantMessage(m.content() + "\n［システム記録：この返事の前に" + m.searchQueries() + "で検索した］"));
                }

            }
        }

        return messages;
    }

}
