package com.innercosmos.config;

import com.innercosmos.common.ApiErrorResponse;
import com.innercosmos.common.ErrorCode;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

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
}
