package app.repository;

import app.model.Donation;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface DonationRepository extends MongoRepository<Donation, String> {
    List<Donation> findByUserIdOrderByCreatedAtDesc(String userId);
    List<Donation> findByNgoIdOrderByCreatedAtDesc(String ngoId);
    java.util.Optional<Donation> findByRazorpayOrderId(String razorpayOrderId);
}
