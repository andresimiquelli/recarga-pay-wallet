package recargapay.wallet.application.port;

import java.util.Optional;
import java.util.UUID;
import recargapay.wallet.domain.Wallet;

public interface WalletRepositoryPort {
    Wallet save(Wallet wallet);

    Optional<Wallet> findById(UUID id);

    Optional<Wallet> findByIdForUpdate(UUID id);

    Optional<Wallet> findByUserId(UUID userId);

    Optional<Wallet> findByAlias(String alias);

    boolean existsByUserId(UUID userId);

    boolean existsByAlias(String alias);
}
