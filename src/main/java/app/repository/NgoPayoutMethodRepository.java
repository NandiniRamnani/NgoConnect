package app.repository;

import app.model.NgoPayoutMethod;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;

public interface NgoPayoutMethodRepository extends MongoRepository<NgoPayoutMethod, String> {
    Optional<NgoPayoutMethod> findByNgoId(String ngoId);
}
