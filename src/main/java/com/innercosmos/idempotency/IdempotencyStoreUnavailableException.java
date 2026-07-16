package com.innercosmos.idempotency;

public final class IdempotencyStoreUnavailableException extends RuntimeException {
    public IdempotencyStoreUnavailableException(Throwable cause) { super(cause); }
}
