package com.innercosmos.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.common.ErrorCode;
import com.innercosmos.entity.ABTestConfig;
import com.innercosmos.entity.AdminActionLog;
import com.innercosmos.entity.EchoCapsule;
import com.innercosmos.entity.ModelConfig;
import com.innercosmos.entity.ReportRecord;
import com.innercosmos.entity.SafetyEvent;
import com.innercosmos.entity.SlowLetter;
import com.innercosmos.entity.User;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.mapper.ABTestConfigMapper;
import com.innercosmos.mapper.AdminActionLogMapper;
import com.innercosmos.mapper.AiInteractionLogMapper;
import com.innercosmos.mapper.EchoCapsuleMapper;
import com.innercosmos.mapper.ModelConfigMapper;
import com.innercosmos.mapper.ReportRecordMapper;
import com.innercosmos.mapper.SafetyEventMapper;
import com.innercosmos.mapper.SlowLetterMapper;
import com.innercosmos.mapper.UserMapper;
import com.innercosmos.service.ABTestService;
import com.innercosmos.service.AdminService;
import com.innercosmos.vo.AdminOverviewVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class AdminServiceImpl implements AdminService {
    private final UserMapper userMapper;
    private final EchoCapsuleMapper capsuleMapper;
    private final ReportRecordMapper reportMapper;
    private final SafetyEventMapper safetyEventMapper;
    private final SlowLetterMapper letterMapper;
    private final AiInteractionLogMapper aiLogMapper;
    private final ModelConfigMapper modelConfigMapper;
    private final ABTestConfigMapper abTestConfigMapper;
    private final AdminActionLogMapper adminActionLogMapper;
    private final ABTestService abTestService;

    public AdminServiceImpl(UserMapper userMapper,
                            EchoCapsuleMapper capsuleMapper,
                            ReportRecordMapper reportMapper,
                            SafetyEventMapper safetyEventMapper,
                            SlowLetterMapper letterMapper,
                            AiInteractionLogMapper aiLogMapper,
                            ModelConfigMapper modelConfigMapper,
                            ABTestConfigMapper abTestConfigMapper,
                            AdminActionLogMapper adminActionLogMapper,
                            ABTestService abTestService) {
        this.userMapper = userMapper;
        this.capsuleMapper = capsuleMapper;
        this.reportMapper = reportMapper;
        this.safetyEventMapper = safetyEventMapper;
        this.letterMapper = letterMapper;
        this.aiLogMapper = aiLogMapper;
        this.modelConfigMapper = modelConfigMapper;
        this.abTestConfigMapper = abTestConfigMapper;
        this.adminActionLogMapper = adminActionLogMapper;
        this.abTestService = abTestService;
    }

    @Override
    public List<User> users() {
        return userMapper.selectList(null);
    }

    @Override
    public List<EchoCapsule> capsules() {
        return capsuleMapper.selectList(null);
    }

    @Override
    public List<EchoCapsule> capsules(String status, String keyword) {
        QueryWrapper<EchoCapsule> query = new QueryWrapper<>();
        if (status != null && !status.isBlank()) query.eq("visibility_status", status);
        if (keyword != null && !keyword.isBlank()) {
            query.and(w -> w.like("pseudonym", keyword).or().like("intro", keyword));
        }
        query.orderByDesc("id");
        return capsuleMapper.selectList(query);
    }

    @Override
    public List<ReportRecord> reports() {
        return reportMapper.selectList(null);
    }

    @Override
    public List<ReportRecord> reports(String status) {
        QueryWrapper<ReportRecord> query = new QueryWrapper<>();
        if (status != null && !status.isBlank()) query.eq("status", status);
        query.orderByDesc("id");
        return reportMapper.selectList(query);
    }

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
    @Transactional(rollbackFor = Exception.class)
    public void hideCapsule(Long adminUserId, Long id, String reason) {
        EchoCapsule capsule = capsuleMapper.selectById(id);
        if (capsule == null) throw new BusinessException(ErrorCode.NOT_FOUND, "共鸣体不存在");
        capsule.visibilityStatus = "HIDDEN";
        capsule.isPublic = false;
        capsuleMapper.updateById(capsule);
        audit(adminUserId, "HIDE_CAPSULE", "CAPSULE", id, reason);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void restoreCapsule(Long adminUserId, Long id, String reason) {
        EchoCapsule capsule = capsuleMapper.selectById(id);
        if (capsule == null) throw new BusinessException(ErrorCode.NOT_FOUND, "共鸣体不存在");
        capsule.visibilityStatus = "PUBLIC";
        capsule.isPublic = true;
        capsuleMapper.updateById(capsule);
        audit(adminUserId, "RESTORE_CAPSULE", "CAPSULE", id, reason);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resolveReport(Long adminUserId, Long id, String action, String reason) {
        ReportRecord report = reportMapper.selectById(id);
        if (report == null) throw new BusinessException(ErrorCode.NOT_FOUND, "举报记录不存在");
        String normalized = action == null || action.isBlank() ? "DISMISS" : action.trim().toUpperCase();
        report.status = "RESOLVED_" + normalized;
        reportMapper.updateById(report);
        audit(adminUserId, "RESOLVE_REPORT_" + normalized, report.targetType, report.targetId, reason);
        if (("HIDE".equals(normalized) || "BAN".equals(normalized)) && "CAPSULE".equalsIgnoreCase(report.targetType)) {
            hideCapsule(adminUserId, report.targetId, "report#" + id + ": " + safe(reason));
        }
    }

    @Override
    public List<AdminActionLog> auditLogs() {
        return adminActionLogMapper.selectList(new QueryWrapper<AdminActionLog>().orderByDesc("id").last("LIMIT 100"));
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
        if (config.id == null) modelConfigMapper.insert(config);
        else modelConfigMapper.updateById(config);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void disableUser(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        user.status = "DISABLED";
        userMapper.updateById(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void enableUser(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        user.status = "ACTIVE";
        userMapper.updateById(user);
    }

    @Override
    public List<ABTestConfig> abTestConfigs() {
        return abTestConfigMapper.selectList(new QueryWrapper<ABTestConfig>().orderByDesc("created_at"));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ABTestConfig createABTest(ABTestConfig config) {
        if (config.id == null) abTestConfigMapper.insert(config);
        else abTestConfigMapper.updateById(config);
        return config;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void toggleABTest(Long configId, boolean enabled) {
        abTestService.toggleTest(configId, enabled);
    }

    @Override
    public Map<String, ABTestService.ABTestStats> getABTestStats(String testName) {
        return abTestService.getAggregatedStats(testName);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ABTestService.ABTestReport completeABTest(Long configId) {
        return abTestService.completeTest(configId);
    }

    private void audit(Long adminUserId, String actionType, String targetType, Long targetId, String detail) {
        AdminActionLog log = new AdminActionLog();
        log.adminUserId = adminUserId;
        log.actionType = actionType;
        log.targetType = targetType;
        log.targetId = targetId;
        log.detail = safe(detail);
        adminActionLogMapper.insert(log);
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
