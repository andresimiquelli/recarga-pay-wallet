package recargapay.wallet.adapter.in.api.exception;

import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import recargapay.wallet.application.exception.IdempotencyKeyConflictException;
import recargapay.wallet.application.exception.IdempotencyRequestInProgressException;
import recargapay.wallet.application.exception.InsufficientWalletBalanceException;
import recargapay.wallet.application.exception.InvalidTransferOperationException;
import recargapay.wallet.application.exception.InvalidWalletAliasException;
import recargapay.wallet.application.exception.InvalidTransactionAmountException;
import recargapay.wallet.application.exception.MissingIdempotencyKeyException;
import recargapay.wallet.application.exception.WalletAliasAlreadyInUseException;
import recargapay.wallet.application.exception.WalletAlreadyExistsForUserException;
import recargapay.wallet.application.exception.WalletNotFoundException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(WalletNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleWalletNotFound(WalletNotFoundException exception) {
        return buildResponse(HttpStatus.NOT_FOUND, exception.getMessage(), List.of());
    }

    @ExceptionHandler({
            InvalidTransferOperationException.class,
            InvalidWalletAliasException.class,
            InvalidTransactionAmountException.class,
            MethodArgumentNotValidException.class,
            ConstraintViolationException.class,
            MissingIdempotencyKeyException.class
    })
    public ResponseEntity<ApiErrorResponse> handleBadRequest(Exception exception) {
        if (exception instanceof MethodArgumentNotValidException methodArgumentNotValidException) {
            List<String> details = methodArgumentNotValidException.getBindingResult().getFieldErrors().stream()
                    .map(this::formatFieldError)
                    .toList();
            return buildResponse(HttpStatus.BAD_REQUEST, "request validation failed", details);
        }

        if (exception instanceof ConstraintViolationException constraintViolationException) {
            List<String> details = constraintViolationException.getConstraintViolations().stream()
                    .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                    .toList();
            return buildResponse(HttpStatus.BAD_REQUEST, "request validation failed", details);
        }

        return buildResponse(HttpStatus.BAD_REQUEST, exception.getMessage(), List.of());
    }

    @ExceptionHandler({
            InsufficientWalletBalanceException.class,
            WalletAlreadyExistsForUserException.class,
            WalletAliasAlreadyInUseException.class,
            IdempotencyKeyConflictException.class,
            IdempotencyRequestInProgressException.class
    })
    public ResponseEntity<ApiErrorResponse> handleConflict(RuntimeException exception) {
        return buildResponse(HttpStatus.CONFLICT, exception.getMessage(), List.of());
    }

    private ResponseEntity<ApiErrorResponse> buildResponse(
            HttpStatus status, String message, List<String> details) {
        ApiErrorResponse response = new ApiErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                details);
        return ResponseEntity.status(status).body(response);
    }

    private String formatFieldError(FieldError fieldError) {
        return fieldError.getField() + ": " + fieldError.getDefaultMessage();
    }
}
