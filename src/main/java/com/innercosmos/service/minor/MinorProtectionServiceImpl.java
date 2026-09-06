package com.innercosmos.service.minor;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.innercosmos.common.ErrorCode;
import com.innercosmos.entity.MinorAppeal;
import com.innercosmos.entity.User;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.mapper.MinorAppealMapper;
import com.innercosmos.mapper.UserMapper;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class MinorProtectionServiceImpl implements MinorProtectionService {

    private static final String RESTRICTION_MESSAGE =
            "本服务仅面向成年人。当前账户已进入未成年人保护状态：AI 陪伴与社交功能已停用，"
                    + "你的账号数据与安全支持资源仍然可用。如认为这是误判，可以提交申诉，"
                    + "我们会尽快人工复核。";

    private final UserMapper userMapper;
    private final MinorAppealMapper appealMapper;

    public MinorProtectionServiceImpl(UserMapper userMapper, MinorAppealMapper appealMapper) {
        this.userMapper = userMapper;
        this.appealMapper = appealMapper;
    }

    @Override
    public void assertAdultAccess(Long userId) {
        User user = userMapper.selectById(userId);
        if (user != null && STATUS_MINOR_RESTRICTED.equals(user.status)) {
            throw new BusinessException(ErrorCode.ADULT_GATE_REQUIRED, RESTRICTION_MESSAGE);
        }
    }

    @Override
    public boolean flagMinor(Long userId, String reason) {
        int updated = userMapper.update(null, new UpdateWrapper<User>()
                .eq("id", userId).ne("status", STATUS_MINOR_RESTRICTED)
                .set("status", STATUS_MINOR_RESTRICTED)
                .set("updated_at", LocalDateTime.now(ZoneOffset.UTC)));
        return updated == 1;
    }

    @Override
    public MinorAppeal appeal(Long userId, String statement) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "账户不存在");
        }
        if (!STATUS_MINOR_RESTRICTED.equals(user.status)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "账户未处于未成年人保护状态，无需申诉");
        }
        if (statement == null || statement.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请填写申诉说明");
        }
        Long pending = appealMapper.selectCount(new QueryWrapper<MinorAppeal>()
                .eq("user_id", userId).eq("status", "PENDING"));
        if (pending != null && pending > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "你已有一条待处理的申诉");
        }
        MinorAppeal row = new MinorAppeal();
        row.userId = userId;
        row.statement = statement.strip();
        row.status = "PENDING";
        appealMapper.insert(row);
        return row;
    }

    @Override
    public MinorAppeal resolve(Long appealId, boolean accept, Long adminUserId, String note) {
        MinorAppeal appeal = appealMapper.selectById(appealId);
        if (appeal == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "申诉不存在");
        }
        if (!"PENDING".equals(appeal.status)) {
            throw new BusinessException(ErrorCode.CONFLICT, "该申诉已处理");
        }
        appeal.status = accept ? "ACCEPTED" : "REJECTED";
        appeal.decidedBy = adminUserId;
        appeal.decisionNote = note == null ? null
                : note.strip().substring(0, Math.min(400, note.strip().length()));
        appealMapper.updateById(appeal);
        if (accept) {
            // A misjudged adult is restorable: the restriction lifts immediately; the age
            // record correction itself is a separate reviewed action (CP-13 identity flow).
            userMapper.update(null, new UpdateWrapper<User>()
                    .eq("id", appeal.userId).eq("status", STATUS_MINOR_RESTRICTED)
                    .set("status", "ACTIVE")
                    .set("updated_at", LocalDateTime.now(ZoneOffset.UTC)));
        }
        return appeal;
    }

    @Override
    public List<MinorAppeal> pendingAppeals() {
        return appealMapper.selectList(new QueryWrapper<MinorAppeal>()
                .eq("status", "PENDING").orderByAsc("id"));
    }
}
