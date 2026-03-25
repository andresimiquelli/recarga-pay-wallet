package recargapay.wallet.application.exception;

public class InvalidTransferOperationException extends RuntimeException {
    public InvalidTransferOperationException(String message) {
        super(message);
    }
}
