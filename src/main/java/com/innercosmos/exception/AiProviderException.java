package com.innercosmos.exception;

public class AiProviderException extends BusinessException {
    public AiProviderException(String message) {
        super("AI_PROVIDER_ERROR", message);
    }
}
