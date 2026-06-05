package com.innercosmos.service;

import com.innercosmos.entity.ABTestConfig;
import com.innercosmos.entity.AdminActionLog;
import com.innercosmos.entity.EchoCapsule;
import com.innercosmos.entity.ModelConfig;
import com.innercosmos.entity.ReportRecord;
import com.innercosmos.entity.SafetyEvent;
import com.innercosmos.entity.User;
import com.innercosmos.vo.AdminOverviewVO;

import java.util.List;
import java.util.Map;

public interface AdminService {
    List<User> users();

    List<EchoCapsule> capsules();

    List<EchoCapsule> capsules(String status, String keyword);

    List<ReportRecord> reports();

    List<ReportRecord> reports(String status);

    AdminOverviewVO overview();

    void hideCapsule(Long adminUserId, Long id, String reason);

    void restoreCapsule(Long adminUserId, Long id, String reason);

    void resolveReport(Long adminUserId, Long id, String action, String reason);

    List<AdminActionLog> auditLogs();

    List<SafetyEvent> safetyEvents();

    List<ModelConfig> modelConfigs();

    void updateModelConfig(ModelConfig config);

    void disableUser(Long id);

    void enableUser(Long id);

    // A/B Testing Management

    List<ABTestConfig> abTestConfigs();

    ABTestConfig createABTest(ABTestConfig config);

    void toggleABTest(Long configId, boolean enabled);

    Map<String, ABTestService.ABTestStats> getABTestStats(String testName);

    ABTestService.ABTestReport completeABTest(Long configId);
}
