package com.innercosmos.idempotency;

public record CachedHttpResponse(int status, String contentType, byte[] body, String etag) {
    public CachedHttpResponse(int status, String contentType, byte[] body) {
        this(status, contentType, body, null);
    }

    public CachedHttpResponse {
        body = body == null ? new byte[0] : body.clone();
    }

    @Override
    public byte[] body() {
        return body.clone();
    }
}
