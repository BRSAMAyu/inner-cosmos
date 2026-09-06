package com.innercosmos.service.identity;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.innercosmos.common.Constants;
import com.innercosmos.common.ErrorCode;
import com.innercosmos.entity.AccountSecurityEvent;
import com.innercosmos.entity.User;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.mapper.AccountSecurityEventMapper;
import com.innercosmos.mapper.UserMapper;
import com.innercosmos.service.identity.AccountSecurityService.EventRow;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountSecurityServiceImpl implements AccountSecurityService {

    private final UserMapper userMapper;
    private final AccountSecurityEventMapper securityEventMapper;
    private final JdbcTemplate jdbc;

    public AccountSecurityServiceImpl(UserMapper userMapper,
                                      AccountSecurityEventMapper securityEventMapper,
                                      JdbcTemplate jdbc) {
        this.userMapper = userMapper;
        this.securityEventMapper = securityEventMapper;
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int revokeAllDevices(Long userId) {
        requireUser(userId);
        int revoked = jdbc.update(
                "UPDATE tb_device_registration SET enabled=FALSE, revoked=TRUE, "
                        + "token_hash=NULL, token_ciphertext=NULL, updated_at=? "
                        + "WHERE user_id=? AND revoked=FALSE",
                LocalDateTime.now(ZoneOffset.UTC), userId);
        audit(userId, "DEVICES_REVOKED_ALL", userId, "count=" + revoked);
        return revoked;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean freeze(Long userId, Long actorId, String reason) {
        User user = requireUser(userId);
        if (STATUS_FROZEN.equals(user.status)) {
            return false;
        }
        if (!Constants.STATUS_ACTIVE.equals(user.status)) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "仅正常状态的账户可以冻结（当前：" + user.status + "）");
        }
        userMapper.update(null, new UpdateWrapper<User>()
                .eq("id", userId).eq("status", Constants.STATUS_ACTIVE)
                .set("status", STATUS_FROZEN)
                .set("updated_at", LocalDateTime.now(ZoneOffset.UTC)));
        audit(userId, "ACCOUNT_FROZEN", actorId, reason);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean unfreeze(Long userId, Long actorId, String note) {
        User user = requireUser(userId);
        if (!STATUS_FROZEN.equals(user.status)) {
            return false;
        }
        userMapper.update(null, new UpdateWrapper<User>()
                .eq("id", userId).eq("status", STATUS_FROZEN)
                .set("status", Constants.STATUS_ACTIVE)
                .set("updated_at", LocalDateTime.now(ZoneOffset.UTC)));
        audit(userId, "ACCOUNT_UNFROZEN", actorId, note);
        return true;
    }

    @Override
    public SecuritySnapshot snapshot(Long userId) {
        User user = requireUser(userId);
        Long activeDevices = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tb_device_registration WHERE user_id=? AND revoked=FALSE",
                Long.class, userId);
        List<EventRow> events = recentEvents(userId, 10).stream()
                .map(event -> new EventRow(event.action,
                        event.actorId == null ? null : String.valueOf(event.actorId),
                        event.detail,
                        event.createdAt == null ? null : event.createdAt.toString()))
                .toList();
        return new SecuritySnapshot(user.status, user.accountKind,
                user.ageGateMethod,
                user.lastLoginAt == null ? null : user.lastLoginAt.toString(),
                activeDevices == null ? 0 : activeDevices, events);
    }

    @Override
    public List<AccountSecurityEvent> recentEvents(Long userId, int limit) {
        int bounded = Math.max(1, Math.min(50, limit));
        return securityEventMapper.selectList(new QueryWrapper<AccountSecurityEvent>()
                .eq("user_id", userId).orderByDesc("id").last("LIMIT " + bounded));
    }

    private User requireUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "账户不存在");
        }
        return user;
    }

    private void audit(Long userId, String action, Long actorId, String detail) {
        AccountSecurityEvent event = new AccountSecurityEvent();
        event.userId = userId;
        event.action = action;
        event.actorId = actorId;
        event.detail = detail;
        securityEventMapper.insert(event);
    }
}
