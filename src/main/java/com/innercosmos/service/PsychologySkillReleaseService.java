package com.innercosmos.service;

import com.innercosmos.entity.PsychologySkillRelease;

import java.util.List;

public interface PsychologySkillReleaseService {
    List<PsychologySkillRelease> releases();
    PsychologySkillRelease requireRunnable(String skillId, String version);
    PsychologySkillRelease recordHumanReview(String skillId, String version, Long reviewerUserId, String note);
    PsychologySkillRelease publish(String skillId, String version);
    PsychologySkillRelease disable(String skillId, String version, String reason);
    PsychologySkillRelease rollback(String skillId, String version, String reason);
}
