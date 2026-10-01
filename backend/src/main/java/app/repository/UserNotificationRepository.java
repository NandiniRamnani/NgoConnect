package app.repository;

import app.model.UserNotification;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface UserNotificationRepository extends MongoRepository<UserNotification, String> {
    List<UserNotification> findByUserEmailOrderByCreatedAtDesc(String userEmail);
    List<UserNotification> findByUserEmailAndReadFalse(String userEmail);
    long countByUserEmailAndReadFalse(String userEmail);
}
