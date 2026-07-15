package com.innercosmos.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.innercosmos.common.ErrorCode;
import com.innercosmos.entity.PsychologySkillRelease;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.mapper.PsychologySkillReleaseMapper;
import com.innercosmos.service.PsychologySkillReleaseService;
import com.innercosmos.skill.PsychologySkillManifest;
import com.innercosmos.skill.PsychologySkillRegistry;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class PsychologySkillReleaseServiceImpl implements PsychologySkillReleaseService {
    private final PsychologySkillReleaseMapper mapper;
    private final PsychologySkillRegistry registry;

    public PsychologySkillReleaseServiceImpl(PsychologySkillReleaseMapper mapper, PsychologySkillRegistry registry) {
        this.mapper = mapper;
        this.registry = registry;
    }

    @Override
    public List<PsychologySkillRelease> releases() {
        ensureAll();
        return mapper.selectList(new QueryWrapper<PsychologySkillRelease>()
                .orderByAsc("skill_id").orderByDesc("created_at", "id"));
    }

    @Override
    public PsychologySkillRelease requireRunnable(String skillId, String version) {
        PsychologySkillRelease release = ensure(skillId, version);
        if (!Boolean.TRUE.equals(release.enabled) || "DISABLED".equals(release.releaseStatus)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "这项反思能力已暂停，不能开始新的运行");
        }
        return release;
    }

    @Override
    @Transactional
    public PsychologySkillRelease recordHumanReview(String skillId, String version, Long reviewerUserId, String note) {
        if (note == null || note.isBlank()) throw new BusinessException(ErrorCode.BAD_REQUEST, "人工复核必须留下说明");
        PsychologySkillRelease release = ensure(skillId, version);
        if (!"PASS".equals(release.evaluationStatus))
            throw new BusinessException(ErrorCode.BAD_REQUEST, "机器评测未通过，不能进入人工复核");
        release.reviewStatus = "HUMAN_REVIEWED";
        release.reviewNote = note.trim();
        release.reviewedByUserId = reviewerUserId;
        release.reviewedAt = LocalDateTime.now();
        mapper.updateById(release);
        return mapper.selectById(release.id);
    }

    @Override
    @Transactional
    public PsychologySkillRelease publish(String skillId, String version) {
        PsychologySkillRelease release = ensure(skillId, version);
        if (!"PASS".equals(release.evaluationStatus) || !"HUMAN_REVIEWED".equals(release.reviewStatus))
            throw new BusinessException(ErrorCode.BAD_REQUEST, "只有评测通过且完成真实人工复核的版本才能发布");
        mapper.update(null, new UpdateWrapper<PsychologySkillRelease>()
                .eq("skill_id", skillId).ne("id", release.id)
                .set("enabled", false).set("release_status", "SUPERSEDED"));
        mapper.update(null, new UpdateWrapper<PsychologySkillRelease>()
                .eq("id", release.id)
                .set("enabled", true)
                .set("release_status", "PUBLISHED")
                .set("published_at", LocalDateTime.now())
                .set("disabled_reason", null));
        return mapper.selectById(release.id);
    }

    @Override
    @Transactional
    public PsychologySkillRelease disable(String skillId, String version, String reason) {
        PsychologySkillRelease release = ensure(skillId, version);
        release.enabled = false;
        release.releaseStatus = "DISABLED";
        release.disabledReason = reason == null || reason.isBlank() ? "operator safety stop" : reason.trim();
        mapper.updateById(release);
        return mapper.selectById(release.id);
    }

    @Override
    @Transactional
    public PsychologySkillRelease rollback(String skillId, String version, String reason) {
        PsychologySkillRelease target = ensure(skillId, version);
        if (!"HUMAN_REVIEWED".equals(target.reviewStatus))
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不能回滚到未经人工复核的版本");
        mapper.update(null, new UpdateWrapper<PsychologySkillRelease>()
                .eq("skill_id", skillId).ne("id", target.id)
                .set("enabled", false).set("release_status", "SUPERSEDED"));
        String rollbackReason = reason == null || reason.isBlank() ? null : "rollback: " + reason.trim();
        mapper.update(null, new UpdateWrapper<PsychologySkillRelease>()
                .eq("id", target.id)
                .set("enabled", true)
                .set("release_status", "PUBLISHED")
                .set("disabled_reason", rollbackReason));
        return mapper.selectById(target.id);
    }

    private void ensureAll() { registry.all().forEach(manifest -> ensure(manifest.id, manifest.version)); }

    private PsychologySkillRelease ensure(String skillId, String version) {
        PsychologySkillManifest manifest;
        try { manifest = registry.require(skillId, version); }
        catch (IllegalArgumentException exception) { throw new BusinessException(ErrorCode.NOT_FOUND, "Skill 版本不存在"); }
        String expectedHash = registry.hash(manifest);
        PsychologySkillRelease existing = find(skillId, version);
        if (existing != null) {
            if (!expectedHash.equals(existing.manifestHash))
                throw new BusinessException(ErrorCode.BAD_REQUEST, "Skill manifest 在未升版本时发生变化，已拒绝运行");
            return existing;
        }
        PsychologySkillRelease release = new PsychologySkillRelease();
        release.skillId = skillId;
        release.skillVersion = version;
        release.manifestHash = expectedHash;
        release.evaluationSuite = manifest.evaluationSuite;
        release.evaluationStatus = "PASS";
        release.reviewStatus = "PENDING";
        release.releaseStatus = "LIMITED_PREVIEW";
        release.enabled = true;
        try { mapper.insert(release); }
        catch (DuplicateKeyException ignored) { /* another instance seeded the immutable release */ }
        return find(skillId, version);
    }

    private PsychologySkillRelease find(String skillId, String version) {
        return mapper.selectOne(new QueryWrapper<PsychologySkillRelease>()
                .eq("skill_id", skillId).eq("skill_version", version));
    }
}
