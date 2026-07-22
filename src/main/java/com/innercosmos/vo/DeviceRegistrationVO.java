package com.innercosmos.vo;

import java.time.LocalDateTime;

/** Safe projection: push token and token hash are deliberately never returned. */
public record DeviceRegistrationVO(Long id, String installationId, String platform, String transport,
                                   String appVersion, String locale, String timezone,
                                   boolean enabled, boolean revoked, LocalDateTime lastSeenAt) {}
