package com.innercosmos.service.impl;

import com.innercosmos.common.ErrorCode;
import com.innercosmos.dto.DeviceRegistrationRequest;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.service.DeviceRegistrationService;
import com.innercosmos.service.PushTokenProtector;
import com.innercosmos.vo.DeviceRegistrationVO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class DeviceRegistrationServiceImpl implements DeviceRegistrationService {
    private final JdbcTemplate jdbc;
    private final PushTokenProtector protector;

    public DeviceRegistrationServiceImpl(JdbcTemplate jdbc, PushTokenProtector protector) {
        this.jdbc = jdbc; this.protector = protector;
    }

    @Override @Transactional
    public DeviceRegistrationVO register(Long userId, String installationId, DeviceRegistrationRequest request) {
        validateInstallationId(installationId);
        try { ZoneId.of(request.timezone); }
        catch (RuntimeException invalid) { throw new BusinessException(ErrorCode.BAD_REQUEST, "invalid timezone"); }
        String token = request.token == null ? "" : request.token.trim();
        boolean remote = !"LOCAL_EVIDENCE".equals(request.transport);
        if (remote && token.isBlank()) throw new BusinessException(ErrorCode.BAD_REQUEST, "push token is required");
        if (remote && !protector.available()) {
            throw new BusinessException(ErrorCode.CONFLICT, "EXTERNAL_CREDENTIAL_GATE: push token encryption key is not configured");
        }
        List<Long> owners = jdbc.queryForList("SELECT user_id FROM tb_device_registration WHERE installation_id=?", Long.class, installationId);
        if (!owners.isEmpty() && !userId.equals(owners.getFirst())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "device not found");
        }
        String hash = token.isBlank() ? null : protector.hash(token);
        String protectedToken = token.isBlank() ? null : protector.protect(token).orElse(null);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        jdbc.update("""
            INSERT INTO tb_device_registration
              (user_id,installation_id,platform,transport,token_hash,token_ciphertext,app_version,locale,timezone,enabled,revoked,last_seen_at,created_at,updated_at)
            VALUES (?,?,?,?,?,?,?,?,?,TRUE,FALSE,?,?,?)
            ON CONFLICT (installation_id) DO UPDATE SET platform=EXCLUDED.platform, transport=EXCLUDED.transport,
              token_hash=EXCLUDED.token_hash, token_ciphertext=EXCLUDED.token_ciphertext,
              app_version=EXCLUDED.app_version, locale=EXCLUDED.locale, timezone=EXCLUDED.timezone,
              enabled=TRUE, revoked=FALSE, last_seen_at=EXCLUDED.last_seen_at, updated_at=EXCLUDED.updated_at
            WHERE tb_device_registration.user_id=EXCLUDED.user_id
            """, userId, installationId, request.platform, request.transport, hash, protectedToken,
            request.appVersion, request.locale, request.timezone, now, now, now);
        return owned(userId, installationId);
    }

    @Override
    public List<DeviceRegistrationVO> list(Long userId) {
        return jdbc.query("SELECT id,installation_id,platform,transport,app_version,locale,timezone,enabled,revoked,last_seen_at " +
            "FROM tb_device_registration WHERE user_id=? ORDER BY last_seen_at DESC", (rs, row) -> map(rs), userId);
    }

    @Override @Transactional
    public void revoke(Long userId, String installationId) {
        validateInstallationId(installationId);
        int changed = jdbc.update("UPDATE tb_device_registration SET enabled=FALSE, revoked=TRUE, token_hash=NULL, " +
            "token_ciphertext=NULL, updated_at=? WHERE user_id=? AND installation_id=?",
            LocalDateTime.now(ZoneOffset.UTC), userId, installationId);
        if (changed == 0) throw new BusinessException(ErrorCode.NOT_FOUND, "device not found");
    }

    private DeviceRegistrationVO owned(Long userId, String installationId) {
        List<DeviceRegistrationVO> rows = jdbc.query("SELECT id,installation_id,platform,transport,app_version,locale,timezone,enabled,revoked,last_seen_at " +
            "FROM tb_device_registration WHERE user_id=? AND installation_id=?", (rs, row) -> map(rs), userId, installationId);
        if (rows.isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND, "device not found");
        return rows.getFirst();
    }

    private static DeviceRegistrationVO map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new DeviceRegistrationVO(rs.getLong("id"), rs.getString("installation_id"), rs.getString("platform"),
            rs.getString("transport"), rs.getString("app_version"), rs.getString("locale"), rs.getString("timezone"),
            rs.getBoolean("enabled"), rs.getBoolean("revoked"), rs.getTimestamp("last_seen_at").toLocalDateTime());
    }

    private static void validateInstallationId(String value) {
        if (value == null || !value.matches("[A-Za-z0-9][A-Za-z0-9._:-]{15,127}")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "invalid installation id");
        }
    }
}
