package recargapay.wallet.adapter.out.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaIdempotencyRepository extends JpaRepository<IdempotencyEntity, UUID> {
    Optional<IdempotencyEntity> findByIdempotencyKey(String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from IdempotencyEntity i where i.idempotencyKey = :idempotencyKey")
    Optional<IdempotencyEntity> findByIdempotencyKeyForUpdate(@Param("idempotencyKey") String idempotencyKey);
}
