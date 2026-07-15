package com.innercosmos.service;

import com.innercosmos.dto.PsychologySkillRunRequest;
import com.innercosmos.skill.PsychologySkillManifest;
import com.innercosmos.vo.PsychologySkillRunVO;
import com.innercosmos.vo.PsychologySkillSuggestionVO;

import java.util.List;

public interface PsychologySkillService {
    List<PsychologySkillManifest> manifests();
    List<PsychologySkillRunVO> runs(Long userId);
    PsychologySkillRunVO run(Long userId, String skillId, PsychologySkillRunRequest request);
    PsychologySkillRunVO revoke(Long userId, Long runId);
    PsychologySkillSuggestionVO suggest(Long userId, String text, String locale);
}
