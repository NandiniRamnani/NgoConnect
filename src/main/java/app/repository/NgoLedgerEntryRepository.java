package app.repository;

import app.model.NgoLedgerEntry;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.time.Instant;
import java.util.List;

public interface NgoLedgerEntryRepository extends MongoRepository<NgoLedgerEntry, String> {

    /** The NGO's passbook screen, newest first. */
    List<NgoLedgerEntry> findByNgoIdOrderByCreatedAtDesc(String ngoId);

    /**
     * The clearing job's one query: donations whose settlement window has expired but which
     * have not been promoted into `available` yet. Both fields are indexed.
     */
    List<NgoLedgerEntry> findByClearedFalseAndClearsAtLessThanEqual(Instant now);
}
