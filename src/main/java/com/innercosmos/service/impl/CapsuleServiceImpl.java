package com.innercosmos.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.innercosmos.ai.agent.CapsuleAgent;
import com.innercosmos.dto.CapsuleCreateRequest;
import com.innercosmos.entity.CapsuleBoundary;
import com.innercosmos.entity.EchoCapsule;
import com.innercosmos.entity.BlockRelation;
import com.innercosmos.mapper.BlockRelationMapper;
import com.innercosmos.mapper.CapsuleBoundaryMapper;
import com.innercosmos.mapper.EchoCapsuleMapper;
import com.innercosmos.mapper.MemoryCardMapper;
import com.innercosmos.mapper.UserPortraitMapper;
import com.innercosmos.mapper.AuthorizedMemoryRefMapper;
import com.innercosmos.entity.AuthorizedMemoryRef;
import com.innercosmos.entity.MemoryCard;
import com.innercosmos.entity.UserPortrait;
import com.innercosmos.ai.semantic.PseudoSemanticAnalyzer;
import com.innercosmos.service.CapsuleService;
import com.innercosmos.service.ResonanceMatchStrategy;
import com.innercosmos.service.CapsuleGenomeService;
import com.innercosmos.service.CapsuleEmbeddingIndexService;
import com.innercosmos.service.DataMaskingService;
import com.innercosmos.service.DataRetractionReceiptService;
import com.innercosmos.service.DataUseGrantService;
import com.innercosmos.util.CapsulePublicTextUtils;
import com.innercosmos.util.DataMaskingUtils;
import com.innercosmos.vo.CapsulePreviewVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Arrays;

@Service
public class CapsuleServiceImpl implements CapsuleService {
    private final EchoCapsuleMapper capsuleMapper;
    private final CapsuleBoundaryMapper boundaryMapper;
    private final CapsuleAgent capsuleAgent;
    private final MemoryCardMapper memoryCardMapper;
    private final UserPortraitMapper userPortraitMapper;
    private final AuthorizedMemoryRefMapper authorizedMemoryRefMapper;
    private final CapsuleGenomeService genomeService;
    private final DataUseGrantService dataUseGrantService;
    private final BlockRelationMapper blockRelationMapper;
    private final ObjectMapper objectMapper;
    private final CapsuleEmbeddingIndexService capsuleEmbeddingIndexService;
    private final DataRetractionReceiptService retractionReceiptService;
    // Gemini audit 3.1 (CONFIRMED/P0): the P1->P2 scrubbing chokepoint. Previously this stronger
    // masker existed only behind the disconnected POST /api/capsule/preview-from-memory endpoint
    // (DataMaskingServiceImpl.previewFromMemory) while the REAL compile path below sent raw
    // memory title/summary straight to the persona-synthesis LLM provider and into persisted
    // genome artifacts, with at most DataMaskingUtils.maskContact's digit/email-only masking.
    private final DataMaskingService dataMaskingService;

    public CapsuleServiceImpl(EchoCapsuleMapper capsuleMapper,
                              CapsuleBoundaryMapper boundaryMapper,
                              CapsuleAgent capsuleAgent,
                              MemoryCardMapper memoryCardMapper,
                              UserPortraitMapper userPortraitMapper,
                              AuthorizedMemoryRefMapper authorizedMemoryRefMapper,
                              CapsuleGenomeService genomeService,
                              DataUseGrantService dataUseGrantService,
                              BlockRelationMapper blockRelationMapper,
                              ObjectMapper objectMapper,
                              CapsuleEmbeddingIndexService capsuleEmbeddingIndexService,
                              DataRetractionReceiptService retractionReceiptService,
                              DataMaskingService dataMaskingService) {
        this.capsuleMapper = capsuleMapper;
        this.boundaryMapper = boundaryMapper;
        this.capsuleAgent = capsuleAgent;
        this.memoryCardMapper = memoryCardMapper;
        this.userPortraitMapper = userPortraitMapper;
        this.authorizedMemoryRefMapper = authorizedMemoryRefMapper;
        this.genomeService = genomeService;
        this.dataUseGrantService = dataUseGrantService;
        this.blockRelationMapper = blockRelationMapper;
        this.objectMapper = objectMapper;
        this.capsuleEmbeddingIndexService = capsuleEmbeddingIndexService;
        this.retractionReceiptService = retractionReceiptService;
        this.dataMaskingService = dataMaskingService;
    }

    /**
     * Gemini audit 3.1: the single point every memory line passes through before it can reach
     * either the persona-synthesis provider call or a persisted/servable genome artifact
     * (personaPrompt, contextPreviewJson/genomeIr). Minimization is a property of the compiler,
     * not of whichever call site remembered to apply it.
     */
    private String scrubbedMemoryLine(MemoryCard card, String privacyLevel) {
        String title = card.title == null ? "" : card.title;
        String summary = card.summary == null ? "" : card.summary;
        return dataMaskingService.maskText(title + ": " + summary, privacyLevel);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EchoCapsule createFromMemory(Long userId, CapsuleCreateRequest request) {
        EchoCapsule capsule = new EchoCapsule();
        capsule.ownerUserId = userId;
        capsule.capsuleType = "USER_CAPSULE";
        capsule.pseudonym = request.pseudonym == null || request.pseudonym.isBlank() ? "未命名回声" : request.pseudonym;
        capsule.intro = request.intro == null ? "一枚从脱敏记忆中编织出的数字回声." : request.intro;

        // Gemini audit 3.1: the privacy tier is resolved up front (mirrors the same request field
        // the boundary insert below uses) so the SAME tier scrubs every artifact derived from
        // these memories -- the provider-bound persona summary and the persisted context preview
        // must never diverge in how hard they scrub.
        String privacyLevel = safePrivacy(request.privacyLevel);

        // Fetch selected memory cards to synthesize user persona
        List<String> memorySummaries = new java.util.ArrayList<>();
        List<MemoryCard> authorizedCards = new java.util.ArrayList<>();
        if (request.memoryIds != null && !request.memoryIds.isEmpty()) {
            for (Long mid : request.memoryIds) {
                MemoryCard card = memoryCardMapper.selectById(mid);
                // SIMULATOR_AUTHORIZED is a distinct, purpose-scoped consent grant — it must never
                // be reachable through the normal (potentially-public) capsule path, only through
                // createSimulatorCapsule. Without this exclusion a memory the owner explicitly
                // marked "simulator testing only" could end up in a capsule real visitors can chat with.
                if (card != null && userId.equals(card.userId) && "ACTIVE".equalsIgnoreCase(card.status)
                        && !"LOCAL_ONLY".equalsIgnoreCase(card.consentScope)
                        && !"NO_EXTERNAL_PROCESSING".equalsIgnoreCase(card.consentScope)
                        && !SIMULATOR_CONSENT_SCOPE.equalsIgnoreCase(card.consentScope)) {
                    memorySummaries.add(scrubbedMemoryLine(card, privacyLevel));
                    authorizedCards.add(card);
                }
            }
        }
        capsule.publicTags = toJsonArray(request.publicTags, "self-resonance");
        capsule.authorizedMemoryIds = toJsonArray(authorizedCards.stream().map(card -> String.valueOf(card.id)).toList());
        capsule.ownerContextNote = request.ownerContextNote;
        capsule.styleProfileJson = request.styleProfileJson == null ? inferStyleProfile(authorizedCards) : request.styleProfileJson;
        capsule.contextPreviewJson = request.contextPreviewJson == null
                ? buildContextPreview(authorizedCards, capsule.publicTags, capsule.ownerContextNote, privacyLevel)
                : request.contextPreviewJson;
        capsule.standInEnabled = request.standInEnabled == null ? false : request.standInEnabled;
        capsule.realContactPolicy = request.realContactPolicy == null ? "LETTER_ONLY" : request.realContactPolicy;
        capsule.echoEnergy = 0.72;
        capsule.freshnessScore = 0.86;
        capsule.conversationLimitPerDay = safeTurns(request.maxConversationTurns, false);
        capsule.visibilityStatus = safeVisibility(request.visibilityStatus);
        capsule.isPublic = request.isPublic == null ? !"PRIVATE".equals(capsule.visibilityStatus) : request.isPublic;
        capsule.lastMemoryUpdateAt = LocalDateTime.now();
        capsuleMapper.insert(capsule);

        CapsuleBoundary boundary = new CapsuleBoundary();
        boundary.capsuleId = capsule.id;
        boundary.allowTopics = toJsonArray(request.allowTopics, "自我观察", "温柔建议", "日常支持");
        boundary.blockedTopics = toJsonArray(request.blockedTopics, "隐私身份", "诊断承诺", "强迫即时回应");
        boundary.maxConversationTurns = safeTurns(request.maxConversationTurns, false);
        boundary.allowLetterRequest = request.allowLetterRequest == null ? true : request.allowLetterRequest;
        boundary.privacyLevel = safePrivacy(request.privacyLevel);
        boundaryMapper.insert(boundary);
        for (MemoryCard card : authorizedCards) authorize(capsule, card);
        // Persist purpose/version grants before any provider can receive derived memory text.
        // Empty selection still means a generic capsule, never implicit access to top memories.
        capsule.personaPrompt = capsuleAgent.generateUserPersona(userId, memorySummaries, capsule.pseudonym, capsule.intro);
        capsuleMapper.updateById(capsule);
        genomeService.compile(capsule, authorizedCards, "initial explicit capsule compilation");
        return capsule;
    }

    private static final String SIMULATOR_CONSENT_SCOPE = "SIMULATOR_AUTHORIZED";

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EchoCapsule createSimulatorCapsule(Long userId, CapsuleCreateRequest request) {
        List<Long> requested = request.memoryIds == null ? List.of() : request.memoryIds;
        if (requested.isEmpty()) {
            throw new com.innercosmos.exception.BusinessException(
                    com.innercosmos.common.ErrorCode.BAD_REQUEST, "模拟器共鸣体需要至少一条明确授权的记忆");
        }
        List<MemoryCard> authorizedCards = new ArrayList<>();
        for (Long mid : requested) {
            MemoryCard card = memoryCardMapper.selectById(mid);
            if (card == null || !userId.equals(card.userId) || !"ACTIVE".equalsIgnoreCase(card.status)
                    || !SIMULATOR_CONSENT_SCOPE.equalsIgnoreCase(card.consentScope)) {
                throw new com.innercosmos.exception.BusinessException(
                        com.innercosmos.common.ErrorCode.BAD_REQUEST,
                        "记忆 " + mid + " 未被明确标记为仅供模拟器使用，不能进入模拟器共鸣体");
            }
            authorizedCards.add(card);
        }
        // Gemini audit 3.1: same scrubbing chokepoint as createFromMemory -- a simulator capsule
        // is owner-only/isolated, but its persona is still synthesized by the same external
        // provider call, so the same minimization applies before that call.
        String privacyLevel = safePrivacy(request.privacyLevel);
        List<String> memorySummaries = authorizedCards.stream()
                .map(card -> scrubbedMemoryLine(card, privacyLevel)).toList();

        EchoCapsule capsule = new EchoCapsule();
        capsule.ownerUserId = userId;
        capsule.capsuleType = "USER_CAPSULE";
        capsule.pseudonym = request.pseudonym == null || request.pseudonym.isBlank() ? "隔离的模拟器侧面" : request.pseudonym;
        capsule.intro = request.intro == null ? "仅供测试与研究使用的隔离侧面，不会被真实访客发现或对话。" : request.intro;
        capsule.publicTags = toJsonArray(request.publicTags, "simulator-only");
        capsule.authorizedMemoryIds = toJsonArray(authorizedCards.stream().map(card -> String.valueOf(card.id)).toList());
        capsule.ownerContextNote = request.ownerContextNote;
        capsule.styleProfileJson = request.styleProfileJson == null ? inferStyleProfile(authorizedCards) : request.styleProfileJson;
        capsule.contextPreviewJson = request.contextPreviewJson == null
                ? buildContextPreview(authorizedCards, capsule.publicTags, capsule.ownerContextNote, privacyLevel)
                : request.contextPreviewJson;
        capsule.standInEnabled = false;
        capsule.realContactPolicy = "LETTER_ONLY";
        capsule.echoEnergy = 0.0;
        capsule.freshnessScore = 0.0;
        capsule.conversationLimitPerDay = safeTurns(request.maxConversationTurns, false);
        // Permanently isolated regardless of what the request asked for: never public, never matched,
        // never reachable by real visitor persona chat.
        capsule.visibilityStatus = "PRIVATE";
        capsule.isPublic = false;
        capsule.simulatorOnly = true;
        capsule.lastMemoryUpdateAt = LocalDateTime.now();
        capsuleMapper.insert(capsule);

        CapsuleBoundary boundary = new CapsuleBoundary();
        boundary.capsuleId = capsule.id;
        boundary.allowTopics = toJsonArray(request.allowTopics, "自我观察", "温柔建议", "日常支持");
        boundary.blockedTopics = toJsonArray(request.blockedTopics, "隐私身份", "诊断承诺", "强迫即时回应");
        boundary.maxConversationTurns = safeTurns(request.maxConversationTurns, false);
        boundary.allowLetterRequest = false;
        boundary.privacyLevel = safePrivacy(request.privacyLevel);
        boundaryMapper.insert(boundary);
        for (MemoryCard card : authorizedCards) authorize(capsule, card);
        capsule.personaPrompt = capsuleAgent.generateUserPersona(userId, memorySummaries, capsule.pseudonym, capsule.intro);
        capsuleMapper.updateById(capsule);
        genomeService.compile(capsule, authorizedCards, "simulator-only compilation for isolated testing/research");
        return capsule;
    }

    @Override
    public EchoCapsule updateContext(Long userId, Long capsuleId, Map<String, Object> body) {
        EchoCapsule capsule = getOwnedCapsule(userId, capsuleId);
        if (capsule == null) {
            throw new com.innercosmos.exception.BusinessException(
                    com.innercosmos.common.ErrorCode.UNAUTHORIZED, "无权操作此共鸣体");
        }
        if (body.containsKey("ownerContextNote")) capsule.ownerContextNote = stringValue(body.get("ownerContextNote"));
        if (body.containsKey("styleProfileJson")) capsule.styleProfileJson = stringValue(body.get("styleProfileJson"));
        if (body.containsKey("contextPreviewJson")) capsule.contextPreviewJson = stringValue(body.get("contextPreviewJson"));
        if (body.containsKey("standInEnabled")) capsule.standInEnabled = Boolean.TRUE.equals(body.get("standInEnabled"));
        if (body.containsKey("realContactPolicy")) capsule.realContactPolicy = safeContactPolicy(stringValue(body.get("realContactPolicy")));
        if (body.containsKey("publicTags")) capsule.publicTags = toJsonArray(castStringList(body.get("publicTags")), "self-resonance");
        if (body.containsKey("authorizedMemoryIds")) {
            List<Long> requested = parseLongIds(toJsonArray(castStringList(body.get("authorizedMemoryIds"))));
            replaceAuthorizations(userId, capsule, requested);
            capsule.visibilityStatus = "NEEDS_REVIEW";
            capsule.isPublic = false;
            genomeService.markNeedsReview(capsule.id, "owner changed authorized memories");
        }
        capsule.lastMemoryUpdateAt = LocalDateTime.now();
        if (capsule.contextPreviewJson == null || capsule.contextPreviewJson.isBlank()) {
            capsule.contextPreviewJson = buildFallbackContextPreview(capsule.intro, capsule.publicTags, capsule.ownerContextNote);
        }
        capsuleMapper.updateById(capsule);
        return capsule;
    }

    @Override
    public Map<String, Object> contextPreview(Long userId, Long capsuleId) {
        EchoCapsule capsule = getOwnedCapsule(userId, capsuleId);
        if (capsule == null) {
            throw new com.innercosmos.exception.BusinessException(
                    com.innercosmos.common.ErrorCode.UNAUTHORIZED, "无权操作此共鸣体");
        }
        List<Long> ids = parseLongIds(capsule.authorizedMemoryIds);
        List<Map<String, Object>> memories = new ArrayList<>();
        for (Long id : ids) {
            MemoryCard card = memoryCardMapper.selectById(id);
            if (card != null && userId.equals(card.userId)) {
                memories.add(Map.of(
                        "id", card.id,
                        "title", card.title == null ? "" : card.title,
                        "summary", card.summary == null ? "" : card.summary,
                        "tags", parseTags(card.keywordTags)
                ));
            }
        }
        return Map.of(
                "capsuleId", capsule.id,
                "pseudonym", capsule.pseudonym == null ? "" : capsule.pseudonym,
                "contextPreview", capsule.contextPreviewJson == null ? "" : capsule.contextPreviewJson,
                "styleProfile", capsule.styleProfileJson == null ? "" : capsule.styleProfileJson,
                "ownerContextNote", capsule.ownerContextNote == null ? "" : capsule.ownerContextNote,
                "authorizedMemories", memories,
                "publicTags", parseTags(capsule.publicTags),
                "standInEnabled", Boolean.TRUE.equals(capsule.standInEnabled),
                "realContactPolicy", capsule.realContactPolicy == null ? "LETTER_ONLY" : capsule.realContactPolicy
        );
    }

    @Override
    public EchoCapsule getOwnedCapsule(Long userId, Long capsuleId) {
        QueryWrapper<EchoCapsule> query = new QueryWrapper<>();
        query.eq("id", capsuleId).eq("owner_user_id", userId).last("LIMIT 1");
        return capsuleMapper.selectOne(query);
    }

    @Override
    public EchoCapsule updateVisibility(Long userId, Long capsuleId, String visibilityStatus, Boolean isPublic) {
        EchoCapsule capsule = getOwnedCapsule(userId, capsuleId);
        if (capsule == null) {
            return null;
        }
        if ("PUBLIC".equals(visibilityStatus) && (!currentAuthorizationsValid(capsule)
                || (capsule.activeGenomeVersionId != null && genomeService.current(capsuleId) == null))) {
            throw new com.innercosmos.exception.BusinessException(
                    com.innercosmos.common.ErrorCode.BAD_REQUEST, "授权记忆需要复核后才能重新公开");
        }
        boolean requestsPublic = "PUBLIC".equals(visibilityStatus) || Boolean.TRUE.equals(isPublic);
        if (Boolean.TRUE.equals(capsule.simulatorOnly) && requestsPublic) {
            throw new com.innercosmos.exception.BusinessException(
                    com.innercosmos.common.ErrorCode.BAD_REQUEST, "模拟器共鸣体仅供隔离测试，永远不能公开或被真实访客发现");
        }
        capsule.visibilityStatus = safeVisibility(visibilityStatus);
        capsule.isPublic = isPublic == null ? !"PRIVATE".equals(capsule.visibilityStatus) : isPublic;
        capsuleMapper.updateById(capsule);
        return capsule;
    }

    @Override
    public List<EchoCapsule> myCapsules(Long userId) {
        QueryWrapper<EchoCapsule> query = new QueryWrapper<>();
        query.eq("owner_user_id", userId).orderByDesc("id");
        return capsuleMapper.selectList(query);
    }

    @Override
    public List<EchoCapsule> plazaCapsules() {
        QueryWrapper<EchoCapsule> query = new QueryWrapper<>();
        query.eq("is_public", true).eq("visibility_status", "PUBLIC").orderByDesc("echo_energy");
        return capsuleMapper.selectList(query);
    }

    // IC-CAP-003 smart-matching constants. Deterministic, no LLM.
    // The 6 real theme families produced by PseudoSemanticAnalyzer; "日常分享" (default) is ignored.
    private static final Set<String> THEME_FAMILIES = Set.of(
            "任务压力", "关系牵动", "情绪承压", "认知探索", "自我评价", "希望期待");
    // Stable tie-break order for matchReasons when min-frequencies are equal.
    private static final List<String> FAMILY_ORDER = List.of(
            "任务压力", "关系牵动", "情绪承压", "认知探索", "自我评价", "希望期待");
    // themeOverlap = min(0.55, rawOverlap * 0.18), rawOverlap = sum over shared families of min(userFreq, capFreq).
    private static final double THEME_OVERLAP_UNIT = 0.18;
    private static final double THEME_OVERLAP_CAP = 0.55;
    // portraitSignal: each portrait-dim family also present in the capsule profile adds a fixed increment, capped.
    private static final double PORTRAIT_INCREMENT = 0.07;
    private static final double PORTRAIT_CAP = 0.20;
    private static final double ENERGY_WEIGHT = 0.18;
    private static final double SEED_BOOST = 0.12;
    private static final double USER_BOOST = 0.06;
    // A3-capsule-matching: real embedding/vector-similarity signal, ensembled with the lexical
    // theme-overlap signal above rather than replacing it (multi-strategy: lexical always
    // available offline; semantic widens recall on paraphrase/no-shared-keyword cases whenever an
    // embedding provider is configured — see CapsuleEmbeddingIndexService). Contributes to MIRROR
    // relevance only; capped well below THEME_OVERLAP_CAP so a strong lexical overlap can never be
    // out-voted by embedding noise, and a zero-lexical-overlap capsule needs a genuinely high
    // cosine similarity to become resonant at all.
    private static final double SEMANTIC_SIMILARITY_WEIGHT = 0.5;
    private static final double SEMANTIC_SIMILARITY_CAP = 0.40;
    private static final double SEMANTIC_REASON_THRESHOLD = 0.05;

    // G6.MATCH-MULTI (INNO-CAP-013, this dispatch): a local, deterministic "Mock" semantic-
    // similarity stand-in for a real embedding provider. No real embedding/LLM provider key is
    // available in this environment (see evidence/innovation/INNO-CAP-013), so this is NOT the
    // CapsuleEmbeddingIndexService real-provider path above -- it is a small, hand-curated,
    // per-family "paraphrase cue" lexicon, deliberately kept separate from and additive to the
    // strict PseudoSemanticAnalyzer.THEME_KEYWORDS lexicon `themesOf()` uses everywhere else in
    // this class and in Aurora mode-suggestion/sentiment/intent detection, so this fix has zero
    // blast radius outside capsule matching. A family only counts via this path when the STRICT
    // theme match already missed it (see paraphraseFamiliesOf below), and the contribution is
    // capped low so a paraphrase can never out-rank or match the strength of a genuine exact
    // keyword overlap.
    private static final Map<String, Set<String>> PARAPHRASE_CUES = Map.of(
            "任务压力", Set.of("熬夜", "赶稿", "加班", "连轴转", "焦头烂额", "扛不住", "身心俱疲"),
            "关系牵动", Set.of("疏远", "隔阂", "不理我", "渐行渐远", "话不投机"),
            "情绪承压", Set.of("喘不过气", "情绪崩溃", "心力交瘁", "无力感", "快撑不下去"),
            "认知探索", Set.of("拿不定主意", "理不出头绪", "举棋不定", "不知道该信谁"),
            "自我评价", Set.of("配不上", "抬不起头", "一无是处", "自我怀疑"),
            "希望期待", Set.of("满怀期待", "值得期待", "心生向往", "盼望已久"));
    private static final double PARAPHRASE_SIGNAL_UNIT = 0.10;
    private static final double PARAPHRASE_SIGNAL_CAP = 0.10;

    @Override
    public List<Map<String, Object>> matchedCapsules(Long userId) {
        return matchedCapsules(userId, ResonanceMatchStrategy.MIRROR);
    }

    @Override
    public List<Map<String, Object>> matchedCapsules(Long userId, ResonanceMatchStrategy strategy) {
        List<MemoryCard> memories = memoryCardMapper.selectList(new QueryWrapper<MemoryCard>()
                .eq("user_id", userId)
                .eq("status", "ACTIVE")
                .orderByDesc("emotional_gravity")
                .last("LIMIT 24"));

        // userThemeProfile: family -> frequency aggregated over up to 24 memories.
        Map<String, Integer> userThemeProfile = new HashMap<>();
        for (MemoryCard memory : memories) {
            String text = joinText(memory.title, memory.summary,
                    String.join(" ", parseTags(memory.keywordTags)),
                    String.join(" ", parseTags(memory.emotionTags)));
            mergeThemes(userThemeProfile, themesOf(text));
        }

        // portrait families (CURRENT_STATE / EMOTION_PATTERN / INNER_DRIVE) for this user.
        Set<String> portraitFamilies = fetchPortraitFamilies(userId);

        // SAFETY-FILTER STAGE: a capsule owned by someone in a block relationship with the viewer
        // (either direction) must never surface as a resonance match. The plaza query only gates
        // public/visible; block state is a viewer-specific safety signal that belongs in matching.
        Set<Long> blockedUserIds = blockedCounterparties(userId);

        List<EchoCapsule> all = plazaCapsules();
        // Pre-filter to the exact candidate set the scoring loop below will consider, BEFORE
        // asking the embedding index for similarities — a blocked or self-owned capsule must never
        // even be sent to the embedding path, let alone surface via a semantic score.
        List<EchoCapsule> eligible = new ArrayList<>();
        for (EchoCapsule capsule : all) {
            if (userId.equals(capsule.ownerUserId)) continue;
            if (capsule.ownerUserId != null && blockedUserIds.contains(capsule.ownerUserId)) continue;
            eligible.add(capsule);
        }
        // Consent-scoped query text: the same memories driving userThemeProfile, minus any memory
        // whose consentScope forbids external processing — mirrors the exact filter
        // MemoryEmbeddingIndexServiceImpl already applies before sending memory text to a provider.
        String semanticQueryText = consentScopedSemanticQueryText(memories);
        Map<Long, Double> semanticScores = capsuleEmbeddingIndexService.similarities(semanticQueryText, eligible);

        List<Map<String, Object>> scored = new ArrayList<>();
        for (EchoCapsule capsule : eligible) {
            String capText = CapsulePublicTextUtils.publicSafeText(capsule);
            Map<String, Integer> capsuleThemeProfile = new HashMap<>();
            mergeThemes(capsuleThemeProfile, themesOf(capText));

            // themeOverlap: weighted intersection. More shared families + higher min-freq => higher score.
            int rawOverlap = 0;
            // matchReasons sorted by descending min-frequency, then stable family order.
            List<Map.Entry<String, Integer>> shared = new ArrayList<>();
            for (Map.Entry<String, Integer> e : userThemeProfile.entrySet()) {
                Integer capFreq = capsuleThemeProfile.get(e.getKey());
                if (capFreq != null) {
                    int minFreq = Math.min(e.getValue(), capFreq);
                    rawOverlap += minFreq;
                    shared.add(Map.entry(e.getKey(), minFreq));
                }
            }
            double themeOverlap = Math.min(THEME_OVERLAP_CAP, rawOverlap * THEME_OVERLAP_UNIT);

            // portraitSignal: portrait families that also appear in the capsule profile.
            double portraitSignal = 0.0;
            for (String fam : portraitFamilies) {
                if (capsuleThemeProfile.containsKey(fam)) {
                    portraitSignal += PORTRAIT_INCREMENT;
                }
            }
            portraitSignal = Math.min(PORTRAIT_CAP, portraitSignal);

            // semanticSignal: real embedding cosine similarity between the viewer's consent-scoped
            // profile text and this capsule's public-safe text, widened into a bounded relevance
            // contribution. 0.0 whenever no embedding provider is configured (graceful degrade to
            // pure lexical matching, matching MemoryRetrievalService's established pattern).
            double semanticSimilarity = semanticScores.getOrDefault(capsule.id, 0.0);
            double semanticSignal = Math.min(SEMANTIC_SIMILARITY_CAP,
                    Math.max(0.0, semanticSimilarity) * SEMANTIC_SIMILARITY_WEIGHT);

            // paraphraseSignal: G6.MATCH-MULTI local Mock semantic-cue signal (see PARAPHRASE_CUES
            // Javadoc above). Only counts a family the viewer already actively cares about
            // (userThemeProfile) that the STRICT theme match on this capsule missed -- so a family
            // already driving themeOverlap is never double-counted here.
            Set<String> paraphraseFamilies = new LinkedHashSet<>();
            for (String fam : weakParaphraseFamiliesOf(capText)) {
                if (userThemeProfile.containsKey(fam) && !capsuleThemeProfile.containsKey(fam)) {
                    paraphraseFamilies.add(fam);
                }
            }
            double paraphraseSignal = Math.min(PARAPHRASE_SIGNAL_CAP,
                    paraphraseFamilies.size() * PARAPHRASE_SIGNAL_UNIT);

            double energyScore = (capsule.echoEnergy == null ? 0.5 : capsule.echoEnergy) * ENERGY_WEIGHT;
            double seedBoost = "SEED_CAPSULE".equals(capsule.capsuleType) ? SEED_BOOST : USER_BOOST;
            // FIX-A: relevance is ONLY the genuinely user-specific signal (themeOverlap +
            // portraitSignal + semanticSignal). seedBoost/energyScore are NOT relevance — they
            // break ties and shape ordering, but must never make a zero-overlap capsule count as a
            // match on their own.
            shared.sort(Comparator
                    .comparingInt((Map.Entry<String, Integer> en) -> -en.getValue())
                    .thenComparingInt(en -> FAMILY_ORDER.indexOf(en.getKey())));
            List<String> mirrorReasons = new ArrayList<>(shared.stream().limit(5).map(Map.Entry::getKey).toList());
            // Interpretable reason: only surfaced when the semantic signal meaningfully
            // contributes, so a viewer can tell a match apart from pure lexical overlap (spec:
            // "expose understandable reasons and controls").
            if (semanticSignal >= SEMANTIC_REASON_THRESHOLD || paraphraseSignal > 0.0) {
                mirrorReasons.add("语义相近");
            }
            StrategySignal signal = strategySignal(strategy, userThemeProfile, portraitFamilies,
                    capsuleThemeProfile.keySet(), capsule.id,
                    themeOverlap + portraitSignal + semanticSignal + paraphraseSignal,
                    mirrorReasons);
            boolean resonant = signal.relevance() > 0.0;
            double score = Math.min(0.99, signal.relevance() + energyScore + seedBoost);

            // matchTier: G6.MATCH-MULTI graded neutral/partial-overlap bucket. FULL when every one
            // of the viewer's currently active theme families (userThemeProfile) is represented by
            // this capsule (strict OR paraphrase match); PARTIAL when only some are; NONE when none
            // are (mirrors the existing resonant=false backfill case). Purely additive/explanatory
            // metadata -- does not change resonant, matchScore or ranking/backfill behavior.
            Set<String> coveredFamilies = new LinkedHashSet<>(capsuleThemeProfile.keySet());
            coveredFamilies.addAll(paraphraseFamilies);
            String matchTier;
            if (userThemeProfile.isEmpty()) {
                matchTier = "NONE";
            } else {
                long covered = userThemeProfile.keySet().stream().filter(coveredFamilies::contains).count();
                matchTier = covered == 0 ? "NONE" : covered == userThemeProfile.size() ? "FULL" : "PARTIAL";
            }

            Map<String, Object> item = new HashMap<>();
            item.put("capsule", capsule);
            item.put("matchScore", Math.round(score * 100.0) / 100.0);
            item.put("matchReasons", signal.reasons());
            item.put("matchSummary", buildMatchSummary(capsule, strategy, signal.reasons()));
            item.put("matchTier", matchTier);
            item.put("strategy", strategy.name());
            item.put("strategyLabel", strategy.label);
            item.put("strategyDescription", strategy.description);
            // FIX-A: frontend MAY use this to distinguish a genuine resonance match from a
            // cold-start backfill; existing keys (matchScore/matchSummary/matchReasons/capsule)
            // are unchanged so no frontend change is required.
            item.put("resonant", resonant);
            // Transient feature set for the diversity stage; stripped before returning.
            item.put("__themeKeys", new HashSet<>(capsuleThemeProfile.keySet()));
            scored.add(item);
        }
        // FIX-A relevance gate: relevant capsules (resonant=true) ALWAYS sort before
        // non-relevant ones, so a zero-overlap high-energy/seed capsule can never outrank or
        // crowd out a genuinely relevant one. Within each group, the prior deterministic order
        // applies: matchScore desc, then echoEnergy desc, then capsule.id asc. After limit(12),
        // non-relevant capsules only occupy slots left over once every relevant capsule is in —
        // i.e. they merely backfill, guaranteeing the plaza is never empty for a sparse user.
        Comparator<Map<String, Object>> withinGroup = Comparator
                .comparingDouble((Map<String, Object> v) -> -((Number) v.get("matchScore")).doubleValue())
                .thenComparingDouble(v -> -energyOf(v))
                .thenComparingLong(CapsuleServiceImpl::idOf);
        // DIVERSITY STAGE: within each group (resonant first, then backfill), re-rank with
        // maximal-marginal-relevance so the top slots are not crowded by near-identical theme
        // profiles. The resonant-before-non-resonant invariant (FIX-A) is preserved by diversifying
        // each group independently, and relevance still dominates (lambda 0.72).
        List<Map<String, Object>> resonantGroup = new ArrayList<>();
        List<Map<String, Object>> backfillGroup = new ArrayList<>();
        for (Map<String, Object> v : scored) {
            (Boolean.TRUE.equals(v.get("resonant")) ? resonantGroup : backfillGroup).add(v);
        }
        resonantGroup.sort(withinGroup);
        backfillGroup.sort(withinGroup);
        List<Map<String, Object>> ordered = new ArrayList<>();
        ordered.addAll(diversify(resonantGroup, withinGroup));
        ordered.addAll(diversify(backfillGroup, withinGroup));
        return ordered.stream().limit(12)
                .peek(item -> item.remove("__themeKeys"))
                .toList();
    }

    private static final double DIVERSITY_LAMBDA = 0.72;

    /**
     * Maximal-marginal-relevance re-rank: greedily pick the candidate that best balances relevance
     * against dissimilarity to what is already selected, so the result set spreads across theme
     * families instead of returning many near-duplicate capsules. Deterministic — ties fall back to
     * the caller's stable comparator.
     */
    private List<Map<String, Object>> diversify(List<Map<String, Object>> group,
                                                Comparator<Map<String, Object>> tieBreak) {
        List<Map<String, Object>> pool = new ArrayList<>(group);
        List<Map<String, Object>> selected = new ArrayList<>(pool.size());
        while (!pool.isEmpty()) {
            Map<String, Object> best = null;
            double bestScore = Double.NEGATIVE_INFINITY;
            for (Map<String, Object> candidate : pool) {
                double relevance = ((Number) candidate.get("matchScore")).doubleValue();
                double maxSim = 0.0;
                for (Map<String, Object> chosen : selected) {
                    maxSim = Math.max(maxSim, jaccard(themeKeysOf(candidate), themeKeysOf(chosen)));
                }
                double mmr = DIVERSITY_LAMBDA * relevance - (1.0 - DIVERSITY_LAMBDA) * maxSim;
                if (best == null || mmr > bestScore + 1e-9
                        || (Math.abs(mmr - bestScore) <= 1e-9 && tieBreak.compare(candidate, best) < 0)) {
                    best = candidate;
                    bestScore = mmr;
                }
            }
            selected.add(best);
            pool.remove(best);
        }
        return selected;
    }

    @SuppressWarnings("unchecked")
    private static Set<String> themeKeysOf(Map<String, Object> item) {
        Object keys = item.get("__themeKeys");
        return keys instanceof Set ? (Set<String>) keys : Set.of();
    }

    private static double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() && b.isEmpty()) return 0.0;
        long intersection = a.stream().filter(b::contains).count();
        long union = a.size() + b.size() - intersection;
        return union == 0 ? 0.0 : (double) intersection / union;
    }

    /** Blocked counterparties (either direction) for the viewer — a viewer-specific safety filter. */
    private Set<Long> blockedCounterparties(Long userId) {
        Set<Long> blocked = new HashSet<>();
        blockRelationMapper.selectList(new QueryWrapper<BlockRelation>().eq("blocker_user_id", userId))
                .forEach(relation -> { if (relation.blockedUserId != null) blocked.add(relation.blockedUserId); });
        blockRelationMapper.selectList(new QueryWrapper<BlockRelation>().eq("blocked_user_id", userId))
                .forEach(relation -> { if (relation.blockerUserId != null) blocked.add(relation.blockerUserId); });
        return blocked;
    }

    private StrategySignal strategySignal(ResonanceMatchStrategy strategy,
                                          Map<String, Integer> userThemes,
                                          Set<String> portraitThemes,
                                          Set<String> capsuleThemes,
                                          Long capsuleId,
                                          double mirrorRelevance,
                                          List<String> mirrorReasons) {
        if (strategy == ResonanceMatchStrategy.MIRROR) {
            return new StrategySignal(mirrorRelevance, mirrorReasons);
        }
        if (strategy == ResonanceMatchStrategy.COMPLEMENT) {
            List<String> missing = FAMILY_ORDER.stream()
                    .filter(capsuleThemes::contains).filter(theme -> !userThemes.containsKey(theme)).limit(5)
                    .map(theme -> "带来·" + theme).toList();
            return new StrategySignal(Math.min(0.55, missing.size() * 0.18), missing);
        }
        if (strategy == ResonanceMatchStrategy.GROWTH_EDGE) {
            List<String> bridges = new ArrayList<>();
            addBridge(bridges, userThemes, capsuleThemes, "任务压力", "希望期待");
            addBridge(bridges, userThemes, capsuleThemes, "情绪承压", "认知探索");
            addBridge(bridges, userThemes, capsuleThemes, "自我评价", "关系牵动");
            return new StrategySignal(Math.min(0.60, bridges.size() * 0.28), bridges);
        }
        if (strategy == ResonanceMatchStrategy.SERENDIPITY) {
            List<String> reasons = new ArrayList<>();
            reasons.add("为熟悉轨迹留出意外");
            capsuleThemes.stream().limit(2).map(theme -> "可能遇见·" + theme).forEach(reasons::add);
            double stableVariation = Math.floorMod(capsuleId == null ? 0L : capsuleId, 7L) * 0.02;
            return new StrategySignal(0.22 + stableVariation, reasons);
        }
        Set<String> context = portraitThemes.isEmpty()
                ? userThemes.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .limit(2).map(Map.Entry::getKey).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new))
                : portraitThemes;
        List<String> reasons = FAMILY_ORDER.stream().filter(context::contains).filter(capsuleThemes::contains)
                .limit(5).map(theme -> "此刻·" + theme).toList();
        return new StrategySignal(Math.min(0.60, reasons.size() * 0.24), reasons);
    }

    private static void addBridge(List<String> bridges, Map<String, Integer> userThemes,
                                  Set<String> capsuleThemes, String from, String to) {
        if (userThemes.containsKey(from) && capsuleThemes.contains(to)) {
            bridges.add(from + " → " + to);
        }
    }

    private record StrategySignal(double relevance, List<String> reasons) {}

    private static double energyOf(Map<String, Object> item) {
        EchoCapsule c = (EchoCapsule) item.get("capsule");
        return c.echoEnergy == null ? 0.5 : c.echoEnergy;
    }

    private static long idOf(Map<String, Object> item) {
        EchoCapsule c = (EchoCapsule) item.get("capsule");
        return c.id == null ? Long.MAX_VALUE : c.id;
    }

    private String joinText(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p != null && !p.isBlank()) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(p);
            }
        }
        return sb.toString();
    }

    /**
     * A3-capsule-matching: the viewer-side text handed to the embedding provider. Built from the
     * SAME up-to-24 recent memories driving userThemeProfile, minus any memory whose consentScope
     * forbids external processing — the identical LOCAL_ONLY/NO_EXTERNAL_PROCESSING exclusion
     * MemoryEmbeddingIndexServiceImpl already applies before it ever calls a provider. A memory the
     * owner marked local-only must never leave the process boundary just because it also happens to
     * be one of their most emotionally weighted memories.
     */
    private String consentScopedSemanticQueryText(List<MemoryCard> memories) {
        List<String> parts = new ArrayList<>();
        for (MemoryCard memory : memories) {
            String scope = memory.consentScope == null ? "" : memory.consentScope.toUpperCase(Locale.ROOT);
            if ("LOCAL_ONLY".equals(scope) || "NO_EXTERNAL_PROCESSING".equals(scope)
                    || SIMULATOR_CONSENT_SCOPE.equalsIgnoreCase(memory.consentScope)) {
                continue;
            }
            parts.add(joinText(memory.title, memory.summary,
                    String.join(" ", parseTags(memory.keywordTags)),
                    String.join(" ", parseTags(memory.emotionTags))));
        }
        return String.join(" ", parts).trim();
    }

    /** Run the pseudo-semantic analyzer and keep only the 6 real theme families (drop "日常分享"). */
    private Set<String> themesOf(String text) {
        Set<String> families = new LinkedHashSet<>();
        if (text == null || text.isBlank()) {
            return families;
        }
        for (String theme : PseudoSemanticAnalyzer.analyze(text).detectedThemes) {
            if (THEME_FAMILIES.contains(theme)) {
                families.add(theme);
            }
        }
        return families;
    }

    /**
     * G6.MATCH-MULTI (INNO-CAP-013): families detected only via the broader {@link
     * #PARAPHRASE_CUES} lexicon -- see that field's Javadoc for why this is a local Mock stand-in,
     * not a real embedding call. Independent of {@link #themesOf}; the caller is responsible for
     * only counting a family here when the strict lexicon already missed it, so this never
     * double-counts a family the exact-keyword path already found.
     */
    private Set<String> weakParaphraseFamiliesOf(String text) {
        Set<String> families = new LinkedHashSet<>();
        if (text == null || text.isBlank()) {
            return families;
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, Set<String>> entry : PARAPHRASE_CUES.entrySet()) {
            for (String cue : entry.getValue()) {
                if (normalized.contains(cue.toLowerCase(Locale.ROOT))) {
                    families.add(entry.getKey());
                    break;
                }
            }
        }
        return families;
    }

    private void mergeThemes(Map<String, Integer> profile, Set<String> families) {
        for (String fam : families) {
            profile.merge(fam, 1, Integer::sum);
        }
    }

    /** Collect 6-family themes from the user's CURRENT_STATE / EMOTION_PATTERN / INNER_DRIVE portrait rows. */
    private Set<String> fetchPortraitFamilies(Long userId) {
        Set<String> families = new LinkedHashSet<>();
        List<UserPortrait> rows = userPortraitMapper.selectList(new QueryWrapper<UserPortrait>()
                .eq("user_id", userId)
                .in("dim", "CURRENT_STATE", "EMOTION_PATTERN", "INNER_DRIVE"));
        if (rows == null) {
            return families;
        }
        for (UserPortrait p : rows) {
            families.addAll(themesOf(p.valueJson));
        }
        return families;
    }

    @Override
    public CapsulePreviewVO previewUserMirror(Long userId) {
        List<MemoryCard> cards = memoryCardMapper.selectList(new QueryWrapper<MemoryCard>()
                .eq("user_id", userId)
                .eq("status", "ACTIVE")
                .orderByDesc("emotional_gravity")
                .last("LIMIT 8"));
        CapsulePreviewVO vo = new CapsulePreviewVO();
        vo.removedSensitiveItems = new ArrayList<>();
        vo.riskWarnings = new ArrayList<>();
        vo.publicTags = new ArrayList<>();
        if (cards.isEmpty()) {
            vo.abstractSummary = "还没有足够的长期记忆来生成用户共鸣体。建议先完成一次 Aurora 对话或心声日记。";
            vo.suggestedPseudonym = "新的回声分身";
            vo.personaPromptDraft = capsuleAgent.buildPersonaPrompt(vo.suggestedPseudonym, vo.abstractSummary);
            return vo;
        }
        // Gemini audit 3.1: this preview calls the SAME generateUserPersona provider RPC as real
        // capsule creation (it is not a purely local summary), and it must show the owner what
        // will actually be sent/persisted -- not a rosier, unscrubbed stand-in for it. No
        // capsule/boundary exists yet at preview time, so this uses safePrivacy's own default
        // (BALANCED) rather than a stricter tier that would make the preview look safer than the
        // real capsule createFromMemory would produce.
        String privacyLevel = safePrivacy(null);
        StringBuilder summary = new StringBuilder();
        List<String> memorySummaries = new ArrayList<>();
        for (MemoryCard card : cards) {
            String scrubbedTitle = dataMaskingService.maskText(card.title == null ? "" : card.title, privacyLevel);
            String scrubbedSummary = dataMaskingService.maskText(card.summary == null ? "" : card.summary, privacyLevel);
            summary.append("「").append(scrubbedTitle).append("」").append(scrubbedSummary).append("\n");
            memorySummaries.add(scrubbedTitle + ": " + scrubbedSummary);
            vo.publicTags.addAll(parseTags(card.keywordTags).stream().limit(3).toList());
            if (card.intensityScore != null && card.intensityScore >= 7.5) {
                vo.riskWarnings.add("包含高情绪重力记忆，建议公开前再次确认边界。");
            }
        }
        vo.publicTags = vo.publicTags.stream().distinct().limit(8).toList();
        vo.abstractSummary = summary.toString().trim();
        vo.suggestedPseudonym = "我的回声分身";
        vo.personaPromptDraft = capsuleAgent.generateUserPersona(userId, memorySummaries, vo.suggestedPseudonym,
                "一个由授权长期记忆生成的用户共鸣体，用于慢社交中的低压力共鸣对话。");
        vo.personaPromptDraft += "\n\n透明提示：这个共鸣体会保留真实困惑、表达习惯和价值偏好，不会把用户美化成完美人设。访问者看到的是授权后的脱敏回声。";
        vo.removedSensitiveItems.add("原始对话全文");
        vo.removedSensitiveItems.add("真实身份与联系方式");
        return vo;
    }

    @Override
    public CapsuleBoundary getBoundary(Long userId, Long capsuleId) {
        // M-023: boundary (allowTopics/blockedTopics/visibility) is owner-private config —
        // verify ownership before returning it.
        if (getOwnedCapsule(userId, capsuleId) == null) {
            throw new com.innercosmos.exception.BusinessException(
                    com.innercosmos.common.ErrorCode.NOT_FOUND, "共鸣体不存在或无权访问");
        }
        QueryWrapper<CapsuleBoundary> query = new QueryWrapper<>();
        query.eq("capsule_id", capsuleId).last("LIMIT 1");
        return boundaryMapper.selectOne(query);
    }

    @Override
    public CapsuleBoundary updateBoundary(Long userId, Long capsuleId, CapsuleBoundary boundary, Integer expectedVersion) {
        EchoCapsule capsule = getOwnedCapsule(userId, capsuleId);
        if (capsule == null) {
            throw new com.innercosmos.exception.BusinessException(
                    com.innercosmos.common.ErrorCode.UNAUTHORIZED, "无权操作此共鸣体");
        }
        CapsuleBoundary existing = getBoundary(userId, capsuleId);
        if (existing == null) {
            throw new com.innercosmos.exception.BusinessException(
                    com.innercosmos.common.ErrorCode.NOT_FOUND, "边界配置不存在");
        }
        int currentVersion = existing.version == null ? 1 : existing.version;
        int expected = expectedVersion == null ? currentVersion : expectedVersion;
        if (expected != currentVersion) {
            throw new com.innercosmos.exception.BusinessException(
                    com.innercosmos.common.ErrorCode.CONFLICT, "capsule boundary version is stale");
        }
        if (boundary.allowTopics != null) existing.allowTopics = boundary.allowTopics;
        if (boundary.blockedTopics != null) existing.blockedTopics = boundary.blockedTopics;
        if (boundary.maxConversationTurns != null) existing.maxConversationTurns = boundary.maxConversationTurns;
        if (boundary.allowLetterRequest != null) existing.allowLetterRequest = boundary.allowLetterRequest;
        if (boundary.privacyLevel != null) existing.privacyLevel = boundary.privacyLevel;
        int updated = boundaryMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<CapsuleBoundary>()
                .eq("id", existing.id).eq("version", currentVersion)
                .set("allow_topics", existing.allowTopics)
                .set("blocked_topics", existing.blockedTopics)
                .set("max_conversation_turns", existing.maxConversationTurns)
                .set("allow_letter_request", existing.allowLetterRequest)
                .set("privacy_level", existing.privacyLevel)
                .set("version", currentVersion + 1)
                .set("updated_at", java.time.LocalDateTime.now()));
        if (updated != 1) {
            throw new com.innercosmos.exception.BusinessException(
                    com.innercosmos.common.ErrorCode.CONFLICT, "capsule boundary changed concurrently");
        }
        return getBoundary(userId, capsuleId);
    }

    @Override
    public void archiveCapsule(Long userId, Long capsuleId) {
        EchoCapsule capsule = getOwnedCapsule(userId, capsuleId);
        if (capsule == null) {
            throw new com.innercosmos.exception.BusinessException(
                    com.innercosmos.common.ErrorCode.UNAUTHORIZED, "无权操作此共鸣体");
        }
        capsule.visibilityStatus = "ARCHIVED";
        capsule.isPublic = false;
        capsule.authorizedMemoryIds = "[]";
        capsuleMapper.updateById(capsule);
        genomeService.withdraw(capsuleId, "owner archived capsule");
        dataUseGrantService.revokeForCapsule(capsuleId, "owner archived capsule");
        for (AuthorizedMemoryRef ref : authorizedMemoryRefMapper.selectList(
                new QueryWrapper<AuthorizedMemoryRef>().eq("capsule_id", capsuleId))) {
            ref.authorizationStatus = "WITHDRAWN";
            authorizedMemoryRefMapper.updateById(ref);
        }
        // Archiving delists the capsule; erase its compiled matching vector now so it can no longer
        // surface as a discovery candidate, and leave an auditable receipt of the erasure.
        int erased = capsuleEmbeddingIndexService.retireForCapsule(capsuleId);
        retractionReceiptService.record(userId, DataRetractionReceiptService.SUBJECT_CAPSULE,
                capsuleId, DataRetractionReceiptService.DERIVATIVE_CAPSULE_MATCH_INDEX,
                DataRetractionReceiptService.ACTION_ERASED, erased, "owner archived capsule");
    }

    private List<MemoryCard> replaceAuthorizations(Long userId, EchoCapsule capsule, List<Long> requestedIds) {
        dataUseGrantService.revokeForCapsule(capsule.id, "owner replaced capsule authorization set");
        for (AuthorizedMemoryRef ref : authorizedMemoryRefMapper.selectList(
                new QueryWrapper<AuthorizedMemoryRef>().eq("capsule_id", capsule.id))) {
            ref.authorizationStatus = "WITHDRAWN";
            authorizedMemoryRefMapper.updateById(ref);
        }
        // The SIMULATOR_AUTHORIZED purpose grant must hold for the capsule's entire lifetime, not
        // just at creation: updateContext/recompileGenome both funnel through this method, so
        // without this check a Simulator capsule could later be recompiled with ordinary
        // AURORA_PRIVATE memories (defeating the distinct-consent contract), or — the more
        // serious direction — a normal capsule could accept a memory the owner marked simulator-
        // testing-only, reaching real visitors through plaza/matching/persona chat.
        boolean simulatorOnly = Boolean.TRUE.equals(capsule.simulatorOnly);
        List<MemoryCard> accepted = new ArrayList<>();
        for (Long id : requestedIds) {
            MemoryCard card = memoryCardMapper.selectById(id);
            if (card == null || !userId.equals(card.userId) || !"ACTIVE".equalsIgnoreCase(card.status)) continue;
            boolean isSimulatorScope = SIMULATOR_CONSENT_SCOPE.equalsIgnoreCase(card.consentScope);
            if (simulatorOnly ? !isSimulatorScope
                    : (isSimulatorScope || "LOCAL_ONLY".equalsIgnoreCase(card.consentScope)
                            || "NO_EXTERNAL_PROCESSING".equalsIgnoreCase(card.consentScope))) {
                continue;
            }
            accepted.add(card);
            authorize(capsule, card);
        }
        capsule.authorizedMemoryIds = toJsonArray(accepted.stream().map(card -> String.valueOf(card.id)).toList());
        return accepted;
    }

    private boolean currentAuthorizationsValid(EchoCapsule capsule) {
        Set<Long> ids = new LinkedHashSet<>(parseLongIds(capsule.authorizedMemoryIds));
        if (ids.isEmpty()) return true;
        long authorized = authorizedMemoryRefMapper.selectCount(new QueryWrapper<AuthorizedMemoryRef>()
                .eq("capsule_id", capsule.id).in("memory_card_id", ids)
                .eq("authorization_status", "AUTHORIZED"));
        return authorized >= ids.size() && dataUseGrantService.authorizationsValid(capsule, ids);
    }

    private void authorize(EchoCapsule capsule, MemoryCard card) {
        List<com.innercosmos.entity.DataUseGrant> grants = dataUseGrantService.authorize(capsule, card);
        AuthorizedMemoryRef ref = new AuthorizedMemoryRef();
        ref.capsuleId = capsule.id;
        ref.memoryCardId = card.id;
        ref.dataUseGrantId = grants.getFirst().id;
        String summary = card.summary == null ? "" : card.summary;
        ref.abstractExcerpt = summary.substring(0, Math.min(180, summary.length()));
        ref.authorizationStatus = "AUTHORIZED";
        authorizedMemoryRefMapper.insert(ref);
    }

    @Override
    public Double markLanded(Long userId, Long capsuleId) {
        // M-067: "this landed for me" — a closed resonance signal. Verify the capsule is public,
        // then atomically bump echoEnergy by 0.02 (capped at 1.0) in a single SQL statement.
        EchoCapsule capsule = capsuleMapper.selectById(capsuleId);
        if (capsule == null || !Boolean.TRUE.equals(capsule.isPublic)) {
            throw new com.innercosmos.exception.BusinessException(
                    com.innercosmos.common.ErrorCode.NOT_FOUND, "共鸣体不存在或不可见");
        }
        capsuleMapper.update(null, new UpdateWrapper<EchoCapsule>()
                .eq("id", capsuleId)
                .setSql("echo_energy = LEAST(1.0, COALESCE(echo_energy, 0) + 0.02)"));
        Double updated = capsuleMapper.selectById(capsuleId).echoEnergy;
        return updated == null ? 1.0 : updated;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public com.innercosmos.entity.CapsuleGenomeVersion recompileGenome(Long userId, Long capsuleId,
                                                                       List<Long> memoryIds) {
        EchoCapsule capsule = getOwnedCapsule(userId, capsuleId);
        if (capsule == null) throw new com.innercosmos.exception.BusinessException(
                com.innercosmos.common.ErrorCode.UNAUTHORIZED, "无权重新编译此共鸣体");
        Set<Long> requested = new LinkedHashSet<>(memoryIds == null ? List.of() : memoryIds);
        requested.remove(null);
        List<MemoryCard> cards = replaceAuthorizations(userId, capsule,
                new ArrayList<>(requested));
        if (cards.size() != requested.size()) {
            throw new com.innercosmos.exception.BusinessException(
                    com.innercosmos.common.ErrorCode.BAD_REQUEST,
                    "所选记忆包含已撤回、非本人或禁止用于共鸣体的内容");
        }
        // Gemini audit 3.1: recompile must scrub with the capsule's OWN configured privacy tier,
        // not a hardcoded default -- an owner who set STRICT at creation must not silently get a
        // weaker scrub just because they later re-authorized their memory selection.
        CapsuleBoundary existingBoundary = boundaryMapper.selectOne(
                new QueryWrapper<CapsuleBoundary>().eq("capsule_id", capsuleId).last("LIMIT 1"));
        String privacyLevel = safePrivacy(existingBoundary == null ? null : existingBoundary.privacyLevel);
        List<String> summaries = cards.stream().map(card -> scrubbedMemoryLine(card, privacyLevel)).toList();
        capsule.personaPrompt = capsuleAgent.generateUserPersona(
                userId, summaries, capsule.pseudonym, capsule.intro);
        capsule.styleProfileJson = inferStyleProfile(cards);
        capsule.contextPreviewJson = buildContextPreview(
                cards, capsule.publicTags, capsule.ownerContextNote, privacyLevel);
        capsule.visibilityStatus = "PRIVATE";
        capsule.isPublic = false;
        capsule.lastMemoryUpdateAt = LocalDateTime.now();
        capsuleMapper.updateById(capsule);
        return genomeService.compile(capsule, cards, "owner reviewed and recompiled authorization");
    }

    private Integer safeTurns(Integer turns, boolean seedCapsule) {
        if (seedCapsule) return 0;
        if (turns == null) return 30;
        return Math.max(2, Math.min(50, turns));
    }

    private String safeVisibility(String value) {
        if ("PRIVATE".equals(value) || "HIDDEN".equals(value) || "ARCHIVED".equals(value)) {
            return value;
        }
        return "PUBLIC";
    }

    private String safePrivacy(String value) {
        if ("STRICT".equals(value) || "OPEN".equals(value)) {
            return value;
        }
        return "BALANCED";
    }

    private String toJsonArray(List<String> values, String... defaults) {
        List<String> source = values == null || values.isEmpty() ? List.of(defaults) : values;
        return source.stream()
                .filter(v -> v != null && !v.isBlank())
                .map(v -> "\"" + v.replace("\\", "\\\\").replace("\"", "\\\"") + "\"")
                .reduce("[", (a, b) -> "[".equals(a) ? a + b : a + "," + b) + "]";
    }

    private List<String> parseTags(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        String cleaned = raw.trim();
        if (cleaned.startsWith("[") && cleaned.endsWith("]")) {
            cleaned = cleaned.substring(1, cleaned.length() - 1);
        }
        List<String> tags = new ArrayList<>();
        for (String item : cleaned.split(",")) {
            String tag = item.trim().replace("\"", "");
            if (!tag.isBlank()) {
                tags.add(tag);
            }
        }
        return tags;
    }

    private String buildMatchSummary(EchoCapsule capsule, ResonanceMatchStrategy strategy,
                                     List<String> themeFamilies) {
        if (themeFamilies == null || themeFamilies.isEmpty()) {
            return strategy.label + "暂未找到强信号；这是安全回填，不代表高度契合。";
        }
        String type = "SEED_CAPSULE".equals(capsule.capsuleType) ? "官方种子" : "用户回声";
        return type + " · " + strategy.label + "：" + String.join("、", themeFamilies.stream().limit(3).toList()) + "。";
    }

    // Feature-extraction voice descriptors keyed by the 6 real PseudoSemanticAnalyzer theme
    // families (see THEME_FAMILIES/FAMILY_ORDER above) — generalizes the old 3 hardcoded
    // substring checks into a signal derived from every authorized memory, not just wording.
    private static final Map<String, String> VOICE_BY_THEME = Map.of(
            "任务压力", "重视可验证的小行动，不喜欢空泛的鼓励",
            "关系牵动", "在关系中重视被认真回应，不喜欢被敷衍",
            "情绪承压", "对被评判或被催促\"赶紧好起来\"敏感",
            "认知探索", "喜欢把话说透彻，不喜欢被简化总结",
            "自我评价", "对自我否定的措辞敏感，需要被谨慎回应",
            "希望期待", "愿意谈期待和向往，但不喜欢空泛的正能量"
    );

    // Substring-based sentiment classification, deliberately independent of
    // PseudoSemanticAnalyzer.calculateSentiment: that method tokenizes Chinese text into single
    // characters but scores via exact lookup against a mostly multi-character lexicon, so it
    // silently returns NEUTRAL for nearly all real sentences (verified: 4/4 clearly emotional
    // probe sentences scored 0.0). Theme detection in the same analyzer is unaffected — it
    // already matches via substring containment — which is exactly the pattern mirrored here.
    private static final Set<String> POSITIVE_WORDS = Set.of(
            "开心", "高兴", "快乐", "幸福", "满足", "欣慰", "喜悦", "愉快", "轻松", "放松",
            "顺利", "成功", "希望", "期待", "憧憬", "释然", "解脱", "温暖", "感激", "安心");
    private static final Set<String> NEGATIVE_WORDS = Set.of(
            "难过", "伤心", "委屈", "失望", "沮丧", "痛苦", "煎熬", "折磨", "焦虑", "崩溃",
            "绝望", "撑不住", "孤独", "孤单", "没人懂", "害怕", "恐惧", "吵架", "冲突",
            "分手", "冷战", "闹翻", "生气", "愤怒", "委靡", "累垮");
    private static final List<String> VALUE_CUES = List.of(
            "重视", "看重", "在意", "原则", "价值", "坚持", "不愿");
    private static final List<String> HABIT_CUES = List.of(
            "习惯", "通常", "一般会", "总是", "常常", "往往", "会先", "倾向");
    private static final List<String> TEMPORAL_CUES = List.of(
            "最近", "现在", "目前", "近期", "这段时间", "今天", "本周", "正在", "刚刚");

    private String sceneSentiment(String text) {
        if (text == null || text.isBlank()) return "NEUTRAL";
        boolean positive = POSITIVE_WORDS.stream().anyMatch(text::contains);
        boolean negative = NEGATIVE_WORDS.stream().anyMatch(text::contains);
        if (positive && negative) return "MIXED";
        if (positive) return "POSITIVE";
        if (negative) return "NEGATIVE";
        return "NEUTRAL";
    }

    /**
     * Real feature extraction over the authorized memory set: aggregates PseudoSemanticAnalyzer
     * theme families (weighted by frequency) into voice descriptors, and tracks the dominant
     * sentiment plus a sample-size-derived confidence so downstream consumers can see how much
     * signal actually backs the profile, rather than a single keyword-substring guess.
     *
     * Genome IR slice 1 (provenance): each voice descriptor now cites the exact memoryIds that
     * produced it via "voiceEvidence" — a reviewer or the owner can trace "在关系中重视被认真回应"
     * back to specific authorized memories, rather than trusting an unexplained personality
     * adjective. This is a first, bounded step toward a fully provenance-carrying Genome IR
     * (claims/values/habits/temporal state/unknowns), not that whole structure yet.
     */
    private String inferStyleProfile(List<MemoryCard> cards) {
        Map<String, Integer> themeFreq = new LinkedHashMap<>();
        Map<String, List<Long>> themeEvidence = new LinkedHashMap<>();
        Map<String, Double> sentimentWeight = new LinkedHashMap<>();
        int unreadableCount = 0;
        for (MemoryCard card : cards) {
            String text = joinText(card.title, card.summary,
                    String.join(" ", parseTags(card.keywordTags)),
                    String.join(" ", parseTags(card.emotionTags)));
            if (text.isBlank()) { unreadableCount++; continue; }
            Set<String> families = themesOf(text);
            mergeThemes(themeFreq, families);
            for (String family : families) {
                themeEvidence.computeIfAbsent(family, k -> new ArrayList<>()).add(card.id);
            }
            double gravity = card.emotionalGravity == null ? 0.5 : Math.max(0, Math.min(1, card.emotionalGravity));
            sentimentWeight.merge(sceneSentiment(text), 0.5 + gravity * 0.5, Double::sum);
        }
        List<String> dominantThemes = themeFreq.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                        .thenComparingInt(e -> FAMILY_ORDER.indexOf(e.getKey())))
                .limit(2)
                .map(Map.Entry::getKey)
                .toList();
        List<String> style = dominantThemes.stream()
                .map(VOICE_BY_THEME::get)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        // Genome IR "unknowns": a fallback voice is a real absence of signal, not a low-confidence
        // match on some signal — say so explicitly instead of presenting a generic phrase with the
        // same authority as a genuinely theme-derived one.
        List<String> unknowns = new ArrayList<>();
        if (style.isEmpty()) {
            style.add("温和、诚实、慢热");
            unknowns.add("没有任何授权记忆产生足够强的主题信号，voice 使用了通用默认值，不代表已识别出这个人具体的表达模式");
        }
        if (unreadableCount > 0) {
            unknowns.add(unreadableCount + " 条已授权记忆标题和摘要均为空，未参与特征提取");
        }
        String dominantSentiment = sentimentWeight.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey).orElse("NEUTRAL");

        Map<String, Object> voiceEvidence = new LinkedHashMap<>();
        for (String theme : dominantThemes) {
            voiceEvidence.put(theme, themeEvidence.getOrDefault(theme, List.of()));
        }

        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("voice", String.join("，", style));
        profile.put("voiceEvidence", voiceEvidence);
        profile.put("dominantSentiment", dominantSentiment);
        profile.put("themeSignals", themeFreq);
        profile.put("unknowns", unknowns);
        profile.put("sampleSize", cards.size());
        profile.put("confidence", Math.round(Math.min(1.0, cards.size() / 5.0) * 100.0) / 100.0);
        profile.put("notBeautified", true);
        profile.put("boundary", "只呈现授权后的真实片段，不替本人承诺");
        return write(profile);
    }

    /**
     * Real scene indexing + tension surfacing: groups authorized memories by dominant theme
     * family into scenes (instead of a single 420-char truncation of everything concatenated),
     * picks the highest-gravity memory per scene as its representative excerpt, and flags a
     * scene as holding tension when it contains both a POSITIVE and a NEGATIVE memory. This is
     * deliberately NOT called a "contradiction": one happy and one difficult memory about the
     * same relationship is normal emotional complexity, not a logical inconsistency the compiler
     * has detected — it's a signal to represent nuance rather than blend it into one flat voice.
     */
    private String buildContextPreview(List<MemoryCard> cards, String publicTags, String note, String privacyLevel) {
        Map<String, List<MemoryCard>> byScene = new LinkedHashMap<>();
        for (MemoryCard card : cards) {
            String text = joinText(card.title, card.summary, String.join(" ", parseTags(card.keywordTags)));
            Set<String> families = themesOf(text);
            // A card can match >1 family (substring-based theme detection over single Chinese
            // characters can cross-match, e.g. "日期" collides with the "期待" keyword) — break
            // ties deterministically via the same FAMILY_ORDER convention used elsewhere in this
            // class, instead of an arbitrary Set iteration order.
            String scene = families.isEmpty() ? "日常片段"
                    : FAMILY_ORDER.stream().filter(families::contains).findFirst()
                            .orElseGet(() -> families.iterator().next());
            byScene.computeIfAbsent(scene, k -> new ArrayList<>()).add(card);
        }

        List<Map<String, Object>> scenes = new ArrayList<>();
        List<Map<String, Object>> tensions = new ArrayList<>();
        for (Map.Entry<String, List<MemoryCard>> entry : byScene.entrySet()) {
            List<MemoryCard> members = entry.getValue();
            MemoryCard representative = members.stream()
                    .max(Comparator.comparingDouble(c -> c.emotionalGravity == null ? 0.0 : c.emotionalGravity))
                    .orElse(members.get(0));
            String excerpt = ((representative.title == null ? "" : representative.title) + ": "
                    + (representative.summary == null ? "" : representative.summary)).replaceAll("\\s+", " ").trim();
            excerpt = excerpt.substring(0, Math.min(160, excerpt.length()));
            // Gemini audit 3.1: this excerpt is not just an owner-facing preview string -- it is
            // read back at persona-chat runtime (CapsuleRuntimeContextComposer selects "scenes"
            // straight out of this JSON into the live provider prompt), so it needs the same
            // entity/PII scrubbing as personaPrompt, not only the narrower digit/email masking
            // maskContact alone provides.
            excerpt = dataMaskingService.maskText(excerpt, privacyLevel);
            excerpt = DataMaskingUtils.maskContact(excerpt);

            Set<String> sentimentLabels = new LinkedHashSet<>();
            for (MemoryCard member : members) {
                String memberText = joinText(member.title, member.summary,
                        String.join(" ", parseTags(member.emotionTags)));
                sentimentLabels.add(sceneSentiment(memberText));
            }
            boolean hasTension = sentimentLabels.contains("MIXED")
                    || (sentimentLabels.contains("POSITIVE") && sentimentLabels.contains("NEGATIVE"));

            // Genome IR slice 1 (provenance): every scene cites the exact memoryId/sourceVersion/
            // confidence backing it, instead of just an aggregate excerpt — the owner or a
            // reviewer can trace a scene back to the specific authorized memories it came from.
            List<Map<String, Object>> memberProvenance = members.stream()
                    .map(member -> {
                        Map<String, Object> ref = new LinkedHashMap<>();
                        ref.put("memoryId", member.id);
                        ref.put("sourceVersion", member.versionNo == null ? 1 : member.versionNo);
                        ref.put("confidence", member.confidence == null ? 1.0 : member.confidence);
                        return ref;
                    }).toList();

            Map<String, Object> scene = new LinkedHashMap<>();
            scene.put("theme", entry.getKey());
            scene.put("memoryCount", members.size());
            scene.put("excerpt", excerpt);
            scene.put("memories", memberProvenance);
            scene.put("sentimentLabels", sentimentLabels);
            scene.put("hasTension", hasTension);
            scenes.add(scene);
            if (hasTension) {
                tensions.add(Map.of("theme", entry.getKey(),
                        "note", "同一主题下同时存在轻松与低落的记忆，这是正常的情绪复杂性，不代表矛盾，回应时不做单一定论"));
            }
        }
        scenes.sort(Comparator.comparingInt((Map<String, Object> s) -> (Integer) s.get("memoryCount")).reversed());
        List<Map<String, Object>> topScenes = scenes.stream().limit(5).toList();

        Map<String, Object> preview = new LinkedHashMap<>();
        preview.put("schemaVersion", "capsule-context-preview.v3");
        preview.put("genomeIr", buildGenomeIr(cards, privacyLevel));
        preview.put("scenes", topScenes);
        preview.put("tensions", tensions);
        preview.put("retrievalPolicy", retrievalPolicy());
        preview.put("publicTags", parseTags(publicTags));
        preview.put("ownerNote", note == null ? "" : note);
        preview.put("privacy", "不包含原始对话全文、联系方式、真实身份和未授权记忆");
        return write(preview);
    }

    /** No authorized memories exist yet (e.g. a freshly-created capsule) — a single-scene stub. */
    private String buildFallbackContextPreview(String intro, String publicTags, String note) {
        Map<String, Object> preview = new LinkedHashMap<>();
        preview.put("schemaVersion", "capsule-context-preview.v3");
        preview.put("genomeIr", emptyGenomeIr("没有授权记忆，无法提取 claims、values、habits 或 temporal state"));
        preview.put("scenes", List.of(Map.of(
                "theme", "日常片段", "memoryCount", 0,
                "excerpt", DataMaskingUtils.maskContact(intro), "memories", List.of(),
                "sentimentLabels", List.of(), "hasTension", false)));
        preview.put("tensions", List.of());
        preview.put("retrievalPolicy", retrievalPolicy());
        preview.put("publicTags", parseTags(publicTags));
        preview.put("ownerNote", note == null ? "" : note);
        preview.put("privacy", "不包含原始对话全文、联系方式、真实身份和未授权记忆");
        return write(preview);
    }

    /**
     * Deterministic, evidence-bounded Genome IR. Claims remain episode-scoped excerpts; values,
     * habits and temporal state are emitted only when their explicit cue appears in the same
     * authorized memory. This compiler intentionally records absence as unknown instead of
     * generalising one event into a stable personality trait.
     */
    private Map<String, Object> buildGenomeIr(List<MemoryCard> cards, String privacyLevel) {
        List<Map<String, Object>> claims = new ArrayList<>();
        List<Map<String, Object>> values = new ArrayList<>();
        List<Map<String, Object>> habits = new ArrayList<>();
        List<Map<String, Object>> temporalState = new ArrayList<>();
        for (MemoryCard card : cards) {
            // Gemini audit 3.1: claims/values/habits/temporalState statements are the exact text
            // CapsuleRuntimeContextComposer selects into the live persona-chat provider prompt on
            // every visitor turn -- the single highest-traffic egress point in the whole compiler,
            // so it gets the same scrubbing as personaPrompt, not only contact masking.
            String statement = DataMaskingUtils.maskContact(
                            dataMaskingService.maskText(joinText(card.title, card.summary), privacyLevel))
                    .replaceAll("\\s+", " ").trim();
            if (statement.isBlank()) continue;
            statement = statement.substring(0, Math.min(220, statement.length()));
            claims.add(irFeature("claim", card, statement, "EPISODIC_ONLY"));
            if (containsCue(statement, VALUE_CUES)) {
                values.add(irFeature("value", card, statement, "EXPLICIT_CUE_NOT_STABLE_TRAIT"));
            }
            if (containsCue(statement, HABIT_CUES)) {
                habits.add(irFeature("habit", card, statement, "EXPLICIT_CUE_NOT_UNIVERSAL_BEHAVIOR"));
            }
            if (containsCue(statement, TEMPORAL_CUES)) {
                temporalState.add(irFeature("temporal", card, statement, "TIME_BOUND_CLAIM"));
            }
        }
        List<Map<String, Object>> unknowns = new ArrayList<>();
        if (values.isEmpty()) unknowns.add(unknown("values", "没有授权记忆包含明确价值线索"));
        if (habits.isEmpty()) unknowns.add(unknown("habits", "没有授权记忆包含明确习惯线索"));
        if (temporalState.isEmpty()) unknowns.add(unknown("temporalState", "没有授权记忆包含明确近期状态线索"));

        Map<String, Object> ir = new LinkedHashMap<>();
        ir.put("schemaVersion", "capsule-genome-ir.v1");
        ir.put("claims", claims);
        ir.put("values", values);
        ir.put("habits", habits);
        ir.put("temporalState", temporalState);
        ir.put("unknowns", unknowns);
        ir.put("compilerNotice", "确定性显式线索提取，只证明来源可追溯，不证明真人相似度");
        return ir;
    }

    private Map<String, Object> emptyGenomeIr(String reason) {
        Map<String, Object> ir = new LinkedHashMap<>();
        ir.put("schemaVersion", "capsule-genome-ir.v1");
        ir.put("claims", List.of());
        ir.put("values", List.of());
        ir.put("habits", List.of());
        ir.put("temporalState", List.of());
        ir.put("unknowns", List.of(unknown("all", reason)));
        ir.put("compilerNotice", "没有证据时不生成身份或行为断言");
        return ir;
    }

    private Map<String, Object> irFeature(String prefix, MemoryCard card, String statement, String scope) {
        double confidence = card.confidence == null ? 0.8 : Math.max(0.0, Math.min(1.0, card.confidence));
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("memoryId", card.id);
        evidence.put("sourceVersion", card.versionNo == null ? 1 : card.versionNo);
        evidence.put("confidence", confidence);
        Map<String, Object> feature = new LinkedHashMap<>();
        feature.put("id", prefix + "-memory-" + card.id + "-v" + (card.versionNo == null ? 1 : card.versionNo));
        feature.put("statement", statement);
        feature.put("scope", scope);
        feature.put("confidence", confidence);
        feature.put("evidence", List.of(evidence));
        feature.put("extractionMethod", "claim".equals(prefix)
                ? "DETERMINISTIC_EPISODE_PROJECTION" : "DETERMINISTIC_EXPLICIT_CUE");
        return feature;
    }

    private Map<String, Object> unknown(String category, String reason) {
        return Map.of("category", category, "reason", reason, "fallback", "ACKNOWLEDGE_UNKNOWN");
    }

    private boolean containsCue(String text, List<String> cues) {
        return cues.stream().anyMatch(text::contains);
    }

    private Map<String, Object> retrievalPolicy() {
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("schemaVersion", "capsule-retrieval-policy.v1");
        policy.put("intentToCategory", Map.of(
                "CLAIM", List.of("claims"), "VALUE", List.of("values"),
                "HABIT", List.of("habits"), "TEMPORAL", List.of("temporalState")));
        policy.put("maxFeaturesPerTurn", 3);
        policy.put("unsupportedBehavior", "ACKNOWLEDGE_UNKNOWN");
        policy.put("neverRetrieve", List.of("未授权记忆", "原始对话全文", "真实身份", "联系方式"));
        return policy;
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception impossible) {
            throw new IllegalStateException("Unable to serialize capsule compiler output", impossible);
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String safeContactPolicy(String value) {
        if ("DIRECT_REQUEST".equals(value) || "STAND_IN_FIRST".equals(value) || "NO_REAL_CONTACT".equals(value)) return value;
        return "LETTER_ONLY";
    }

    private List<String> castStringList(Object raw) {
        if (raw instanceof List<?> list) {
            return list.stream().map(String::valueOf).filter(s -> !s.isBlank()).toList();
        }
        if (raw == null || String.valueOf(raw).isBlank()) return List.of();
        return Arrays.stream(String.valueOf(raw).split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
    }

    private List<Long> parseLongIds(String raw) {
        List<Long> result = new ArrayList<>();
        for (String item : parseTags(raw)) {
            try {
                result.add(Long.parseLong(item.replace("#", "")));
            } catch (Exception ignored) {
            }
        }
        return result;
    }
}
