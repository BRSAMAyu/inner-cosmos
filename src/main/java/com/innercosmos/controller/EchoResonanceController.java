package com.innercosmos.controller;

import com.innercosmos.common.ApiResponse;
import com.innercosmos.service.CapsuleService;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * M-067: closed "this landed for me" resonance signal — a visitor who feels a capsule echo
 * resonated can mark it, bumping the capsule's echoEnergy (the slow-social promise made felt).
 * No new table: echoEnergy already lives on tb_echo_capsule.
 */
@RestController
@RequestMapping("/api/echo")
public class EchoResonanceController extends BaseController {
    private final CapsuleService capsuleService;

    public EchoResonanceController(CapsuleService capsuleService) {
        this.capsuleService = capsuleService;
    }

    @PostMapping("/{capsuleId}/landed")
    public ApiResponse<Map<String, Object>> landed(@PathVariable Long capsuleId, HttpSession session) {
        Double newEnergy = capsuleService.markLanded(currentUserId(session), capsuleId);
        return ApiResponse.ok(Map.of("echoEnergy", newEnergy));
    }
}
