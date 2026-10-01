package app.repository;

import app.model.NgoMedia;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;
public interface NgoMediaRepository extends MongoRepository<NgoMedia, String> {
    List<NgoMedia> findByNgoIdOrderByCreatedAtDesc(String ngoId);
}
