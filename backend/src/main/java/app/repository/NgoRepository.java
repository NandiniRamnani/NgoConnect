package app.repository;

import app.enums.VerificationStatus;
import app.model.Ngo;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface NgoRepository extends MongoRepository<Ngo, String> {
    boolean existsByEmail(String email);
    boolean existsByPanNumber(String panNumber);
    Ngo findByEmail(String email);
    Ngo findByResetToken(String resetToken);
    List<Ngo> findByVerificationStatus(VerificationStatus verificationStatus);
}
