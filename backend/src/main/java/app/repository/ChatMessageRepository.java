package app.repository;

import app.model.ChatMessage;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface ChatMessageRepository extends MongoRepository<ChatMessage, String> {
    List<ChatMessage> findByRoomIdOrderBySentAtAsc(String roomId);
    long countByRoomIdAndReadFalseAndSenderEmailNot(String roomId, String readerEmail);
}
