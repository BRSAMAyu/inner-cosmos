package com.innercosmos.exception;

public class AiProviderException extends BusinessException {
    public AiProviderException(String message) {
        super(com.innercosmos.common.ErrorCode.AI_PROVIDER_ERROR, message);
    }
}
