package com.akisnot.pastel.Controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.akisnot.pastel.Service.ChatService;

@RestController
public class ChatController {
    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/chat")
    public ChatReply chat(@RequestBody ChatRequest request) {
        return new ChatReply(chatService.chat(request.message()));
    }

    // jsonでくるむためにrecordクラスで包む
    public record ChatRequest(String message) {
    }

    public record ChatReply(String reply) {
    }

}
