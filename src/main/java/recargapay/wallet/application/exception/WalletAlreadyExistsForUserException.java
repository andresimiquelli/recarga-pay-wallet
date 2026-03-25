package recargapay.wallet.application.exception;

import java.util.UUID;

public class WalletAlreadyExistsForUserException extends RuntimeException {
    public WalletAlreadyExistsForUserException(UUID userId) {
        super("user already has a wallet: " + userId);
    }
}
