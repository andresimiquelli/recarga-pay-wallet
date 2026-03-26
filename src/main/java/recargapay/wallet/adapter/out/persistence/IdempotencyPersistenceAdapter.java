package recargapay.wallet.adapter.out.persistence;

import java.util.Optional;
import org.springframework.stereotype.Component;
import recargapay.wallet.application.idempotency.IdempotencyRecord;
import recargapay.wallet.application.port.IdempotencyRepositoryPort;

@Component
public class IdempotencyPersistenceAdapter implements IdempotencyRepositoryPort {
    private final JpaIdempotencyRepository jpaIdempotencyRepository;

    public IdempotencyPersistenceAdapter(JpaIdempotencyRepository jpaIdempotencyRepository) {
        this.jpaIdempotencyRepository = jpaIdempotencyRepository;
    }

    @Override
    public Optional<IdempotencyRecord> findByIdempotencyKeyForUpdate(String idempotencyKey) {
        return jpaIdempotencyRepository.findByIdempotencyKeyForUpdate(idempotencyKey).map(this::toDomain);
    }

    @Override
    public IdempotencyRecord save(IdempotencyRecord record) {
        return toDomain(jpaIdempotencyRepository.save(toEntity(record)));
    }

    @Override
    public void delete(IdempotencyRecord record) {
        jpaIdempotencyRepository.delete(toEntity(record));
    }

    @Override
    public void flush() {
        jpaIdempotencyRepository.flush();
    }

    private IdempotencyEntity toEntity(IdempotencyRecord record) {
        IdempotencyEntity entity = new IdempotencyEntity();
        entity.setId(record.getId());
        entity.setIdempotencyKey(record.getIdempotencyKey());
        entity.setRequestHash(record.getRequestHash());
        entity.setStatus(record.getStatus());
        entity.setResponseStatus(record.getResponseStatus());
        entity.setResponseBody(record.getResponseBody());
        entity.setResponseContentType(record.getResponseContentType());
        entity.setCreatedAt(record.getCreatedAt());
        entity.setExpiresAt(record.getExpiresAt());
        return entity;
    }

    private IdempotencyRecord toDomain(IdempotencyEntity entity) {
        IdempotencyRecord record = new IdempotencyRecord();
        record.setId(entity.getId());
        record.setIdempotencyKey(entity.getIdempotencyKey());
        record.setRequestHash(entity.getRequestHash());
        record.setStatus(entity.getStatus());
        record.setResponseStatus(entity.getResponseStatus());
        record.setResponseBody(entity.getResponseBody());
        record.setResponseContentType(entity.getResponseContentType());
        record.setCreatedAt(entity.getCreatedAt());
        record.setExpiresAt(entity.getExpiresAt());
        return record;
    }
}
