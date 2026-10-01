package app.repository;

import app.enums.NotificationType;
import app.model.NgoNotification;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface NgoNotificationRepository extends MongoRepository<NgoNotification, String> {
    List<NgoNotification> findByActiveTrueOrderByCreatedAtDesc();
    List<NgoNotification> findByActiveTrueAndTypeOrderByCreatedAtDesc(NotificationType type);
    // Urgent first, then newest. Posts from before the flag existed have no "urgent" field, which
    // Mongo sorts below true — so they correctly land among the non-urgent ones.
    List<NgoNotification> findByActiveTrueOrderByUrgentDescCreatedAtDesc();
    List<NgoNotification> findByActiveTrueAndTypeOrderByUrgentDescCreatedAtDesc(NotificationType type);
    List<NgoNotification> findByNgoIdOrderByCreatedAtDesc(String ngoId);
}
