package com.innercosmos.controller;

import com.innercosmos.common.ApiResponse;
import com.innercosmos.common.ErrorCode;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.service.identity.AccountSecurityService;
import com.innercosmos.service.identity.IdentityVerificationService;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * CP-13 owner-facing identity and account-security surface: the VERIFIED_ID age-verification
 * upgrade chain and whole-account device control. Anonymous display vs backend verification
 * stay separated — nothing here leaks identity documents or raw codes.
 */
@RestController
@RequestMapping("/api/me")
public class IdentitySecurityController extends BaseController {

    private final IdentityVerificationService identityVerificationService;
    private final AccountSecurityService accountSecurityService;

    public IdentitySecurityController(IdentityVerificationService identityVerificationService,
                                      AccountSecurityService accountSecurityService) {
        this.identityVerificationService = identityVerificationService;
        this.accountSecurityService = accountSecurityService;
    }

    public record ConfirmRequest(String providerReference, String answer) {
    }

    @PostMapping("/identity/age-verification")
    public ApiResponse<IdentityVerificationService.ChallengeView> initiate(HttpSession session) {
        return ApiResponse.ok(identityVerificationService.initiate(currentUserId(session)));
    }

    @PostMapping("/identity/age-verification/confirm")
    public ApiResponse<IdentityVerificationService.StatusView> confirm(
            @RequestBody ConfirmRequest request, HttpSession session) {
        if (request == null || isBlank(request.providerReference()) || isBlank(request.answer())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "providerReference 与 answer 必填");
        }
        return ApiResponse.ok(identityVerificationService.confirm(
                currentUserId(session), request.providerReference(), request.answer()));
    }

    @GetMapping("/identity/age-verification")
    public ApiResponse<IdentityVerificationService.StatusView> status(HttpSession session) {
        return ApiResponse.ok(identityVerificationService.statusOf(currentUserId(session)));
    }

    @GetMapping("/security/snapshot")
    public ApiResponse<AccountSecurityService.SecuritySnapshot> snapshot(HttpSession session) {
        return ApiResponse.ok(accountSecurityService.snapshot(currentUserId(session)));
    }

    public record RevokeDevicesResult(int revoked) {
    }

    @PostMapping("/security/revoke-devices")
    public ApiResponse<RevokeDevicesResult> revokeDevices(HttpSession session) {
        int revoked = accountSecurityService.revokeAllDevices(currentUserId(session));
        return ApiResponse.ok(new RevokeDevicesResult(revoked));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
