package recargapay.wallet.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;

public interface JpaWalletRepository extends JpaRepository<WalletEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from WalletEntity w where w.id = :id")
    Optional<WalletEntity> findByIdForUpdate(@Param("id") UUID id);

    Optional<WalletEntity> findByUserId(UUID userId);

    Optional<WalletEntity> findByAlias(String alias);

    boolean existsByUserId(UUID userId);

    boolean existsByAlias(String alias);
}
