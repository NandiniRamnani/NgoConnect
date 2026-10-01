package app.repository;

import app.model.WalletTransaction;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;
import java.util.Optional;

public interface WalletTransactionRepository extends MongoRepository<WalletTransaction, String> {

    /** Passbook screen: newest entry first. */
    List<WalletTransaction> findByUserIdOrderByCreatedAtDesc(String userId);

    /**
     * Razorpay hands the frontend an order id after a top-up succeeds; this is how we find
     * which pending ledger row that payment belongs to.
     */
    Optional<WalletTransaction> findByRazorpayOrderId(String razorpayOrderId);
}
