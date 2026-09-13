package com.innercosmos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class DeviceRegistrationRequest {
    @NotBlank @Pattern(regexp = "ANDROID|IOS|WINDOWS|MACOS") public String platform;
    // CP-41: mainland vendor transports register alongside FCM/APNS/local evidence; each
    // vendor's gateway stays credential-gated until its 渠道合同 lands (operator gate).
    @NotBlank @Pattern(regexp = "FCM|APNS|LOCAL_EVIDENCE|XIAOMI|OPPO|VIVO|HONOR|HUAWEI") public String transport;
    @Size(max = 8192) public String token;
    @NotBlank @Size(max = 64) public String appVersion;
    @NotBlank @Size(max = 32) public String locale;
    @NotBlank @Size(max = 64) public String timezone;
}
