package com.innercosmos.service;

import com.innercosmos.dto.DeviceRegistrationRequest;
import com.innercosmos.vo.DeviceRegistrationVO;
import java.util.List;

public interface DeviceRegistrationService {
    DeviceRegistrationVO register(Long userId, String installationId, DeviceRegistrationRequest request);
    List<DeviceRegistrationVO> list(Long userId);
    void revoke(Long userId, String installationId);
}
