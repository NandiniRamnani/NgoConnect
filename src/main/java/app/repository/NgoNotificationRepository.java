package app.repository;

import app.enums.NotificationType;
import app.model.NgoNotification;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface NgoNotificationRepository extends MongoRepository<NgoNotification, String> {
    List<NgoNotification> findByActiveTrueOrderByCreatedAtDesc();
    List<NgoNotification> findByActiveTrueAndTypeOrderByCreatedAtDesc(NotificationType type);
    List<NgoNotification> findByNgoIdOrderByCreatedAtDesc(String ngoId);
}
