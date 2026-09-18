package app.repository;

import app.model.EventEnrollment;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface EventEnrollmentRepository extends MongoRepository<EventEnrollment, String> {
    List<EventEnrollment> findByEventId(String eventId);
    List<EventEnrollment> findByUserId(String userId);
    boolean existsByEventIdAndUserId(String eventId, String userId);
    List<EventEnrollment> findByEventIdAndAttended(String eventId, Boolean attended);
}
