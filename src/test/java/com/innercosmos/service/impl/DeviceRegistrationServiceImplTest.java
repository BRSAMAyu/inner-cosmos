package com.innercosmos.service.impl;

import com.innercosmos.dto.DeviceRegistrationRequest;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.service.PushTokenProtector;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DeviceRegistrationServiceImplTest {
    private static DeviceRegistrationRequest request(String transport, String token) {
        var request = new DeviceRegistrationRequest();
        request.platform = "ANDROID"; request.transport = transport; request.token = token;
        request.appVersion = "1.0"; request.locale = "en-SG"; request.timezone = "Asia/Singapore";
        return request;
    }

    @Test void remoteRegistrationFailsClosedWithoutProtectionKey() {
        var service = new DeviceRegistrationServiceImpl(mock(JdbcTemplate.class), new PushTokenProtector(""));
        BusinessException failure = assertThrows(BusinessException.class,
            () -> service.register(1L, "installation-123456789", request("FCM", "token")));
        assertEquals("CONFLICT", failure.code);
        assertTrue(failure.getMessage().contains("EXTERNAL_CREDENTIAL_GATE"));
    }

    @Test void cannotClaimAnInstallationOwnedByAnotherUser() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), eq(Long.class), any())).thenReturn(List.of(2L));
        var key = Base64.getEncoder().encodeToString(new byte[32]);
        var service = new DeviceRegistrationServiceImpl(jdbc, new PushTokenProtector(key));
        BusinessException failure = assertThrows(BusinessException.class,
            () -> service.register(1L, "installation-123456789", request("FCM", "token")));
        assertEquals("NOT_FOUND", failure.code);
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test void revokeIsOpaqueWhenTheOwnedRowDoesNotExist() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), any(), any(), any())).thenReturn(0);
        var service = new DeviceRegistrationServiceImpl(jdbc, new PushTokenProtector(""));
        BusinessException failure = assertThrows(BusinessException.class,
            () -> service.revoke(1L, "installation-123456789"));
        assertEquals("NOT_FOUND", failure.code);
    }
}
