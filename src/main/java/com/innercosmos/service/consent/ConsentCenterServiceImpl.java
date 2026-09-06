package com.innercosmos.service.consent;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.common.ErrorCode;
import com.innercosmos.entity.AnalysisConsent;
import com.innercosmos.entity.ConsentRecord;
import com.innercosmos.entity.User;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.mapper.AnalysisConsentMapper;
import com.innercosmos.mapper.ConsentRecordMapper;
import com.innercosmos.mapper.UserMapper;
import com.innercosmos.service.consent.ConsentPurpose.Decision;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConsentCenterServiceImpl implements ConsentCenterService {

    private static final Logger log = LoggerFactory.getLogger(ConsentCenterServiceImpl.class);

    private final ConsentRecordMapper consentMapper;
    private final AnalysisConsentMapper analysisConsentMapper;
    private final UserMapper userMapper;

    public ConsentCenterServiceImpl(ConsentRecordMapper consentMapper,
                                    AnalysisConsentMapper analysisConsentMapper,
                                    UserMapper userMapper) {
        this.consentMapper = consentMapper;
        this.analysisConsentMapper = analysisConsentMapper;
        this.userMapper = userMapper;
    }

    @Override
    public List<ConsentView> list(Long userId) {
        Map<ConsentPurpose, ConsentRecord> rows = rowsFor(userId);
        List<ConsentView> views = new ArrayList<>();
        for (ConsentPurpose purpose : ConsentPurpose.values()) {
            views.add(viewOf(purpose, rows.get(purpose)));
        }
        return views;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ConsentView decide(Long userId, String purposeCode, boolean grant) {
        ConsentPurpose purpose = parse(purposeCode);
        if (!purpose.userSettable) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "该用途由对应功能内的逐项授权管理：" + purpose.description);
        }
        if (purpose.group == ConsentPurpose.Group.REQUIRED && !grant) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "核心服务用途不可单独拒绝；如不需要，可以通过注销账号终止全部处理");
        }
        ConsentRecord row = consentMapper.selectOne(new QueryWrapper<ConsentRecord>()
                .eq("user_id", userId).eq("purpose_code", purpose.name()));
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (row == null) {
            row = new ConsentRecord();
            row.userId = userId;
            row.purposeCode = purpose.name();
            row.evidenceSource = "CONSENT_CENTER";
        }
        row.status = grant ? "GRANTED" : "DECLINED";
        row.version = ConsentPurpose.CURRENT_VERSION;
        if (grant) {
            row.grantedAt = now;
            row.revokedAt = null;
        } else {
            row.revokedAt = now;
        }
        if (row.id == null) {
            consentMapper.insert(row);
        } else {
            consentMapper.updateById(row);
        }
        // Dual-write so the CP-03 metric gate reads the same decision without a join.
        if (purpose == ConsentPurpose.ANALYTICS) {
            syncAnalysisConsent(userId, grant, now);
        }
        log.info("consent decision user={} purpose={} -> {}", userId, purpose.name(), row.status);
        return viewOf(purpose, row);
    }

    @Override
    public Decision effective(Long userId, ConsentPurpose purpose) {
        ConsentRecord row = consentMapper.selectOne(new QueryWrapper<ConsentRecord>()
                .eq("user_id", userId).eq("purpose_code", purpose.name()));
        if (row != null) {
            return "GRANTED".equals(row.status) ? Decision.GRANTED : Decision.DECLINED;
        }
        if (purpose == ConsentPurpose.ANALYTICS) {
            AnalysisConsent analysis = analysisConsentMapper.selectOne(
                    new QueryWrapper<AnalysisConsent>().eq("user_id", userId));
            if (analysis != null && "DECLINED".equals(analysis.status)) {
                return Decision.DECLINED;
            }
        }
        return purpose.defaultDecision;
    }

    @Override
    public void assertProviderEgress(Long userId) {
        if (userId == null) {
            return; // userless system call (no user content leaves)
        }
        User user = userMapper.selectById(userId);
        // Only real human accounts are consent-subject: internal synthetic/eval accounts
        // are not users of the service (same isolation rule as the CP-03 metric gate).
        if (user == null || !"HUMAN".equals(user.accountKind)) {
            return;
        }
        if (effective(userId, ConsentPurpose.AI_PROVIDER_EGRESS) != Decision.GRANTED) {
            throw new BusinessException(ErrorCode.CONSENT_REQUIRED, """
                    需要你的同意才能把内容发送给大模型服务。你可以在"我的—数据与同意"中逐项查看并选择。\
                    未同意前：你的对话内容不会发送到任何外部模型；本地功能（浏览记忆、数据导出与删除等）不受影响。""");
        }
    }

    private Map<ConsentPurpose, ConsentRecord> rowsFor(Long userId) {
        Map<ConsentPurpose, ConsentRecord> rows = new EnumMap<>(ConsentPurpose.class);
        for (ConsentRecord row : consentMapper.selectList(new QueryWrapper<ConsentRecord>()
                .eq("user_id", userId))) {
            try {
                rows.put(ConsentPurpose.valueOf(row.purposeCode), row);
            } catch (IllegalArgumentException retiredPurpose) {
                // Retired purposes keep their row for audit but disappear from the center.
            }
        }
        return rows;
    }

    private ConsentView viewOf(ConsentPurpose purpose, ConsentRecord row) {
        boolean granted = switch (effectiveFrom(purpose, row)) {
            case GRANTED, MANAGED -> true;
            case NOT_GRANTED, DECLINED -> false;
        };
        return new ConsentView(
                purpose.name(),
                purpose.group.name(),
                granted,
                purpose.userSettable,
                purpose.description,
                purpose.withdrawalEffect,
                row == null ? ConsentPurpose.CURRENT_VERSION : row.version,
                row == null ? "DEFAULT" : "CONSENT_CENTER");
    }

    /** Row-level effective decision without extra queries (list path). */
    private Decision effectiveFrom(ConsentPurpose purpose, ConsentRecord row) {
        if (row != null) {
            return "GRANTED".equals(row.status) ? Decision.GRANTED : Decision.DECLINED;
        }
        return purpose.defaultDecision;
    }

    private void syncAnalysisConsent(Long userId, boolean grant, LocalDateTime now) {
        AnalysisConsent existing = analysisConsentMapper.selectOne(
                new QueryWrapper<AnalysisConsent>().eq("user_id", userId));
        if (existing == null) {
            AnalysisConsent created = new AnalysisConsent();
            created.userId = userId;
            created.status = grant ? "GRANTED" : "DECLINED";
            created.consentVersion = ConsentPurpose.CURRENT_VERSION;
            analysisConsentMapper.insert(created);
        } else {
            existing.status = grant ? "GRANTED" : "DECLINED";
            existing.consentVersion = ConsentPurpose.CURRENT_VERSION;
            analysisConsentMapper.updateById(existing);
        }
    }

    private static ConsentPurpose parse(String code) {
        try {
            return ConsentPurpose.valueOf(code);
        } catch (IllegalArgumentException unknown) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "未知同意用途：" + code);
        }
    }
}
