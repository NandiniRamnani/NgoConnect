package app.repository;

import app.model.ChatRoom;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;
import java.util.Optional;

public interface ChatRoomRepository extends MongoRepository<ChatRoom, String> {
    List<ChatRoom> findByUserEmailOrderByLastMessageAtDesc(String userEmail);
    List<ChatRoom> findByNgoEmailOrderByLastMessageAtDesc(String ngoEmail);
    Optional<ChatRoom> findByNgoIdAndUserId(String ngoId, String userId);
}
