package app.controller;

import app.dto.ChatMessageRequest;
import app.model.ChatMessage;
import app.model.ChatRoom;
import app.service.ChatService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chats")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    /** GET /api/chats  →  all rooms for the authenticated user/NGO */
    @GetMapping
    public List<ChatRoom> myRooms(Authentication auth) {
        return chatService.myRooms(auth.getName());
    }

    /**
     * POST /api/chats/{ngoId}/start
     * User opens (or retrieves) a chat with an NGO.
     */
    @PostMapping("/{ngoId}/start")
    public ChatRoom startChat(@PathVariable String ngoId, Authentication auth) {
        return chatService.getOrCreateRoom(ngoId, auth.getName());
    }

    /** GET /api/chats/{roomId}/messages  →  full message history */
    @GetMapping("/{roomId}/messages")
    public List<ChatMessage> getMessages(@PathVariable String roomId, Authentication auth) {
        return chatService.getMessages(roomId, auth.getName());
    }

    /** POST /api/chats/{roomId}/messages  →  send a message */
    @PostMapping("/{roomId}/messages")
    public ChatMessage sendMessage(@PathVariable String roomId,
                                   @RequestBody ChatMessageRequest req,
                                   Authentication auth) {
        return chatService.sendMessage(roomId, auth.getName(), req);
    }

    /** GET /api/chats/{roomId}/unread  →  unread message count */
    @GetMapping("/{roomId}/unread")
    public Map<String, Long> unreadCount(@PathVariable String roomId, Authentication auth) {
        return Map.of("unread", chatService.unreadCount(roomId, auth.getName()));
    }
}
