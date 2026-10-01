package app.service;

import app.dto.ChatMessageRequest;
import app.model.*;
import app.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.List;

@Service
public class ChatService {

    private final ChatRoomRepository roomRepository;
    private final ChatMessageRepository messageRepository;
    private final AccountRepository accountRepository;
    private final NgoRepository ngoRepository;

    public ChatService(ChatRoomRepository roomRepository,
                       ChatMessageRepository messageRepository,
                       AccountRepository accountRepository,
                       NgoRepository ngoRepository) {
        this.roomRepository = roomRepository;
        this.messageRepository = messageRepository;
        this.accountRepository = accountRepository;
        this.ngoRepository = ngoRepository;
    }

    /**
     * User starts or retrieves an existing chat room with an NGO.
     */
    public ChatRoom getOrCreateRoom(String ngoId, String userEmail) {
        Account user = accountRepository.findByEmail(userEmail);
        if (user == null)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found");

        Ngo ngo = ngoRepository.findById(ngoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "NGO not found"));

        return roomRepository.findByNgoIdAndUserId(ngoId, user.getId())
                .orElseGet(() -> {
                    ChatRoom room = new ChatRoom();
                    room.setNgoId(ngoId);
                    room.setNgoEmail(ngo.getEmail());
                    room.setNgoName(ngo.getNgoName());
                    room.setUserId(user.getId());
                    room.setUserEmail(user.getEmail());
                    room.setUserName(user.getFullName());
                    room.setCreatedAt(Instant.now());
                    return roomRepository.save(room);
                });
    }

    /**
     * Returns all chat rooms for the authenticated principal (user or NGO).
     */
    public List<ChatRoom> myRooms(String email) {
        // Try as user first, then as NGO
        Account user = accountRepository.findByEmail(email);
        if (user != null)
            return roomRepository.findByUserEmailOrderByLastMessageAtDesc(email);

        Ngo ngo = ngoRepository.findByEmail(email);
        if (ngo != null)
            return roomRepository.findByNgoEmailOrderByLastMessageAtDesc(email);

        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account not found");
    }

    /**
     * Get all messages in a room (checks that caller is a participant).
     */
    public List<ChatMessage> getMessages(String roomId, String callerEmail) {
        ChatRoom room = getRoom(roomId);
        assertParticipant(room, callerEmail);

        // Mark unread messages as read
        List<ChatMessage> messages = messageRepository.findByRoomIdOrderBySentAtAsc(roomId);
        messages.stream()
                .filter(m -> !m.getSenderEmail().equalsIgnoreCase(callerEmail) && !m.isRead())
                .forEach(m -> { m.setRead(true); messageRepository.save(m); });

        return messages;
    }

    /**
     * Send a message in a room.
     */
    public ChatMessage sendMessage(String roomId, String senderEmail, ChatMessageRequest req) {
        if (req.getContent() == null || req.getContent().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message content cannot be empty");

        ChatRoom room = getRoom(roomId);
        assertParticipant(room, senderEmail);

        String senderType;
        String senderName;

        Account user = accountRepository.findByEmail(senderEmail);
        if (user != null) {
            senderType = "USER";
            senderName = user.getFullName();
        } else {
            Ngo ngo = ngoRepository.findByEmail(senderEmail);
            if (ngo == null)
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sender account not found");
            senderType = "NGO";
            senderName = ngo.getNgoName();
        }

        ChatMessage msg = new ChatMessage();
        msg.setRoomId(roomId);
        msg.setSenderEmail(senderEmail);
        msg.setSenderName(senderName);
        msg.setSenderType(senderType);
        msg.setContent(req.getContent().trim());
        msg.setSentAt(Instant.now());
        msg.setRead(false);
        messageRepository.save(msg);

        // Update room preview
        room.setLastMessageAt(msg.getSentAt());
        room.setLastMessagePreview(msg.getContent().length() > 60
                ? msg.getContent().substring(0, 60) + "…" : msg.getContent());
        roomRepository.save(room);

        return msg;
    }

    /**
     * Count unread messages for the caller in a specific room.
     */
    public long unreadCount(String roomId, String callerEmail) {
        return messageRepository.countByRoomIdAndReadFalseAndSenderEmailNot(roomId, callerEmail);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private ChatRoom getRoom(String roomId) {
        return roomRepository.findById(roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Chat room not found"));
    }

    private void assertParticipant(ChatRoom room, String email) {
        boolean isUser = room.getUserEmail().equalsIgnoreCase(email);
        boolean isNgo  = room.getNgoEmail().equalsIgnoreCase(email);
        if (!isUser && !isNgo)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not a participant of this chat");
    }
}
