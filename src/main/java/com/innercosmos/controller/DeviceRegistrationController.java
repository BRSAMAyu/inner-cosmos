package com.innercosmos.controller;

import com.innercosmos.common.ApiResponse;
import com.innercosmos.dto.DeviceRegistrationRequest;
import com.innercosmos.service.DeviceRegistrationService;
import com.innercosmos.vo.DeviceRegistrationVO;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/devices")
public class DeviceRegistrationController extends BaseController {
    private final DeviceRegistrationService devices;
    public DeviceRegistrationController(DeviceRegistrationService devices) { this.devices = devices; }

    @PutMapping("/{installationId}")
    public ApiResponse<DeviceRegistrationVO> register(@PathVariable String installationId,
                                                       @Valid @RequestBody DeviceRegistrationRequest request,
                                                       HttpSession session) {
        return ApiResponse.ok(devices.register(currentUserId(session), installationId, request));
    }

    @GetMapping
    public ApiResponse<List<DeviceRegistrationVO>> list(HttpSession session) {
        return ApiResponse.ok(devices.list(currentUserId(session)));
    }

    @DeleteMapping("/{installationId}")
    public ApiResponse<Void> revoke(@PathVariable String installationId, HttpSession session) {
        devices.revoke(currentUserId(session), installationId);
        return ApiResponse.ok(null);
    }
}
