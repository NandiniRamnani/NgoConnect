package app.repository;

import app.enums.WithdrawalStatus;
import app.model.WithdrawalRequest;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface WithdrawalRequestRepository extends MongoRepository<WithdrawalRequest, String> {

    /** The NGO's own history. */
    List<WithdrawalRequest> findByNgoIdOrderByRequestedAtDesc(String ngoId);

    /** The admin queue, filtered by status. */
    List<WithdrawalRequest> findByStatusOrderByRequestedAtAsc(WithdrawalStatus status);

    /** Badge count on the admin dashboard. */
    long countByStatus(WithdrawalStatus status);
}
