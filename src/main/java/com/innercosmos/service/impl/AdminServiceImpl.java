package com.innercosmos.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.entity.EchoCapsule;
import com.innercosmos.entity.ModelConfig;
import com.innercosmos.entity.ReportRecord;
import com.innercosmos.entity.SafetyEvent;
import com.innercosmos.entity.SlowLetter;
import com.innercosmos.entity.User;
import com.innercosmos.mapper.AiInteractionLogMapper;
import com.innercosmos.mapper.EchoCapsuleMapper;
import com.innercosmos.mapper.ModelConfigMapper;
import com.innercosmos.mapper.ReportRecordMapper;
import com.innercosmos.mapper.SafetyEventMapper;
import com.innercosmos.mapper.SlowLetterMapper;
import com.innercosmos.mapper.UserMapper;
import com.innercosmos.service.AdminService;
import com.innercosmos.vo.AdminOverviewVO;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AdminServiceImpl implements AdminService {
    private final UserMapper userMapper;
    private final EchoCapsuleMapper capsuleMapper;
    private final ReportRecordMapper reportMapper;
    private final SafetyEventMapper safetyEventMapper;
    private final SlowLetterMapper letterMapper;
    private final AiInteractionLogMapper aiLogMapper;
    private final ModelConfigMapper modelConfigMapper;

    public AdminServiceImpl(UserMapper userMapper, EchoCapsuleMapper capsuleMapper, ReportRecordMapper reportMapper, SafetyEventMapper safetyEventMapper, SlowLetterMapper letterMapper, AiInteractionLogMapper aiLogMapper, ModelConfigMapper modelConfigMapper) {
        this.userMapper = userMapper;
        this.capsuleMapper = capsuleMapper;
        this.reportMapper = reportMapper;
        this.safetyEventMapper = safetyEventMapper;
        this.letterMapper = letterMapper;
        this.aiLogMapper = aiLogMapper;
        this.modelConfigMapper = modelConfigMapper;
    }

    public List<User> users() { return userMapper.selectList(null); }

    public List<EchoCapsule> capsules() { return capsuleMapper.selectList(null); }

    public List<ReportRecord> reports() { return reportMapper.selectList(null); }

    @Override
    public AdminOverviewVO overview() {
        AdminOverviewVO vo = new AdminOverviewVO();
        vo.totalUsers = userMapper.selectCount(null);
        vo.activeUsersToday = userMapper.selectCount(new QueryWrapper<User>().apply("DATE(last_login_at) = CURDATE()"));
        vo.totalCapsules = capsuleMapper.selectCount(null);
        vo.publicCapsules = capsuleMapper.selectCount(new QueryWrapper<EchoCapsule>().eq("is_public", true));
        vo.totalLetters = letterMapper.selectCount(null);
        vo.pendingLetters = letterMapper.selectCount(new QueryWrapper<SlowLetter>().in("status", "DRAFT", "FLYING"));
        vo.totalAiLogs = aiLogMapper.selectCount(null);
        vo.safetyEvents = safetyEventMapper.selectCount(null);
        vo.pendingReports = reportMapper.selectCount(new QueryWrapper<ReportRecord>().eq("status", "PENDING"));
        return vo;
    }

    @Override
    public List<EchoCapsule> capsules(String status, String keyword) {
        QueryWrapper<EchoCapsule> query = new QueryWrapper<>();
        if (status != null && !status.isBlank()) {
            query.eq("visibility_status", status);
        }
        if (keyword != null && !keyword.isBlank()) {
            query.and(w -> w.like("pseudonym", keyword).or().like("intro", keyword));
        }
        query.orderByDesc("id");
        return capsuleMapper.selectList(query);
    }

    @Override
    public void hideCapsule(Long capsuleId) {
        EchoCapsule capsule = capsuleMapper.selectById(capsuleId);
        if (capsule == null) {
            throw new com.innercosmos.exception.BusinessException(com.innercosmos.common.ErrorCode.NOT_FOUND, "共鸣体不存在");
        }
        capsule.visibilityStatus = "HIDDEN";
        capsule.isPublic = false;
        capsuleMapper.updateById(capsule);
    }

    @Override
    public void restoreCapsule(Long capsuleId) {
        EchoCapsule capsule = capsuleMapper.selectById(capsuleId);
        if (capsule == null) {
            throw new com.innercosmos.exception.BusinessException(com.innercosmos.common.ErrorCode.NOT_FOUND, "共鸣体不存在");
        }
        capsule.visibilityStatus = "PUBLIC";
        capsule.isPublic = true;
        capsuleMapper.updateById(capsule);
    }

    @Override
    public List<ReportRecord> reports(String status) {
        QueryWrapper<ReportRecord> query = new QueryWrapper<>();
        if (status != null && !status.isBlank()) {
            query.eq("status", status);
        }
        query.orderByDesc("id");
        return reportMapper.selectList(query);
    }

    @Override
    public void resolveReport(Long reportId, String action) {
        ReportRecord report = reportMapper.selectById(reportId);
        if (report == null) {
            throw new com.innercosmos.exception.BusinessException(com.innercosmos.common.ErrorCode.NOT_FOUND, "举报记录不存在");
        }
        report.status = "RESOLVED";
        reportMapper.updateById(report);
        if ("HIDE".equals(action) && "CAPSULE".equals(report.targetType)) {
            hideCapsule(report.targetId);
        }
    }

    @Override
    public List<SafetyEvent> safetyEvents() {
        return safetyEventMapper.selectList(new QueryWrapper<SafetyEvent>().orderByDesc("id"));
    }

    @Override
    public List<ModelConfig> modelConfigs() {
        return modelConfigMapper.selectList(null);
    }

    @Override
    public void updateModelConfig(ModelConfig config) {
        if (config.id == null) {
            modelConfigMapper.insert(config);
        } else {
            modelConfigMapper.updateById(config);
        }
    }

    @Override
    public void disableUser(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new com.innercosmos.exception.BusinessException(
                    com.innercosmos.common.ErrorCode.NOT_FOUND, "用户不存在");
        }
        user.status = "DISABLED";
        userMapper.updateById(user);
    }

    @Override
    public void enableUser(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new com.innercosmos.exception.BusinessException(
                    com.innercosmos.common.ErrorCode.NOT_FOUND, "用户不存在");
        }
        user.status = "ACTIVE";
        userMapper.updateById(user);
    }
}
