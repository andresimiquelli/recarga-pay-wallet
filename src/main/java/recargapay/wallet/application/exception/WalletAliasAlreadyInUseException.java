package recargapay.wallet.application.exception;

public class WalletAliasAlreadyInUseException extends RuntimeException {
    public WalletAliasAlreadyInUseException(String alias) {
        super("wallet alias is already in use: " + alias);
    }
}
