package recargapay.wallet.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import recargapay.wallet.application.port.WalletRepositoryPort;
import recargapay.wallet.domain.Wallet;

@Component
public class WalletPersistenceAdapter implements WalletRepositoryPort {
    private final JpaWalletRepository jpaWalletRepository;

    public WalletPersistenceAdapter(JpaWalletRepository jpaWalletRepository) {
        this.jpaWalletRepository = jpaWalletRepository;
    }

    @Override
    public Wallet save(Wallet wallet) {
        WalletEntity walletEntity = toEntity(wallet);
        WalletEntity savedWalletEntity = jpaWalletRepository.save(walletEntity);
        return toDomain(savedWalletEntity);
    }

    @Override
    public Optional<Wallet> findById(UUID id) {
        return jpaWalletRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<Wallet> findByIdForUpdate(UUID id) {
        return jpaWalletRepository.findByIdForUpdate(id).map(this::toDomain);
    }

    @Override
    public Optional<Wallet> findByUserId(UUID userId) {
        return jpaWalletRepository.findByUserId(userId).map(this::toDomain);
    }

    @Override
    public Optional<Wallet> findByAlias(String alias) {
        return jpaWalletRepository.findByAlias(alias).map(this::toDomain);
    }

    @Override
    public boolean existsByUserId(UUID userId) {
        return jpaWalletRepository.existsByUserId(userId);
    }

    @Override
    public boolean existsByAlias(String alias) {
        return jpaWalletRepository.existsByAlias(alias);
    }

    private WalletEntity toEntity(Wallet wallet) {
        WalletEntity walletEntity = new WalletEntity();
        walletEntity.setId(wallet.getId());
        walletEntity.setUserId(wallet.getUserId());
        walletEntity.setAlias(wallet.getAlias());
        walletEntity.setCurrentBalance(wallet.getCurrentBalance());
        walletEntity.setCreatedAt(wallet.getCreatedAt());
        walletEntity.setUpdatedAt(wallet.getUpdatedAt());
        return walletEntity;
    }

    private Wallet toDomain(WalletEntity walletEntity) {
        Wallet wallet = new Wallet();
        wallet.setId(walletEntity.getId());
        wallet.setUserId(walletEntity.getUserId());
        wallet.setAlias(walletEntity.getAlias());
        wallet.setCurrentBalance(walletEntity.getCurrentBalance());
        wallet.setCreatedAt(walletEntity.getCreatedAt());
        wallet.setUpdatedAt(walletEntity.getUpdatedAt());
        return wallet;
    }
}
