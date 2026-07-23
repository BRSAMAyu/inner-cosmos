package com.innercosmos.config;

import com.innercosmos.common.ApiErrorResponse;
import com.innercosmos.common.ErrorCode;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;

import static org.assertj.core.api.Assertions.assertThat;

class ApiContractErrorTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void businessErrorsUseOneTypedEnvelopeAndSemanticHttpStatus() {
        var response = handler.handleBusiness(new BusinessException(ErrorCode.CONFLICT, "stale version"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isInstanceOf(ApiErrorResponse.class);
        ApiErrorResponse error = response.getBody();
        assertThat(error.success()).isFalse();
        assertThat(error.code()).isEqualTo(ErrorCode.CONFLICT);
        assertThat(error.status()).isEqualTo(409);
        assertThat(error.traceId()).isNotBlank();
        assertThat(error.timestamp()).isNotBlank();
    }

    @Test
    void malformedJsonBodyReturns400NotAGeneric500() {
        // 2026-07-24 8-agent audit P2-12: reproduced live -- invalid-UTF-8/malformed JSON bodies
        // (Jackson HttpMessageNotReadableException) fell through to the catch-all 500 handler.
        var response = handler.handleMalformedBody(new HttpMessageNotReadableException("JSON parse error"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ApiErrorResponse error = response.getBody();
        assertThat(error).isNotNull();
        assertThat(error.success()).isFalse();
        assertThat(error.status()).isEqualTo(400);
    }
}
