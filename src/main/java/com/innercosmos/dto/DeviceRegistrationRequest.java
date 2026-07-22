package com.innercosmos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class DeviceRegistrationRequest {
    @NotBlank @Pattern(regexp = "ANDROID|IOS|WINDOWS|MACOS") public String platform;
    @NotBlank @Pattern(regexp = "FCM|APNS|LOCAL_EVIDENCE") public String transport;
    @Size(max = 8192) public String token;
    @NotBlank @Size(max = 64) public String appVersion;
    @NotBlank @Size(max = 32) public String locale;
    @NotBlank @Size(max = 64) public String timezone;
}
