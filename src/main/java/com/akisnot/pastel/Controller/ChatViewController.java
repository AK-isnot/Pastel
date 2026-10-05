package com.akisnot.pastel.Controller;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.akisnot.pastel.DTO.PastelMessage;
import com.akisnot.pastel.Service.ChatService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class ChatViewController {

    private final ChatService chatService;

    public ChatViewController(ChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping("/")
    public String showChat(Model model) {
        // 履歴の取得
        List<PastelMessage> history = chatService.getHistory(20);

        // 履歴を画面側に渡す
        model.addAttribute("messages", history);

        // これでchat.htmlに情報が渡る
        return "chat";
    }

    @PostMapping("/send")
    public String send(@RequestParam("message") String message, Model model) {
        // pastelと会話
        chatService.chat(message);

        // 会話した分を取得
        List<PastelMessage> history = chatService.getHistory(2);

        // 画面に渡す
        model.addAttribute("messages", history);

        // chat.html内のmessageListの部品だけ返す
        return "chat :: messageList";
    }

}
