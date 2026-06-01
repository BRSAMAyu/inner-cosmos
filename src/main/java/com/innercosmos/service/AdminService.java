package com.innercosmos.service;

import com.innercosmos.entity.EchoCapsule;
import com.innercosmos.entity.ModelConfig;
import com.innercosmos.entity.ReportRecord;
import com.innercosmos.entity.SafetyEvent;
import com.innercosmos.entity.User;
import com.innercosmos.vo.AdminOverviewVO;
import java.util.List;

public interface AdminService {
    List<User> users();

    List<EchoCapsule> capsules();

    List<EchoCapsule> capsules(String status, String keyword);

    List<ReportRecord> reports();

    List<ReportRecord> reports(String status);

    AdminOverviewVO overview();

    void hideCapsule(Long id);

    void restoreCapsule(Long id);

    void resolveReport(Long id, String action);

    List<SafetyEvent> safetyEvents();

    List<ModelConfig> modelConfigs();

    void updateModelConfig(ModelConfig config);

    void disableUser(Long id);

    void enableUser(Long id);
}
