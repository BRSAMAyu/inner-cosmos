package com.innercosmos.service;

import com.innercosmos.dto.LoginRequest;
import com.innercosmos.dto.RegisterRequest;
import com.innercosmos.entity.User;

public interface UserService {
    User register(RegisterRequest request);

    User login(LoginRequest request);

    User current(Long userId);
}
