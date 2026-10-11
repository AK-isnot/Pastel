package com.akisnot.pastel.Controller;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.akisnot.pastel.Component.MessageNotifier;
import com.akisnot.pastel.DTO.Messages;
import com.akisnot.pastel.Service.ChatService;
import com.akisnot.pastel.Tool.WebSearchTavily;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Controller
public class ChatViewController {

    private final ChatService chatService;
    private final MessageNotifier messageNotifier;

    public ChatViewController(ChatService chatService, MessageNotifier messageNotifier) {
        this.chatService = chatService;
        this.messageNotifier = messageNotifier;
    }

    // ロガー
    private static final Logger log = LoggerFactory.getLogger(ChatViewController.class);

    @GetMapping("/")
    public String showChat(Model model) {
        // 履歴の取得
        List<Messages> history = chatService.getHistory(20);

        // 履歴を画面側に渡す
        model.addAttribute("messages", history);

        // これでchat.htmlに情報が渡る
        return "chat";
    }

    @PostMapping("/send")
    public String send(@RequestParam("message") String message, Model model) {
        // 画面に渡す
        model.addAttribute("message", message);

        // chat.html内のpendingの部品だけ返す
        return "chat :: pending";
    }

    @PostMapping("/reply")
    public String reply(@RequestParam("message") String message, Model model) {

        try {
            // pastelと会話
            chatService.chat(message);
        } catch (Exception e) {
            log.error("返事の取得に失敗しました", e);
            model.addAttribute("replyFailed", true);
            return "chat :: error";
        }

        // 会話した分を取得
        List<Messages> history = chatService.getHistory(1);

        // 画面に渡す
        model.addAttribute("messages", history);

        // chat.html内のmessageListの部品だけ返す
        return "chat :: messageList";
    }
    
    //
    @GetMapping("/events")
    public SseEmitter events() {
        return messageNotifier.subscribe();
    }

    @GetMapping("/latest")
    public String latest(Model model) {
        // 会話した分を取得
        List<Messages> history = chatService.getHistory(1);

        // 画面に渡す
        model.addAttribute("messages", history);

        // chat.html内のmessageListの部品だけ返す
        return "chat :: messageList";
    }

}
