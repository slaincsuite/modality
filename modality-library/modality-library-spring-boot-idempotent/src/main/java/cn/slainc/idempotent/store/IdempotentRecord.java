package cn.slainc.idempotent.store;

public record IdempotentRecord(IdempotentState state, String className, String payload, long createdAt) {

    public static IdempotentRecord pending() {
        return new IdempotentRecord(IdempotentState.PENDING, null, null, System.currentTimeMillis());
    }

    public static IdempotentRecord token(String owner) {
        return new IdempotentRecord(IdempotentState.TOKEN, null, owner, System.currentTimeMillis());
    }

    public boolean isPending() {
        return state == IdempotentState.PENDING;
    }

    public boolean hasPayload() {
        return payload != null && !payload.isBlank();
    }
}
