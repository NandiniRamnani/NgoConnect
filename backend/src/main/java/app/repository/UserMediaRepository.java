package app.repository;

import app.model.UserMedia;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;
public interface UserMediaRepository extends MongoRepository<UserMedia, String> {
    List<UserMedia> findByAccountIdOrderByCreatedAtDesc(String accountId);
}
