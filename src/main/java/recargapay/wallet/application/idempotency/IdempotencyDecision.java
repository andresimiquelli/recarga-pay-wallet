package recargapay.wallet.application.idempotency;

public record IdempotencyDecision(Type type, IdempotencyRecord record) {
    public enum Type {
        CLAIMED,
        REPLAY,
        IN_PROGRESS
    }

    public static IdempotencyDecision claimed(IdempotencyRecord record) {
        return new IdempotencyDecision(Type.CLAIMED, record);
    }

    public static IdempotencyDecision replay(IdempotencyRecord record) {
        return new IdempotencyDecision(Type.REPLAY, record);
    }

    public static IdempotencyDecision inProgress(IdempotencyRecord record) {
        return new IdempotencyDecision(Type.IN_PROGRESS, record);
    }
}
