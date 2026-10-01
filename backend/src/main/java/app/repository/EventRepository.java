package app.repository;

import app.model.Event;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface EventRepository extends MongoRepository<Event, String> {
    List<Event> findByActiveTrueOrderByEventDateAsc();
    List<Event> findByActiveTrueAndCategoryOrderByEventDateAsc(String category);
    List<Event> findByNgoIdOrderByCreatedAtDesc(String ngoId);
}
