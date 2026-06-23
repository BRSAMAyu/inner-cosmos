package com.innercosmos.service;

import com.innercosmos.dto.LoginRequest;
import com.innercosmos.dto.RegisterRequest;
import com.innercosmos.entity.User;
import com.innercosmos.vo.UserProfileVO;

import java.util.Map;

public interface UserService {
    User register(RegisterRequest request);

    User login(LoginRequest request);

    User current(Long userId);

    void updateProfile(Long userId, UserProfileVO profile);

    Map<String, Object> exportData(Long userId);

    void deleteAccount(Long userId);

    /** M-032: change the current user's password (requires the old password). */
    void changePassword(Long userId, String oldPassword, String newPassword);
}
