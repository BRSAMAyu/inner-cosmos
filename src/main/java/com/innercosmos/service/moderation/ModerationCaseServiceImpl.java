package com.innercosmos.service.moderation;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.common.ErrorCode;
import com.innercosmos.entity.ModerationCase;
import com.innercosmos.entity.ReportRecord;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.mapper.ModerationCaseMapper;
import com.innercosmos.mapper.ReportRecordMapper;
import com.innercosmos.service.moderation.ModerationCaseService.CaseView;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ModerationCaseServiceImpl implements ModerationCaseService {

    private static final Duration P0_SLA = Duration.ofHours(1);
    private static final Duration P1_SLA = Duration.ofHours(24);
    private static final Duration P2_SLA = Duration.ofHours(72);

    private final ModerationCaseMapper caseMapper;
    private final ReportRecordMapper reportMapper;

    public ModerationCaseServiceImpl(ModerationCaseMapper caseMapper,
                                     ReportRecordMapper reportMapper) {
        this.caseMapper = caseMapper;
        this.reportMapper = reportMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ModerationCase onReport(Long reportId, String targetType, Long targetId, String reason) {
        ModerationCase existing = caseMapper.selectOne(
                new QueryWrapper<ModerationCase>().eq("report_id", reportId));
        if (existing != null) {
            return existing; // one report -> exactly one case (idempotent hook)
        }
        String priority = classify(targetType, reason);
        ModerationCase row = new ModerationCase();
        row.reportId = reportId;
        row.targetType = targetType;
        row.targetId = targetId;
        row.priority = priority;
        row.status = "OPEN";
        row.slaDueAt = LocalDateTime.now(ZoneOffset.UTC).plus(slaFor(priority));
        caseMapper.insert(row);
        return row;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ModerationCase assign(Long caseId, Long moderatorId) {
        ModerationCase row = require(caseId);
        if (!"OPEN".equals(row.status)) {
            throw new BusinessException(ErrorCode.CONFLICT, "只能分派 OPEN 状态的案件");
        }
        row.status = "ASSIGNED";
        row.assigneeId = moderatorId;
        caseMapper.updateById(row);
        return row;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ModerationCase resolve(Long caseId, Long moderatorId, boolean dismiss, String resolution) {
        ModerationCase row = require(caseId);
        if (!"ASSIGNED".equals(row.status) && !"OPEN".equals(row.status)) {
            throw new BusinessException(ErrorCode.CONFLICT, "该案件已处理");
        }
        row.status = dismiss ? "DISMISSED" : "RESOLVED";
        if (moderatorId != null) {
            row.assigneeId = moderatorId;
        }
        row.resolution = truncate(resolution);
        caseMapper.updateById(row);
        return row;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ModerationCase appeal(Long caseId, String note) {
        ModerationCase row = require(caseId);
        if (!"RESOLVED".equals(row.status) && !"DISMISSED".equals(row.status)) {
            throw new BusinessException(ErrorCode.CONFLICT, "只有已结案件可以申诉");
        }
        row.status = "APPEALED";
        row.appealNote = truncate(note);
        caseMapper.updateById(row);
        return row;
    }

    @Override
    public List<ModerationCase> queue(String statusFilter, int limit) {
        int bounded = Math.max(1, Math.min(200, limit));
        QueryWrapper<ModerationCase> query = new QueryWrapper<ModerationCase>()
                .orderByAsc("priority").orderByAsc("sla_due_at").last("LIMIT " + bounded);
        if (statusFilter != null && !statusFilter.isBlank()) {
            query.eq("status", statusFilter);
        }
        return caseMapper.selectList(query);
    }

    @Override
    public SlaStats slaStats() {
        List<ModerationCase> resolved = caseMapper.selectList(
                new QueryWrapper<ModerationCase>().in("status", "RESOLVED", "DISMISSED"));
        long breached = resolved.stream()
                .filter(row -> row.updatedAt != null && row.updatedAt.isAfter(row.slaDueAt))
                .count();
        return new SlaStats(resolved.size(), breached);
    }

    @Override
    public List<CaseView> views(String statusFilter, int limit, boolean reporterIdentityAuthorized) {
        return queue(statusFilter, limit).stream().map(row -> {
            String reporter = null;
            if (reporterIdentityAuthorized) {
                ReportRecord report = reportMapper.selectById(row.reportId);
                reporter = report == null ? null : String.valueOf(report.reporterUserId);
            }
            return new CaseView(row.id, row.targetType, row.targetId, row.priority, row.status,
                    row.assigneeId == null ? null : String.valueOf(row.assigneeId),
                    row.slaDueAt.toString(), row.resolution, row.appealNote, reporter);
        }).toList();
    }

    /**
     * Content-class triage: crisis/child-safety/fraud-transfer signals are P0; abuse and
     * impersonation P1; everything else P2. Deterministic on report class, never on reporter.
     */
    private static String classify(String targetType, String reason) {
        String text = (reason == null ? "" : reason.toLowerCase());
        if (text.contains("诈骗") || text.contains("转账") || text.contains("fraud")
                || text.contains("未成年") || text.contains("minor") || text.contains("自杀")
                || text.contains("自残")) {
            return "P0";
        }
        if (text.contains("骚扰") || text.contains("辱骂") || text.contains("冒充")
                || text.contains("harass") || text.contains("impersonat")) {
            return "P1";
        }
        return "P2";
    }

    private static Duration slaFor(String priority) {
        return switch (priority) {
            case "P0" -> P0_SLA;
            case "P1" -> P1_SLA;
            default -> P2_SLA;
        };
    }

    private ModerationCase require(Long caseId) {
        ModerationCase row = caseMapper.selectById(caseId);
        if (row == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "案件不存在");
        }
        return row;
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        String stripped = value.strip();
        return stripped.length() <= 400 ? stripped : stripped.substring(0, 400);
    }
}
