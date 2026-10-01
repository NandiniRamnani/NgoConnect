package app.repository;

import app.model.Account;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AccountRepository extends MongoRepository<Account, String> {

    boolean existsByEmail(String email);

    Account findByEmail(String email);

    Account findByResetToken(String resetToken);

}