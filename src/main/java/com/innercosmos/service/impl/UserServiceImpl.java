package com.innercosmos.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.common.Constants;
import com.innercosmos.common.ErrorCode;
import com.innercosmos.dto.LoginRequest;
import com.innercosmos.dto.RegisterRequest;
import com.innercosmos.entity.User;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.mapper.UserMapper;
import com.innercosmos.service.UserService;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;

@Service
public class UserServiceImpl implements UserService {
    private final UserMapper userMapper;

    public UserServiceImpl(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public User register(RegisterRequest request) {
        QueryWrapper<User> query = new QueryWrapper<>();
        query.eq("username", request.username);
        if (userMapper.selectCount(query) > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "username already exists");
        }
        User user = new User();
        user.username = request.username;
        user.passwordHash = hash(request.password);
        user.nickname = request.nickname == null || request.nickname.isBlank() ? request.username : request.nickname;
        user.email = request.email;
        user.role = Constants.ROLE_USER;
        user.status = Constants.STATUS_ACTIVE;
        userMapper.insert(user);
        return user;
    }

    @Override
    public User login(LoginRequest request) {
        QueryWrapper<User> query = new QueryWrapper<>();
        query.eq("username", request.username);
        User user = userMapper.selectOne(query);
        if (user == null || !verify(request.password, user.passwordHash)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "invalid username or password");
        }
        user.lastLoginAt = LocalDateTime.now();
        userMapper.updateById(user);
        return user;
    }

    @Override
    public User current(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "not logged in");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "not logged in");
        }
        return user;
    }

    private String hash(String input) {
        if (input == null) throw new BusinessException(ErrorCode.BAD_REQUEST, "密码不能为空");
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        String saltHex = bytesToHex(salt);
        String hashHex = sha256(saltHex + input);
        return saltHex + ":" + hashHex;
    }

    private boolean verify(String input, String stored) {
        if (input == null || stored == null) return false;
        if (!stored.contains(":")) {
            // Legacy format - plain SHA-256
            return sha256(input).equals(stored);
        }
        String[] parts = stored.split(":");
        String salt = parts[0];
        String expectedHash = parts[1];
        return sha256(salt + input).equals(expectedHash);
    }

    private String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
