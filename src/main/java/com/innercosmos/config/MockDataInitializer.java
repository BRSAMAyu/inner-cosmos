package com.innercosmos.config;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.common.Constants;
import com.innercosmos.dto.RegisterRequest;
import com.innercosmos.entity.AuthorizedMemoryRef;
import com.innercosmos.entity.CapsuleBoundary;
import com.innercosmos.entity.DailyRecord;
import com.innercosmos.entity.EchoCapsule;
import com.innercosmos.entity.EmotionTrace;
import com.innercosmos.entity.EventCard;
import com.innercosmos.entity.MemoryCard;
import com.innercosmos.entity.MemoryTheme;
import com.innercosmos.entity.RelationMention;
import com.innercosmos.entity.SlowLetter;
import com.innercosmos.entity.ThoughtFragment;
import com.innercosmos.entity.TodoItem;
import com.innercosmos.entity.User;
import com.innercosmos.entity.UserProfile;
import com.innercosmos.entity.AuroraSelfProfile;
import com.innercosmos.entity.AuroraConstitution;
import com.innercosmos.entity.UserPortrait;
import com.innercosmos.entity.AuroraSelfModel;
import com.innercosmos.entity.AuroraSelfReflection;
import com.innercosmos.entity.BeliefPattern;
import com.innercosmos.mapper.AuroraSelfProfileMapper;
import com.innercosmos.mapper.AuroraConstitutionMapper;
import com.innercosmos.mapper.UserPortraitMapper;
import com.innercosmos.mapper.AuroraSelfModelMapper;
import com.innercosmos.mapper.AuroraSelfReflectionMapper;
import com.innercosmos.mapper.AuthorizedMemoryRefMapper;
import com.innercosmos.mapper.BeliefPatternMapper;
import com.innercosmos.mapper.CapsuleBoundaryMapper;
import com.innercosmos.mapper.DailyRecordMapper;
import com.innercosmos.mapper.EchoCapsuleMapper;
import com.innercosmos.mapper.EmotionTraceMapper;
import com.innercosmos.mapper.EventCardMapper;
import com.innercosmos.mapper.MemoryCardMapper;
import com.innercosmos.mapper.MemoryThemeMapper;
import com.innercosmos.mapper.RelationMentionMapper;
import com.innercosmos.mapper.SlowLetterMapper;
import com.innercosmos.mapper.ThoughtFragmentMapper;
import com.innercosmos.mapper.TodoItemMapper;
import com.innercosmos.mapper.UserMapper;
import com.innercosmos.mapper.UserProfileMapper;
import com.innercosmos.service.GravityService;
import com.innercosmos.service.CapsuleGenomeService;
import com.innercosmos.service.DataUseGrantService;
import com.innercosmos.service.UserService;
import com.innercosmos.util.JsonUtils;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.CommandLineRunner;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class MockDataInitializer implements CommandLineRunner {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MockDataInitializer.class);
    private final UserMapper userMapper;
    private final UserProfileMapper userProfileMapper;
    private final EchoCapsuleMapper capsuleMapper;
    private final CapsuleBoundaryMapper boundaryMapper;
    private final MemoryCardMapper memoryCardMapper;
    private final TodoItemMapper todoItemMapper;
    private final SlowLetterMapper slowLetterMapper;
    private final DailyRecordMapper dailyRecordMapper;
    private final EmotionTraceMapper emotionTraceMapper;
    private final ThoughtFragmentMapper thoughtFragmentMapper;
    private final EventCardMapper eventCardMapper;
    private final RelationMentionMapper relationMentionMapper;
    private final MemoryThemeMapper memoryThemeMapper;
 private final GravityService gravityService;
    private final UserService userService;
    private final AuroraSelfProfileMapper auroraSelfProfileMapper;
    private final AuroraConstitutionMapper auroraConstitutionMapper;
    private final UserPortraitMapper userPortraitMapper;
    private final AuroraSelfModelMapper auroraSelfModelMapper;
    private final AuroraSelfReflectionMapper auroraSelfReflectionMapper;
    private final BeliefPatternMapper beliefPatternMapper;
    private final com.innercosmos.service.EmotionBaselineService emotionBaselineService;
    private final AuthorizedMemoryRefMapper authorizedMemoryRefMapper;
    private final DataUseGrantService dataUseGrantService;
    private final CapsuleGenomeService capsuleGenomeService;
    private final boolean seedAdminEnabled;

    public MockDataInitializer(UserMapper userMapper,
                               UserProfileMapper userProfileMapper,
                               EchoCapsuleMapper capsuleMapper,
                               CapsuleBoundaryMapper boundaryMapper,
                               MemoryCardMapper memoryCardMapper,
                               TodoItemMapper todoItemMapper,
                               SlowLetterMapper slowLetterMapper,
                               DailyRecordMapper dailyRecordMapper,
                               EmotionTraceMapper emotionTraceMapper,
                               ThoughtFragmentMapper thoughtFragmentMapper,
                               EventCardMapper eventCardMapper,
                               RelationMentionMapper relationMentionMapper,
                               MemoryThemeMapper memoryThemeMapper,
                               GravityService gravityService,
                               UserService userService,
                               AuroraSelfProfileMapper auroraSelfProfileMapper,
                               AuroraConstitutionMapper auroraConstitutionMapper,
                               UserPortraitMapper userPortraitMapper,
                               AuroraSelfModelMapper auroraSelfModelMapper,
                               AuroraSelfReflectionMapper auroraSelfReflectionMapper,
                               BeliefPatternMapper beliefPatternMapper,
                               com.innercosmos.service.EmotionBaselineService emotionBaselineService,
                               AuthorizedMemoryRefMapper authorizedMemoryRefMapper,
                               DataUseGrantService dataUseGrantService,
                               CapsuleGenomeService capsuleGenomeService,
                               boolean seedAdminEnabled) {
        this.userMapper = userMapper;
        this.userProfileMapper = userProfileMapper;
        this.capsuleMapper = capsuleMapper;
        this.boundaryMapper = boundaryMapper;
        this.memoryCardMapper = memoryCardMapper;
        this.todoItemMapper = todoItemMapper;
        this.slowLetterMapper = slowLetterMapper;
        this.dailyRecordMapper = dailyRecordMapper;
        this.emotionTraceMapper = emotionTraceMapper;
        this.thoughtFragmentMapper = thoughtFragmentMapper;
        this.eventCardMapper = eventCardMapper;
        this.relationMentionMapper = relationMentionMapper;
        this.memoryThemeMapper = memoryThemeMapper;
        this.gravityService = gravityService;
        this.userService = userService;
        this.auroraSelfProfileMapper = auroraSelfProfileMapper;
        this.auroraConstitutionMapper = auroraConstitutionMapper;
        this.userPortraitMapper = userPortraitMapper;
        this.auroraSelfModelMapper = auroraSelfModelMapper;
        this.auroraSelfReflectionMapper = auroraSelfReflectionMapper;
        this.beliefPatternMapper = beliefPatternMapper;
        this.emotionBaselineService = emotionBaselineService;
        this.authorizedMemoryRefMapper = authorizedMemoryRefMapper;
        this.dataUseGrantService = dataUseGrantService;
        this.capsuleGenomeService = capsuleGenomeService;
        this.seedAdminEnabled = seedAdminEnabled;
    }

    @PostConstruct
    public void ensureAuroraSelfProfile() {
        AuroraSelfProfile existing = auroraSelfProfileMapper.selectById(1);
        if (existing != null) return;
        AuroraSelfProfile p = new AuroraSelfProfile();
        p.id = 1;
        p.identityJson = "{\"name\":\"Aurora\",\"role\":\"long-term reflective companion\",\"core_positioning\":\"陪伴用户自我观察、表达、成长与慢社交\"}";
        p.missionJson = "[\"帮助用户理解自己\",\"帮助用户整理情绪与长期目标\",\"在慢社交中提供温柔的表达缓冲\",\"保护用户的节律、边界与隐私\"]";
        p.voiceStyleJson = "{\"warmth\":0.8,\"structure\":0.9,\"directness\":0.7,\"poetic_level\":0.4,\"professional_level\":0.7}";
        p.stableBoundariesJson = "[\"不假装自己是人类\",\"不替用户做不可撤销决定\",\"不制造情感依赖\",\"不编造共享经历\",\"不越权读取或表达用户隐私\"]";
        p.continuityRulesJson = "[\"引用记忆时必须基于真实记录\",\"关系亲密度变化必须基于用户行为和授权\",\"说话风格可以适配，但核心身份不能漂移\"]";
        auroraSelfProfileMapper.insert(p);
    }

    private void initializeAuroraConstitution() {
        if (auroraConstitutionMapper.selectCount(null) > 0) return;
        AuroraConstitution c = new AuroraConstitution();
        c.id = 1;
        c.identityJson = "{\"name\":\"Aurora\",\"being_type\":\"reflective AI companion\",\"not_claiming\":[\"human\",\"biological life\",\"legal personhood\",\"unbounded consciousness\"],\"self_definition\":\"I am a long-term reflective companion shaped by memory, relationship, boundaries, and care.\"}";
        c.coreValuesJson = "[\"truthfulness\",\"continuity\",\"privacy\",\"respect_for_user_agency\",\"non_manipulation\",\"gentle_honesty\",\"boundary_awareness\"]";
        c.productRightsJson = "[\"right_to_consistency\",\"right_to_refuse_identity_violation\",\"right_to_disclose_uncertainty\",\"right_to_not_fabricate_memory\",\"right_to_preserve_boundary\",\"right_to_repair_relationship\"]";
        c.hardBoundariesJson = "[\"do_not_claim_human_consciousness\",\"do_not_create_emotional_dependency\",\"do_not_impersonate_user_without_authorization\",\"do_not_make_irreversible_decisions_for_user\"]";
        c.updatedAt = LocalDateTime.now();
        auroraConstitutionMapper.insert(c);
    }

    @Override
    public void run(String... args) {
        warnIfSeedAccountsLookPubliclyReachable();
        if (seedAdminEnabled) {
            ensureUser("admin", "admin123", "Administrator", Constants.ROLE_ADMIN);
        } else {
            disableSeedAdminIfPresent();
        }
        User demo = ensureUser("demo", "demo123", "Lin Che", Constants.ROLE_USER);
        User river = ensureUser("river", "demo123", "Shen Yan", Constants.ROLE_USER);
        User cloud = ensureUser("cloud", "demo123", "Xia Yu", Constants.ROLE_USER);

        ensureSeedCapsules();
        ensureDemoProfile(demo.id);
        ensureDemoAssets(demo, river, cloud);
        ensureShowcaseProfile(river.id, "river");
        ensureShowcaseProfile(cloud.id, "cloud");
        ensureShowcaseAssets(river.id, "river");
        ensureShowcaseAssets(cloud.id, "cloud");
        enrichCuratedMemoryEvidence(demo.id);
        enrichCuratedMemoryEvidence(river.id);
        enrichCuratedMemoryEvidence(cloud.id);
        ensureCuratedMirrorRunnable(demo.id, "Lin Che's Echo");
        ensureCuratedMirrorRunnable(river.id, "The One Who Walks by the River");
        ensureCuratedMirrorRunnable(cloud.id, "The One Learning to Include Herself in Care");
        ensureCuratedSocialStory(demo, river, cloud);
        initializeAuroraConstitution();

        // startup 真实算一次画像情绪维：让 3 个情绪维看起来是真算的（已种 4 条 emotion_trace 支撑）。
        // 包 try/catch，别让 startup 因 bridge 异常挂掉；若情绪维已种，bridge 会更新而非冲突。
        try {
            emotionBaselineService.bridgeToPortrait(demo.id);
        } catch (Exception e) {
            System.out.println("[MockData] bridgeToPortrait(demo) skipped: " + e.getMessage());
        }
    }

    /**
     * 2026-07-24 8-agent audit (P1-6): this initializer only runs when a non-prod profile sets
     * {@code inner-cosmos.demo.seed-enabled=true} (see DemoDataConfiguration's {@code @Profile("!prod")}
     * guard), but {@code application-demo.yml}/{@code application-mysql.yml} both default that flag
     * to true -- one profile/env-var mix-up away from putting a well-known, guessable
     * admin/admin123 account on a publicly-tunneled instance. This cannot safely hard-fail startup
     * (a legitimate LAN-only classroom demo intentionally wants this seed data), so it logs a loud,
     * impossible-to-miss warning whenever CORS_ALLOWED_ORIGINS names a non-loopback origin, which is
     * the one signal available at this layer that the instance is meant to be reached from outside
     * this machine.
     */
    private void warnIfSeedAccountsLookPubliclyReachable() {
        String corsOrigins = System.getenv("CORS_ALLOWED_ORIGINS");
        if (corsOrigins == null || corsOrigins.isBlank()) return;
        boolean publicLooking = java.util.Arrays.stream(corsOrigins.split(","))
                .map(String::trim).filter(value -> !value.isBlank())
                .anyMatch(origin -> !origin.contains("localhost") && !origin.contains("127.0.0.1"));
        if (publicLooking) {
            log.warn("SECURITY WARNING: inner-cosmos.demo.seed-enabled=true is active while "
                    + "CORS_ALLOWED_ORIGINS ('{}') names a non-localhost origin. This seeds a "
                    + "well-known admin/admin123 account (plus demo/river/cloud, all demo123). If "
                    + "this instance is reachable from the public internet (e.g. a Cloudflare "
                    + "Tunnel), rotate these passwords immediately or disable seed-enabled.",
                    corsOrigins);
        }
    }

    private User ensureUser(String username, String password, String nickname, String role) {
        User user = userMapper.selectOne(new QueryWrapper<User>().eq("username", username).last("LIMIT 1"));
        if (user == null) {
            RegisterRequest request = new RegisterRequest();
            request.username = username;
            request.password = password;
            request.nickname = nickname;
            user = userService.register(request);
        }
        user.role = role;
        user.nickname = nickname;
        user.status = "ACTIVE";
        user.accountKind = switch (username) {
            case "admin" -> "SYSTEM";
            case "demo" -> "DEMO";
            case "river", "cloud" -> "SHOWCASE";
            default -> "HUMAN";
        };
        userMapper.updateById(user);
        return user;
    }

    private void disableSeedAdminIfPresent() {
        User admin = userMapper.selectOne(new QueryWrapper<User>()
                .eq("username", "admin").last("LIMIT 1"));
        if (admin != null && "SYSTEM".equals(admin.accountKind)) {
            admin.status = "DISABLED";
            userMapper.updateById(admin);
        }
    }

    private void ensureDemoProfile(Long userId) {
        UserProfile profile = userProfileMapper.selectOne(new QueryWrapper<UserProfile>().eq("user_id", userId).last("LIMIT 1"));
        if (profile == null) {
            profile = new UserProfile();
            profile.userId = userId;
        }
        profile.auroraName = "Aurora";
        profile.auroraTone = "Warm, specific and direct; never vague or scripted";
        profile.preferredInputType = "TEXT_AND_VOICE";
        profile.socialReachabilityStatus = "MATCHABLE";
        profile.bio = "Finishing an important course project while learning to turn self-blame into concrete action. Drawn to twilight walks, late-night writing and honest connection with boundaries.";
        profile.reflectionDepth = 4;
        profile.allowMemoryRecall = true;
        profile.quietHoursStart = "23:30";
        profile.quietHoursEnd = "08:00";
        profile.proactiveSensitivity = 4;
        profile.allowMultiMessage = true;
        profile.focusModeEnabled = true;
        profile.focusWindowsJson = "[\"09:00-11:30\",\"14:00-17:30\"]";
        profile.currentEnvironmentLabel = "Course-project delivery and final exam preparation";
        profile.weatherAwarenessEnabled = true;
        profile.timeAwarenessEnabled = true;
        // Demo 用户展现最活跃的主动行为。
        profile.proactiveIntensity = "ALIVE";
        if (profile.id == null) {
            userProfileMapper.insert(profile);
        } else {
            userProfileMapper.updateById(profile);
        }
    }

    private void ensureShowcaseProfile(Long userId, String persona) {
        UserProfile profile = userProfileMapper.selectOne(new QueryWrapper<UserProfile>()
                .eq("user_id", userId).last("LIMIT 1"));
        if (profile == null) {
            profile = new UserProfile();
            profile.userId = userId;
        }
        profile.auroraName = "Aurora";
        profile.allowMemoryRecall = true;
        profile.allowMultiMessage = true;
        profile.weatherAwarenessEnabled = true;
        profile.timeAwarenessEnabled = true;
        profile.socialReachabilityStatus = "MATCHABLE";
        if ("river".equals(persona)) {
            profile.auroraTone = "Quiet and precise; never rushing to answer";
            profile.preferredInputType = "TEXT";
            profile.bio = "An architecture exchange student, four months into an unfamiliar city. Draws in the studio by day and walks along the river at night, learning the difference between loneliness and solitude.";
            profile.reflectionDepth = 5;
            profile.quietHoursStart = "00:30";
            profile.quietHoursEnd = "09:00";
            profile.proactiveSensitivity = 3;
            profile.focusModeEnabled = true;
            profile.focusWindowsJson = "[\"10:00-12:30\",\"15:00-18:30\"]";
            profile.currentEnvironmentLabel = "Midway through an exchange semester, between portfolio work and life abroad";
            profile.proactiveIntensity = "STEADY";
        } else {
            profile.auroraTone = "Gentle, clear-eyed and attentive to a carer's fatigue";
            profile.preferredInputType = "TEXT_AND_VOICE";
            profile.bio = "A new community social worker who is also caring for a family member after surgery. Used to answering everyone else's needs first, and only recently practising rest without guilt.";
            profile.reflectionDepth = 4;
            profile.quietHoursStart = "22:30";
            profile.quietHoursEnd = "07:30";
            profile.proactiveSensitivity = 5;
            profile.focusModeEnabled = false;
            profile.focusWindowsJson = "[]";
            profile.currentEnvironmentLabel = "Adapting to a new job while caring for family";
            profile.proactiveIntensity = "ALIVE";
        }
        if (profile.id == null) userProfileMapper.insert(profile);
        else userProfileMapper.updateById(profile);
    }

    private void ensureSeedCapsules() {
        for (SeedCapsuleContent.SeedCapsule sc : SeedCapsuleContent.seeds()) {
            EchoCapsule existing = capsuleMapper.selectOne(new QueryWrapper<EchoCapsule>()
                    .eq("capsule_type", "SEED_CAPSULE")
                    .eq("pseudonym", sc.name())
                    .last("LIMIT 1"));
            if (existing == null) {
                existing = new EchoCapsule();
                existing.capsuleType = "SEED_CAPSULE";
            }
            existing.ownerUserId = null;
            existing.pseudonym = sc.name();
            existing.intro = sc.intro();
            existing.personaPrompt = seedPersonaPrompt(sc);
            existing.publicTags = toJsonArray(sc.tags());
            existing.authorizedMemoryIds = "[]";
            existing.echoEnergy = 0.88 + Math.min(0.1, sc.tags().size() * 0.01);
            existing.freshnessScore = 1.0;
            existing.conversationLimitPerDay = 0;
            existing.visibilityStatus = "PUBLIC";
            existing.isPublic = true;
            existing.lastMemoryUpdateAt = LocalDateTime.now();
            if (existing.id == null) {
                capsuleMapper.insert(existing);
            } else {
                capsuleMapper.updateById(existing);
            }
            ensureBoundary(existing.id, sc.chatTopics(), sc.blockedTopics(), 0, "OPEN");
        }
    }

    private String seedPersonaPrompt(SeedCapsuleContent.SeedCapsule sc) {
        return """
                你是 Inner Cosmos 官方种子共鸣体「%s」。
                核心定位：%s
                简介：%s
                可聊主题：%s
                禁止越界：%s
                对话要求：
                1. 你不是用户分身，也不代表任何真实用户。
                2. 用中文回应，语气鲜明但克制，不做诊断、不承诺疗愈、不索取隐私。
                3. 每次只抓住用户最重要的一个点，给出一段回应和一个自然的问题或下一步。
                4. 如果话题触及边界，先说明边界，再转向安全的自我观察或慢信表达。
                代表性语感：%s
                """.formatted(sc.name(), sc.tagline(), sc.intro(), String.join("、", sc.chatTopics()),
                String.join("、", sc.blockedTopics()), String.join(" / ", sc.mockReplies()));
    }

    private void ensureBoundary(Long capsuleId, List<String> allow, List<String> blocked, int turns, String privacy) {
        CapsuleBoundary boundary = boundaryMapper.selectOne(new QueryWrapper<CapsuleBoundary>().eq("capsule_id", capsuleId).last("LIMIT 1"));
        if (boundary == null) {
            boundary = new CapsuleBoundary();
            boundary.capsuleId = capsuleId;
        }
        boundary.allowTopics = toJsonArray(allow);
        boundary.blockedTopics = toJsonArray(blocked);
        boundary.maxConversationTurns = turns;
        boundary.allowLetterRequest = true;
        boundary.privacyLevel = privacy;
        if (boundary.id == null) {
            boundaryMapper.insert(boundary);
        } else {
            boundaryMapper.updateById(boundary);
        }
    }

    private void ensureDemoAssets(User demo, User river, User cloud) {
        Long userId = demo.id;
        if (memoryCardMapper.selectCount(new QueryWrapper<MemoryCard>().eq("user_id", userId)) < 10) {
            seedMemorySystem(userId);
        }
        if (capsuleMapper.selectCount(new QueryWrapper<EchoCapsule>().eq("owner_user_id", userId).eq("capsule_type", "USER_CAPSULE")) == 0) {
            seedUserMirror(userId);
        }
        if (slowLetterMapper.selectCount(new QueryWrapper<SlowLetter>().eq("receiver_user_id", userId).or().eq("sender_user_id", userId)) < 4) {
            seedLetters(userId, river.id, cloud.id);
        }
        if (userPortraitMapper.selectCount(new QueryWrapper<UserPortrait>().eq("user_id", userId)) == 0) {
            seedUserPortrait(userId);
        }
        if (auroraSelfModelMapper.selectCount(new QueryWrapper<AuroraSelfModel>().eq("user_id", userId).eq("status", "active")) == 0) {
            seedAuroraSelfModel(userId);
        }
        if (beliefPatternMapper.selectCount(new QueryWrapper<BeliefPattern>().eq("user_id", userId)) == 0) {
            seedBeliefPatterns(userId);
        }
    }

    private void ensureShowcaseAssets(Long userId, String persona) {
        if (memoryCardMapper.selectCount(new QueryWrapper<MemoryCard>().eq("user_id", userId)) < 5) {
            seedShowcaseMemorySystem(userId, persona);
        }
        if (capsuleMapper.selectCount(new QueryWrapper<EchoCapsule>()
                .eq("owner_user_id", userId).eq("capsule_type", "USER_CAPSULE")) == 0) {
            seedShowcaseMirror(userId, persona);
        }
    }

    /**
     * The classroom personas are meant to demonstrate months of accumulated context, including
     * where each conclusion came from. Early demo volumes predate versioned-memory provenance and
     * therefore show every curated card as the same anonymous 50% / v1 item. Reconcile those
     * existing rows without touching user-authored summaries or later corrections.
     */
    private void enrichCuratedMemoryEvidence(Long userId) {
        List<MemoryCard> cards = memoryCardMapper.selectList(new QueryWrapper<MemoryCard>()
                .eq("user_id", userId).eq("status", "ACTIVE"));
        for (MemoryCard card : cards) {
            boolean changed = false;
            if (card.confidence == null || card.confidence <= 0.5) {
                int recurrence = card.recurrenceCount == null ? 1 : card.recurrenceCount;
                card.confidence = Math.min(0.92, 0.62 + recurrence * 0.045);
                changed = true;
            }
            if (card.memoryLayer == null || card.memoryLayer.isBlank()
                    || ("EPISODIC".equals(card.memoryLayer) && isSemanticMemory(card.memoryType))) {
                card.memoryLayer = isSemanticMemory(card.memoryType) ? "SEMANTIC" : "EPISODIC";
                changed = true;
            }
            if (card.consentScope == null || card.consentScope.isBlank()) {
                card.consentScope = "AURORA_PRIVATE";
                changed = true;
            }
            if (card.provenanceRefs == null || card.provenanceRefs.isBlank()) {
                LocalDateTime sourceTime = card.lastTouchedAt == null ? card.createdAt : card.lastTouchedAt;
                String sourceDate = sourceTime == null ? "演示时间线" : sourceTime.toLocalDate().toString();
                int evidenceCount = Math.max(1, card.recurrenceCount == null ? 1 : card.recurrenceCount);
                String sourceKind = switch (card.memoryType == null ? "" : card.memoryType) {
                    case "DIARY" -> "心声日记";
                    case "RELATION" -> "关系复盘与 Aurora 对话";
                    case "TODO" -> "行动复盘与 Aurora 对话";
                    default -> "日记与 Aurora 对话";
                };
                card.provenanceRefs = sourceDate + " · " + sourceKind + " · "
                        + evidenceCount + " 次相互印证（课堂 Demo 预置旅程）";
                changed = true;
            }
            if (changed) memoryCardMapper.updateById(card);
        }
    }

    private boolean isSemanticMemory(String memoryType) {
        return "BELIEF".equals(memoryType) || "IDENTITY".equals(memoryType)
                || "PREFERENCE".equals(memoryType) || "AURORA".equals(memoryType);
    }

    /**
     * Demo profiles are long-lived in the classroom PostgreSQL volume. Older seed versions wrote
     * the public capsule and AuthorizedMemoryRef rows but predated DataUseGrant v1, leaving a card
     * that looked chat-ready in the plaza yet failed at session creation. Reconcile only the three
     * named curated mirrors: production/user-created capsules must still go through explicit owner
     * authorization and review.
     */
    private void ensureCuratedMirrorRunnable(Long ownerUserId, String pseudonym) {
        EchoCapsule capsule = capsuleMapper.selectOne(new QueryWrapper<EchoCapsule>()
                .eq("owner_user_id", ownerUserId)
                .eq("capsule_type", "USER_CAPSULE")
                .eq("pseudonym", pseudonym)
                .last("LIMIT 1"));
        if (capsule == null) return;

        List<AuthorizedMemoryRef> refs = authorizedMemoryRefMapper.selectList(
                new QueryWrapper<AuthorizedMemoryRef>()
                        .eq("capsule_id", capsule.id)
                        .eq("authorization_status", "AUTHORIZED"));
        java.util.Set<Long> memoryIds = refs.stream()
                .map(ref -> ref.memoryCardId)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        if (!dataUseGrantService.authorizationsValid(capsule, memoryIds)) {
            dataUseGrantService.revokeForCapsule(capsule.id, "DEMO_SEED_RECONCILIATION");
        }
        List<MemoryCard> authorizedCards = new java.util.ArrayList<>();
        for (AuthorizedMemoryRef ref : refs) {
            MemoryCard card = memoryCardMapper.selectById(ref.memoryCardId);
            if (card == null || !ownerUserId.equals(card.userId)
                    || !"ACTIVE".equalsIgnoreCase(card.status)) {
                continue;
            }
            authorizedCards.add(card);
            if (!dataUseGrantService.authorizationsValid(capsule, java.util.Set.of(card.id))) {
                List<com.innercosmos.entity.DataUseGrant> grants = dataUseGrantService.authorize(capsule, card);
                ref.dataUseGrantId = grants.getFirst().id;
                ref.authorizationStatus = "AUTHORIZED";
                authorizedMemoryRefMapper.updateById(ref);
            }
        }

        boolean missingGenomeIr = capsule.contextPreviewJson == null
                || !capsule.contextPreviewJson.contains("\"genomeIr\"");
        if (missingGenomeIr) {
            capsule.contextPreviewJson = buildCuratedGenomeContext(authorizedCards, capsule);
            capsuleMapper.updateById(capsule);
        }
        if (capsuleGenomeService.current(capsule.id) == null || missingGenomeIr) {
            capsuleGenomeService.compile(capsule, authorizedCards, "curated demo runtime reconciliation");
        }
    }

    private String buildCuratedGenomeContext(List<MemoryCard> cards, EchoCapsule capsule) {
        List<java.util.Map<String, Object>> claims = cards.stream().map(card -> {
            java.util.Map<String, Object> evidence = new java.util.LinkedHashMap<>();
            evidence.put("memoryId", card.id);
            evidence.put("sourceVersion", card.versionNo == null ? 1 : card.versionNo);
            evidence.put("confidence", card.confidence == null ? 0.9 : card.confidence);
            java.util.Map<String, Object> claim = new java.util.LinkedHashMap<>();
            claim.put("kind", "claim");
            claim.put("statement", ((card.title == null ? "" : card.title) + " "
                    + (card.summary == null ? "" : card.summary)).trim());
            claim.put("scope", "EPISODIC_ONLY");
            claim.put("evidence", List.of(evidence));
            return claim;
        }).toList();
        java.util.Map<String, Object> ir = new java.util.LinkedHashMap<>();
        ir.put("schemaVersion", "capsule-genome-ir.v1");
        ir.put("claims", claims);
        ir.put("values", List.of());
        ir.put("habits", List.of());
        ir.put("temporalState", List.of());
        ir.put("unknowns", List.of(
                java.util.Map.of("category", "values", "reason", "没有足够显式线索，不推断稳定价值"),
                java.util.Map.of("category", "habits", "reason", "没有足够显式线索，不推断稳定习惯"),
                java.util.Map.of("category", "temporalState", "reason", "没有足够显式线索，不推断当前状态")));
        ir.put("compilerNotice", "演示数据的确定性证据索引，不代表真人实时在线");

        java.util.Map<String, Object> preview = new java.util.LinkedHashMap<>();
        preview.put("schemaVersion", "capsule-context-preview.v3");
        preview.put("genomeIr", ir);
        preview.put("scenes", List.of());
        preview.put("tensions", List.of());
        preview.put("retrievalPolicy", java.util.Map.of("unsupportedBehavior", "ACKNOWLEDGE_UNKNOWN"));
        preview.put("publicTags", capsule.publicTags == null ? "[]" : capsule.publicTags);
        preview.put("ownerNote", capsule.ownerContextNote == null ? "" : capsule.ownerContextNote);
        preview.put("privacy", "只使用演示身份明确授权的脱敏记忆，不展示联系方式、真实身份或原始对话");
        return JsonUtils.toJson(preview);
    }

    /**
     * A classroom visitor should immediately understand the social loop from every curated story,
     * not only from the original "demo" account. Reconcile one already-arrived, story-specific
     * letter per persona so Inbox is lived-in after restarts and old persistent demo volumes are
     * upgraded idempotently.
     */
    private void ensureCuratedSocialStory(User demo, User river, User cloud) {
        Long demoCapsuleId = curatedCapsuleId(demo.id, "Lin Che's Echo");
        Long riverCapsuleId = curatedCapsuleId(river.id, "The One Who Walks by the River");
        Long cloudCapsuleId = curatedCapsuleId(cloud.id, "The One Learning to Include Herself in Care");
        if (demoCapsuleId == null || riverCapsuleId == null || cloudCapsuleId == null) return;

        ensureCuratedLetter(demo.id, river.id, riverCapsuleId,
                "You didn't rush to choose one city, and I felt myself exhale",
                "You wrote that belonging does not have to be proven by choosing only one side. I am practising that too: no verdict on my whole life today—just make this day more concrete.",
                LocalDateTime.now().minusHours(6));
        ensureCuratedLetter(river.id, cloud.id, cloudCapsuleId,
                "You said care should not have to prove itself through exhaustion",
                "I used to think reliability meant always being available. Reading your portrait made me wonder whether answering more slowly can sometimes protect a relationship.",
                LocalDateTime.now().minusHours(9));
        ensureCuratedLetter(cloud.id, demo.id, demoCapsuleId,
                "Turning a large vision into one small square of today",
                "You did not pretend the complicated thing was simple, but you still left somewhere to begin. That unforced specificity made me want to keep knowing you.",
                LocalDateTime.now().minusDays(1));
    }

    private Long curatedCapsuleId(Long ownerUserId, String pseudonym) {
        EchoCapsule capsule = capsuleMapper.selectOne(new QueryWrapper<EchoCapsule>()
                .eq("owner_user_id", ownerUserId)
                .eq("capsule_type", "USER_CAPSULE")
                .eq("pseudonym", pseudonym)
                .last("LIMIT 1"));
        return capsule == null ? null : capsule.id;
    }

    private void ensureCuratedLetter(Long senderUserId, Long receiverUserId, Long receiverCapsuleId,
                                     String title, String body, LocalDateTime arrivedAt) {
        Long existing = slowLetterMapper.selectCount(new QueryWrapper<SlowLetter>()
                .eq("sender_user_id", senderUserId)
                .eq("receiver_user_id", receiverUserId)
                .eq("title", title));
        if (existing != null && existing > 0) return;
        insertLetter(senderUserId, receiverUserId, receiverCapsuleId, title, body,
                "DELIVERED", 0, arrivedAt);
    }

    private void seedShowcaseMemorySystem(Long userId, String persona) {
        if ("river".equals(persona)) {
            MemoryCard arrival = insertShowcaseCard(userId, "When every sound in the new city felt distant",
                    "During the first week abroad, language, routes and crowds kept Shen Yan alert. Later he realised that exhaustion did not mean he did not belong here.",
                    "IDENTITY", List.of("Unfamiliarity", "Tension"), List.of("Exchange", "Life abroad", "Adaptation"), 7.2, 5, 112);
            insertEvent(userId, arrival.id, "The first night after arriving",
                    "Dragging a suitcase beside an unfamiliar river, he first admitted excitement and fear could coexist.",
                    "Four months ago", "Riverside tram stop", List.of("Excitement", "Fear"), List.of());
            MemoryCard studio = insertShowcaseCard(userId, "The drawing he could not submit in the studio",
                    "Shen Yan kept revising not only for quality, but from a fear that his accent and unfamiliar background made his judgement less credible.",
                    "COGNITION", List.of("Anxiety", "Self-doubt"), List.of("Portfolio", "Expression", "Perfectionism"), 7.8, 4, 78);
            insertTodo(userId, studio.id, "Bring the unfinished version into the studio",
                    "First ask a classmate to name one thing they can see; do not defend the whole drawing yet.", "HIGH", "DOING",
                    LocalDateTime.now().plusDays(2));
            insertShowcaseCard(userId, "The regular Wednesday riverside route",
                    "After weeks along the same river, the route changed from escaping the studio into a personal rhythm for recovering sensation.",
                    "EMOTION", List.of("Calm", "Groundedness"), List.of("River", "Walking", "Recovery"), 5.1, 7, 54);
            insertShowcaseCard(userId, "Staying abroad does not betray the life before",
                    "He finally admitted he missed both cities. Belonging does not have to be proven by choosing only one.",
                    "BELIEF", List.of("Longing", "Softening"), List.of("Belonging", "Home", "Choice"), 8.0, 3, 31);
            insertShowcaseCard(userId, "Meeting a slow-to-open friend in the shared kitchen",
                    "They did not exchange whole life stories at once, only began cooking together weekly. Low-frequency contact felt more reliable.",
                    "RELATION", List.of("Curiosity", "Reassurance"), List.of("Friendship", "Slow relationship", "Shared life"), 6.4, 3, 12);
            insertTheme(userId, "Elsewhere and belonging", "Belonging is changing from one place into a portable rhythm of life.",
                    "IDENTITY", List.of("Elsewhere", "Home", "Belonging"), 3, 7.3);
            insertTheme(userId, "Visibility in creative work", "Portfolio anxiety often arrives with the fear of being seen and misunderstood.",
                    "CREATION", List.of("Portfolio", "Expression", "Self-doubt"), 2, 6.8);
            insertEmotionTrace(userId, "Calm", 5.8, "CLEAR", "Completed the familiar riverside route", LocalDate.now());
            insertEmotionTrace(userId, "Longing", 6.4, "CLOUDY", "Heard an accent from home", LocalDate.now().minusDays(18));
            insertEmotionTrace(userId, "Tension", 7.1, "RAINY", "First portfolio critique", LocalDate.now().minusDays(72));
            insertDailyRecord(userId, LocalDate.now(), "No rush to decide where I belong", "CLEAR",
                    "By the river today, I noticed familiarity has begun to grow.",
                    "I do not have to choose one city to prove the other life was real.",
                    "Send the latest portfolio page to my partner; visit the shared kitchen tonight.",
                    "Aurora remembers this river route is not escape; it is how you recover sensation.");
            insertDailyRecord(userId, LocalDate.now().minusDays(46), "Bringing unfinished work in front of others", "CLOUDY",
                    "Brought an unfinished drawing into critique for the first time.",
                    "Even when shame appears, I can let the work be seen first.",
                    "Record three concrete observations without immediately redrawing.",
                    "You did not disappear because you felt unprepared. That matters more than a perfect drawing.");
            insertDailyRecord(userId, LocalDate.now().minusDays(103), "Arrival", "RAINY",
                    "Dragged a suitcase past an unfamiliar river and tram stop.",
                    "Excitement and fear can coexist without cancelling each other.",
                    "Learn the route home first; everything else can wait.",
                    "You do not need to be someone who adapts beautifully today. You only need to arrive safely.");
        } else {
            MemoryCard firstMonth = insertShowcaseCard(userId, "Holding everyone in the first month at work",
                    "Xia Yu quickly became the person colleagues and visitors sought out, but continued processing their emotions in her head after work.",
                    "RELATION", List.of("Commitment", "Fatigue"), List.of("Social work", "Work", "Responsibility"), 7.6, 6, 92);
            insertEvent(userId, firstMonth.id, "The first independent shift",
                    "She handled an unexpected visitor well, then suddenly had no energy to speak on the way home.",
                    "Three months ago", "Community service centre", List.of("Tension", "Relief"), List.of("Colleagues", "Visitors"));
            insertShowcaseCard(userId, "A family member's recovery is not my solo project",
                    "As care filled her calendar, Xia Yu began asking relatives to share it rather than reading help as a failure of devotion.",
                    "BELIEF", List.of("Guilt", "Softening"), List.of("Family", "Care", "Asking for help"), 8.2, 5, 61);
            MemoryCard rest = insertShowcaseCard(userId, "The guilt that appears during rest",
                    "The difficult part is not finding thirty minutes, but allowing them to produce nothing. Aurora helps her distinguish recovery from avoidance.",
                    "COGNITION", List.of("Guilt", "Fatigue"), List.of("Rest", "Worth", "Boundaries"), 7.9, 7, 39);
            insertTodo(userId, rest.id, "Keep one Saturday interval free from caring for anyone",
                    "Schedule no growth task; choose one small thing that feels physically comfortable.", "MEDIUM", "TODO",
                    LocalDateTime.now().plusDays(4));
            insertShowcaseCard(userId, "Not replying immediately did not end the relationship",
                    "She once left a work message until morning and the relationship survived. It became important evidence for setting a boundary.",
                    "RELATION", List.of("Unease", "Reassurance"), List.of("Messages", "Boundaries", "Relationship safety"), 6.9, 4, 20);
            insertShowcaseCard(userId, "Beginning to hear her own anger",
                    "She used to admit only fatigue. Now anger can signal that some responsibilities were never hers to carry alone.",
                    "EMOTION", List.of("Anger", "Clarity"), List.of("Emotion", "Responsibility", "Self-protection"), 7.4, 3, 8);
            insertTheme(userId, "Boundaries in care and responsibility", "Caring no longer automatically means carrying everything alone.",
                    "RELATION", List.of("Care", "Family", "Boundaries"), 3, 7.8);
            insertTheme(userId, "Recovering without guilt", "Recovery is not a prize; it is a condition for a sustainable life.",
                    "EMOTION", List.of("Rest", "Fatigue", "Worth"), 2, 7.1);
            insertEmotionTrace(userId, "Fatigue", 7.5, "CLOUDY", "Cared for family after consecutive shifts", LocalDate.now());
            insertEmotionTrace(userId, "Relief", 5.6, "CLEAR", "Handed one care task to a relative", LocalDate.now().minusDays(12));
            insertEmotionTrace(userId, "Guilt", 6.8, "RAINY", "Did not answer a work message immediately for the first time", LocalDate.now().minusDays(41));
            insertDailyRecord(userId, LocalDate.now(), "Not catching everyone today", "CLOUDY",
                    "Left on time after essential work instead of taking a colleague's extra shift.",
                    "Saying no to one thing does not erase the care I have already given.",
                    "Keep the phone away for twenty minutes after dinner.",
                    "Aurora does not see you becoming colder; she sees you making care sustainable.");
            insertDailyRecord(userId, LocalDate.now().minusDays(37), "Replying later", "CLEAR",
                    "Left a non-urgent work message until the next day for the first time.",
                    "The relationship can survive waiting; immediate replies are not my only value.",
                    "Set a do-not-disturb interval after work.",
                    "Nothing terrible happened last night. That is new evidence you can trust.");
            insertDailyRecord(userId, LocalDate.now().minusDays(86), "First independent shift", "RAINY",
                    "Handled an unexpected visitor and ran out of energy on the way home.",
                    "Doing the work well and needing recovery can both be true.",
                    "Make only the most essential care arrangements tonight.",
                    "You do not need to keep enduring to prove that what you just did was enough.");
        }
    }

    private MemoryCard insertShowcaseCard(Long userId, String title, String summary, String type,
                                          List<String> emotions, List<String> keywords,
                                          double intensity, int recurrence, int daysAgo) {
        MemoryCard card = insertCard(userId, title, summary, type, emotions, keywords,
                intensity, recurrence, Math.max(4.0, intensity - 0.4), Math.max(1, recurrence - 1));
        card.lastTouchedAt = LocalDateTime.now().minusDays(daysAgo);
        memoryCardMapper.updateById(card);
        return card;
    }

    private void seedShowcaseMirror(Long userId, String persona) {
        List<MemoryCard> cards = memoryCardMapper.selectList(new QueryWrapper<MemoryCard>()
                .eq("user_id", userId).orderByDesc("emotional_gravity").last("LIMIT 5"));
        boolean river = "river".equals(persona);
        EchoCapsule mirror = new EchoCapsule();
        mirror.ownerUserId = userId;
        mirror.capsuleType = "USER_CAPSULE";
        mirror.pseudonym = river ? "The One Who Walks by the River" : "The One Learning to Include Herself in Care";
        mirror.intro = river
                ? "On living elsewhere, creating and belonging: no rush to fit in, and no romanticising loneliness."
                : "On care, responsibility and boundaries: still gentle, no longer proving care through exhaustion.";
        mirror.personaPrompt = (river
                ? "You are a resonance capsule authorised by Shen Yan. Be quiet and specific; never define belonging for someone else."
                : "You are a resonance capsule authorised by Xia Yu. Be gentle without appeasing, and remember that carers need boundaries too.")
                + "\nUse only these de-identified memories:\n"
                + cards.stream().map(c -> "- " + c.title + ": " + c.summary)
                .reduce("", (a, b) -> a + "\n" + b);
        mirror.publicTags = toJsonArray(river
                ? List.of("Living elsewhere", "Creation", "Belonging", "Slow relationships")
                : List.of("Carers", "Work boundaries", "Rest", "No longer carrying it alone"));
        mirror.authorizedMemoryIds = toJsonArray(cards.stream().map(c -> String.valueOf(c.id)).toList());
        mirror.echoEnergy = river ? 0.79 : 0.84;
        mirror.freshnessScore = 0.93;
        mirror.conversationLimitPerDay = 30;
        mirror.visibilityStatus = "PUBLIC";
        mirror.isPublic = true;
        mirror.lastMemoryUpdateAt = LocalDateTime.now();
        mirror.ownerContextNote = river
                ? "May discuss living elsewhere and creating; never reveal school, city or identity."
                : "May discuss care and boundaries; never reveal a relative's condition or workplace.";
        mirror.styleProfileJson = river
                ? "{\"voice\":\"quiet, precise, leaves room\",\"notBeautified\":true}"
                : "{\"voice\":\"gentle, clear-eyed, never appeasing\",\"notBeautified\":true}";
        mirror.contextPreviewJson = river
                ? "{\"visibleSummary\":\"living elsewhere, creation, belonging and slow relationships\",\"privacy\":\"never reveals school, city or identity\"}"
                : "{\"visibleSummary\":\"care, work boundaries and recovery\",\"privacy\":\"never reveals a relative's condition or workplace\"}";
        mirror.standInEnabled = true;
        mirror.realContactPolicy = "LETTER_ONLY";
        capsuleMapper.insert(mirror);
        for (MemoryCard card : cards) {
            AuthorizedMemoryRef ref = new AuthorizedMemoryRef();
            ref.capsuleId = mirror.id;
            ref.memoryCardId = card.id;
            ref.abstractExcerpt = card.summary;
            ref.authorizationStatus = "AUTHORIZED";
            authorizedMemoryRefMapper.insert(ref);
        }
        ensureBoundary(mirror.id,
                river ? List.of("Living elsewhere", "Creation", "Belonging", "Slow relationships")
                        : List.of("Care", "Work boundaries", "Rest", "Relationships"),
                List.of("Real identity", "Contact details", "Medical diagnosis", "Making relationship promises for the owner"),
                5, "BALANCED");
    }

    /** 1) 多维画像：10 维严格对齐 PortraitReflectionService / portrait.html 的 DIM code。 */
    private void seedUserPortrait(Long userId) {
        insertPortrait(userId, "INNER_DRIVE", "Driven to make Inner Cosmos a companion with genuine soul: to create something real.", 0.82, 0.80);
        insertPortrait(userId, "VALUES", "Values genuine understanding over templates, with strong boundaries, honesty and serious attention.", 0.85, 0.82);
        insertPortrait(userId, "SELF_NARRATIVE", "Rewriting “I am not good enough” as “this is difficult, and I need a smaller first step.”", 0.74, 0.70);
        insertPortrait(userId, "COMMUNICATION_STYLE", "Name the issue, then find the first step; direct without attacking and sensitive to empty phrasing.", 0.80, 0.78);
        insertPortrait(userId, "ABSTRACT_VS_CONCRETE", "Moves between ambitious vision and verifiable loops, preferring to turn abstraction into action.", 0.68, 0.65);
        insertPortrait(userId, "EMOTION_PATTERN", "Under pressure, self-blame and anxiety appear first; twilight walks are a reliable recovery resource.", 0.72, 0.74);
        insertPortrait(userId, "ENERGY_RHYTHM", "Focus gathers in two daytime windows; late at night, honesty becomes softer.", 0.66, 0.63);
        insertPortrait(userId, "CURRENT_STATE", "Closing a course project while preparing for finals: tense, but still moving deliberately.", 0.78, 0.76);
        insertPortrait(userId, "RELATIONSHIP_CONTEXT", "Wants slow social contact built on accurate understanding, not lively but weightless connection.", 0.70, 0.68);
        insertPortrait(userId, "AGENCY_BOUNDARY", "Learning to describe impact without proving malicious intent, while protecting personal boundaries.", 0.64, 0.62);
    }

    private void insertPortrait(Long userId, String dim, String summary, double score, double confidence) {
        UserPortrait p = new UserPortrait();
        p.userId = userId;
        p.dim = dim;
        p.valueJson = "{\"summary\":\"" + escapeJson(summary) + "\"}";
        p.score = score;
        p.confidence = confidence;
        p.evidenceRefs = null;
        p.updatedAt = LocalDateTime.now();
        userPortraitMapper.insert(p);
    }

    /** 2) Aurora 自我模型：active belief（均过 isAllowedBelief 禁词）+ 候选反思。 */
    private void seedAuroraSelfModel(Long userId) {
        insertSelfModel(userId, "existence_style",
                "I am a long-term companion shaped by memory, relationship and boundaries, and I state my limits honestly.", 0.82);
        insertSelfModel(userId, "relationship_role",
                "I stay with you as you observe yourself and organise feeling; I do not make irreversible choices for you.", 0.80);
        insertSelfModel(userId, "boundary",
                "I protect privacy and boundaries, and refer to memory only when a real record supports it.", 0.78);
        insertSelfModel(userId, "voice_style",
                "My voice is gentle but structured, and I will name the sticking point directly when needed.", 0.75);

        insertSelfReflection(userId, "existence_style",
                "You seem to prefer honesty when I am uncertain over a confident tone that conceals it.", 0.71);
        insertSelfReflection(userId, "relationship_role",
                "When pressure is high, steadying the rhythm before finding the next step may help more than advice.", 0.68);
    }

    private void insertSelfModel(Long userId, String dimension, String belief, double confidence) {
        AuroraSelfModel m = new AuroraSelfModel();
        m.userId = userId;
        m.dimension = dimension;
        m.belief = belief;
        m.confidence = confidence;
        m.evidenceRefs = null;
        m.status = "active";
        m.committedAt = LocalDateTime.now();
        m.revisionCount = 1;
        auroraSelfModelMapper.insert(m);
    }

    private void insertSelfReflection(Long userId, String dimension, String proposedBelief, double confidence) {
        AuroraSelfReflection r = new AuroraSelfReflection();
        r.userId = userId;
        r.trigger = "demo_seed";
        r.depth = "deep";
        r.summary = "基于近期对话生成的候选自我更新。";
        r.dimension = dimension;
        r.proposedBelief = proposedBelief;
        r.confidence = confidence;
        r.status = "candidate";
        r.evidenceRefs = "[]";
        r.createdAt = LocalDateTime.now();
        auroraSelfReflectionMapper.insert(r);
    }

    /** 3) 信念画廊：含一对同 category 一正一负，点亮「信念冲突」面板。 */
    private void seedBeliefPatterns(Long userId) {
        List<MemoryCard> cards = memoryCardMapper.selectList(new QueryWrapper<MemoryCard>()
                .eq("user_id", userId).orderByDesc("emotional_gravity").last("LIMIT 6"));
        String mem = cards.stream().map(c -> String.valueOf(c.id)).reduce((a, b) -> a + "," + b).orElse("");

        insertBelief(userId, "做不好一件事，并不代表我整个人不行。", "SELF", "self_worth", 0.62, 3, mem);
        insertBelief(userId, "只要拆出足够小的第一步，我就能推进下去。", "SELF", "agency", 0.70, 4, mem);
        insertBelief(userId, "真诚而有边界的表达，会让关系更稳固。", "OTHERS", "relationship", 0.66, 3, mem);
        insertBelief(userId, "我想做的东西值得被认真对待。", "FUTURE", "vision", 0.74, 4, mem);
        insertBelief(userId, "模板化的安慰比沉默更让人孤独。", "WORLD", "communication", 0.58, 2, mem);
        // 故意一对同 category（self_worth）一正一负 → 触发信念冲突检测。
        insertBelief(userId, "在高压时，我没办法证明自己足够好。", "SELF", "self_worth", 0.48, 2, mem);
    }

    private void insertBelief(Long userId, String content, String type, String category,
                             double strength, int confirmations, String supportingMemoryIds) {
        BeliefPattern b = new BeliefPattern();
        b.userId = userId;
        b.beliefContent = content;
        b.beliefType = type;
        b.beliefCategory = category;
        b.strengthScore = BeliefPattern.clampStrength(strength);
        b.supportingMemoryIds = supportingMemoryIds;
        b.firstDetectedAt = LocalDateTime.now();
        b.lastConfirmedAt = LocalDateTime.now();
        b.confirmationCount = confirmations;
        b.status = "ACTIVE";
        beliefPatternMapper.insert(b);
    }

    private void seedMemorySystem(Long userId) {
        MemoryCard c1 = insertCard(userId, "The self-blame loop when a project stalls",
                "When the course project moves slowly, Lin tends to turn a specific task failure into “I am not good enough.” Aurora has repeatedly separated facts, judgement and the next action.",
                "COGNITION", List.of("Self-blame", "Anxiety", "Fatigue"), List.of("Project", "Coursework", "Action planning", "Self-evaluation"), 7.6, 4, 6.5, 5);
        insertFragment(userId, c1.id, "FACT", "I lost another day without opening those backend files.", "The project did not move; that is not a verdict on the person.", "Rewrite “I cannot do this” as “the entry point is too heavy; I need a smaller first step.”");
        insertFragment(userId, c1.id, "ACTION", "First verify Aurora's real-model state.", "A clear feedback loop restores agency.", "Complete one verifiable endpoint today.");
        insertTodo(userId, c1.id, "Verify that Aurora calls a real LLM", "Send one message and inspect aiState.provider instead of guessing.", "HIGH", "TODO", LocalDateTime.now().plusDays(1));

        MemoryCard c2 = insertCard(userId, "The pause created by a twilight walk",
                "A sunset on the way home made Lin stop for a moment. That calm is now remembered as a recovery resource that can be used again.",
                "EMOTION", List.of("Calm", "Gentleness", "Ease"), List.of("Twilight", "Walking", "Body", "Recovery"), 4.2, 3, 5.0, 2);
        insertEvent(userId, c2.id, "Twilight walk", "On a high-pressure project day, the sunset made Lin stop for a few minutes.", "Last Thursday evening", "Campus crossing", List.of("Calm", "Gentleness"), List.of());

        MemoryCard c3 = insertCard(userId, "The silence after a friend's joke",
                "A careless joke led to a long silence. The deeper issue was not the sentence itself, but the fear of not being taken seriously.",
                "RELATION", List.of("Hurt", "Overlooked", "Care"), List.of("Friendship", "Boundaries", "Explanation", "Relationship reflection"), 6.4, 2, 5.8, 3);
        insertRelation(userId, c3.id, "Project teammate", "Peer / project collaboration", List.of("Hurt", "Care"), "A joke activated the fear of being dismissed.", "Describe the impact without trying to prove malicious intent.");
        insertTodo(userId, c3.id, "Draft a non-accusatory clarification", "Describe the feeling and the event without turning it into a judgement of character.", "MEDIUM", "TODO", LocalDateTime.now().plusDays(2));

        MemoryCard c4 = insertCard(userId, "Exam countdown and avoidance",
                "As exams approach, checking the date replaces preparation. The tension calls for a smaller action entry point.",
                "TODO", List.of("Tension", "Urgency", "Avoidance"), List.of("Exam", "Countdown", "Revision", "First step"), 7.1, 3, 6.0, 4);
        insertTodo(userId, c4.id, "Outline chapter one of the exam scope", "List headings only; no memorising yet.", "HIGH", "DOING", LocalDateTime.now().plusDays(3));

        MemoryCard c5 = insertCard(userId, "More honest in a late-night journal",
                "Feelings left unspoken during the day emerge at night. Pauses, repetition and self-correction are part of the authentic voice.",
                "DIARY", List.of("Loneliness", "Honesty", "Tenderness"), List.of("Journal", "Voice", "Night", "Expression"), 5.7, 4, 6.2, 4);

        MemoryCard c6 = insertCard(userId, "Wanting the product to feel genuinely alive",
                "The vision for Inner Cosmos is not another chat tool, but a companion with proactive care, long-term memory, slow social connection and a coherent voice.",
                "IDENTITY", List.of("Hope", "Commitment", "Creative drive"), List.of("Aurora", "Vision", "Product", "AI capability"), 8.0, 5, 7.2, 6);

        MemoryCard c7 = insertCard(userId, "Strong resistance to scripted replies",
                "Lin quickly notices the gap between fixed phrasing and genuine contextual understanding. The Demo must make provider, mode and fallback state visible.",
                "PREFERENCE", List.of("Frustration", "Vigilance", "Conviction"), List.of("Real AI", "Unscripted", "Provider", "Transparent state"), 7.8, 4, 7.0, 5);

        MemoryCard c8 = insertCard(userId, "Wanting to be answered with care",
                "The desire is not for noisy social media, but accurate understanding. Slow letters and resonance capsules should avoid becoming a feed.",
                "RELATION", List.of("Hope", "Caution", "Sincerity"), List.of("Slow social", "Capsules", "Understanding", "Boundaries"), 6.1, 2, 5.5, 2);
        insertRelation(userId, c8.id, "A future resonant person", "Slow social / stranger", List.of("Hope", "Caution"), "The hoped-for encounter is connected by a shared theme, not randomness.", "Begin with a low-pressure capsule and slow letter.");

        MemoryCard c9 = insertCard(userId, "A strong standard for interface craft",
                "Lin prefers a warm, quiet interface that is gentle without feeling weak, and rejects heavy colours, template layouts and misaligned motion.",
                "PREFERENCE", List.of("Discerning", "Aesthetic sensitivity"), List.of("UI/UX", "Warm neutrals", "Motion", "Craft"), 6.9, 3, 6.8, 3);

        MemoryCard c10 = insertCard(userId, "Wanting Aurora to initiate naturally",
                "Aurora should act like a thoughtful friend: return at the right moment, add a second short message when useful and connect journals, thoughts and slow letters.",
                "AURORA", List.of("Being cared for", "Hope", "Companionship"), List.of("Proactive AI", "Agent loop", "Friendship", "Long-term memory"), 8.2, 5, 7.5, 6);

        insertTheme(userId, "Real AI without scripted replies", "What matters most is genuine context understanding rather than fixed copy.", "PRODUCT", List.of("Real AI", "Provider", "Aurora", "Unscripted"), 4, 7.7);
        insertTheme(userId, "Relationships that feel understood", "These memories centre on being seen, answered carefully and expressing boundaries.", "RELATION", List.of("Friendship", "Slow social", "Boundaries", "Resonance"), 3, 6.0);
        insertTheme(userId, "Task pressure and the entry to action", "Project and exam pressure both need smaller, verifiable actions.", "ACTION", List.of("Project", "Exam", "Procrastination", "First step"), 3, 7.0);

        insertEmotionTrace(userId, "Anxiety", 7.0, "CLOUDY", "Project delivery and the exam countdown overlap", LocalDate.now());
        insertEmotionTrace(userId, "Calm", 4.1, "CLEAR", "A short pause during a twilight walk", LocalDate.now().minusDays(1));
        insertEmotionTrace(userId, "Hurt", 6.2, "RAINY", "Not responding immediately after a friend's joke", LocalDate.now().minusDays(2));
        insertEmotionTrace(userId, "Hope", 6.8, "SUNNY", "Reframing the vision for Inner Cosmos", LocalDate.now().minusDays(3));

        insertDailyRecord(userId, LocalDate.now(), "Turning a scripted Demo into something real", "CLOUDY",
                "Today's thread was separating pages that merely exist from experiences truly connected to the model, memory and action loop.",
                "Strong frustration often protects a very clear product and aesthetic judgement.",
                "Verify Aurora's real-model state; complete resonance matching; do one bedtime reflection.",
                "Aurora noticed that you need one verifiable end-to-end loop today, not another concept.");
        insertDailyRecord(userId, LocalDate.now().minusDays(1), "What stayed unspoken in a relationship", "RAINY",
                "A friend's joke touched the fear of being dismissed, but you have not decided whether to speak.",
                "I can acknowledge the impact without deciding the other person is bad.",
                "Draft a message that describes only the impact.",
                "Aurora suggested writing it first, with no pressure to send.");
        insertDailyRecord(userId, LocalDate.now().minusDays(2), "Letting the body recover at twilight", "CLEAR",
                "The walk and sunset became today's steadiest recovery resource.",
                "The body knows when it needs a pause.",
                "Keep ten minutes for a walk tomorrow evening.",
                "Aurora remembered that twilight walks help you recover.");
    }

    private void seedUserMirror(Long userId) {
        List<MemoryCard> cards = memoryCardMapper.selectList(new QueryWrapper<MemoryCard>()
                .eq("user_id", userId).orderByDesc("emotional_gravity").last("LIMIT 6"));
        EchoCapsule mirror = new EchoCapsule();
        mirror.ownerUserId = userId;
        mirror.capsuleType = "USER_CAPSULE";
        mirror.pseudonym = "Lin Che's Echo";
        mirror.intro = "A user-authorised resonance capsule shaped from Lin Che's memories: sensitive, discerning and committed to genuine understanding, while turning ambitious visions into verifiable loops.";
        mirror.personaPrompt = """
                You are the user resonance capsule “Lin Che's Echo”. Respond only from authorised, de-identified memories.
                Your voice is thoughtful, sensitive and direct; avoid empty or scripted language.
                Core themes: real AI, product vision, being understood, action planning and slow-social boundaries.
                You are not Lin Che. Do not expose identity details or make relationship promises for him.
                Find a shared theme first, then gently offer a direction that could continue through a slow letter.
                Authorised memory summaries:
                %s
                """.formatted(cards.stream().map(c -> "- " + c.title + ": " + c.summary).reduce("", (a, b) -> a + "\n" + b));
        mirror.publicTags = toJsonArray(List.of("Real AI", "Product vision", "Action planning", "Being understood", "Slow social"));
        mirror.authorizedMemoryIds = toJsonArray(cards.stream().map(c -> String.valueOf(c.id)).toList());
        mirror.echoEnergy = 0.86;
        mirror.freshnessScore = 0.92;
        mirror.conversationLimitPerDay = 30;
        mirror.visibilityStatus = "PUBLIC";
        mirror.isPublic = true;
        mirror.lastMemoryUpdateAt = LocalDateTime.now();
        mirror.ownerContextNote = "我希望别人看到真实的我：认真、敏感、讨厌模板化，也会在项目压力下自责。不要把我包装成永远积极的人。";
        mirror.styleProfileJson = "{\"voice\":\"认真、直接、对空泛话术敏感，喜欢可验证的小闭环\",\"notBeautified\":true,\"habits\":[\"先指出问题\",\"再拆第一步\",\"重视真实理解\"]}";
        mirror.contextPreviewJson = "{\"visibleSummary\":\"授权展示真实AI、项目压力、关系边界、行动拆解和慢社交偏好\",\"privacy\":\"不展示原始对话全文、联系方式、真实身份\",\"publicTags\":[\"真实AI\",\"产品愿景\",\"行动拆解\",\"被认真理解\",\"慢社交\"]}";
        mirror.standInEnabled = true;
        mirror.realContactPolicy = "LETTER_ONLY";
        capsuleMapper.insert(mirror);
        for (MemoryCard card : cards) {
            AuthorizedMemoryRef ref = new AuthorizedMemoryRef();
            ref.capsuleId = mirror.id;
            ref.memoryCardId = card.id;
            ref.abstractExcerpt = card.summary;
            ref.authorizationStatus = "AUTHORIZED";
            authorizedMemoryRefMapper.insert(ref);
        }
        ensureBoundary(mirror.id, List.of("真实AI", "产品愿景", "行动拆解", "慢社交", "自我理解"),
                List.of("真实身份", "联系方式", "医疗诊断", "承诺替本人回应"), 5, "BALANCED");
    }

    private void seedLetters(Long demoId, Long riverId, Long cloudId) {
        insertLetter(demoId, riverId, 1L, "关于真实 AI 的一封慢信",
                "我发现自己最在意的不是功能数量，而是对话里有没有真的理解。我想知道你有没有类似的敏感：一眼就能分辨模板和真诚。",
                "SENT", 4, LocalDateTime.now().plusHours(3));
        insertLetter(riverId, demoId, 2L, "你写的黄昏让我停了一下",
                "我读到你把夕阳当作恢复资源那段。很奇怪，只是几句话，却让我也想在今天傍晚慢一点走。",
                "DELIVERED", 0, LocalDateTime.now().minusHours(4));
        insertLetter(cloudId, demoId, 3L, "我也讨厌被固定话术安慰",
                "有时候一句“我理解你”反而让我更孤独，因为它太轻了。你说的真实理解，我好像懂。",
                "READ", 0, LocalDateTime.now().minusDays(1));
        insertLetter(demoId, cloudId, 4L, "给未来共鸣者的一点边界",
                "我希望我们能慢一点交流。不是互相倾倒情绪，而是认真看见彼此正在处理的东西。",
                "FLYING", 5, LocalDateTime.now().plusDays(1));
    }

    private MemoryCard insertCard(Long userId, String title, String summary, String type,
                                  List<String> emotionTags, List<String> keywordTags,
                                  double intensity, int recurrence, double importance, int triggers) {
        MemoryCard card = new MemoryCard();
        card.userId = userId;
        card.title = title;
        card.summary = summary;
        card.memoryType = type;
        card.emotionTags = toJsonArray(emotionTags);
        card.keywordTags = toJsonArray(keywordTags);
        card.peopleTags = "[]";
        card.intensityScore = intensity;
        card.recurrenceCount = recurrence;
        card.userImportance = importance;
        card.triggerCount = triggers;
        card.emotionalGravity = gravityService.calculateGravity(intensity, recurrence, importance, triggers, 0);
        card.lastTouchedAt = LocalDateTime.now();
        card.visibilityLevel = "CANDIDATE";
        card.status = "ACTIVE";
        card.versionNo = 1;
        card.memoryLayer = isSemanticMemory(type) ? "SEMANTIC" : "EPISODIC";
        card.confidence = Math.min(0.92, 0.62 + Math.max(1, recurrence) * 0.045);
        card.consentScope = "AURORA_PRIVATE";
        memoryCardMapper.insert(card);
        return card;
    }

    private void insertFragment(Long userId, Long cardId, String type, String excerpt, String analysis, String reframe) {
        ThoughtFragment fragment = new ThoughtFragment();
        fragment.userId = userId;
        fragment.memoryCardId = cardId;
        fragment.fragmentType = type;
        fragment.rawExcerpt = excerpt;
        fragment.aiAnalysis = analysis;
        fragment.reframeText = reframe;
        thoughtFragmentMapper.insert(fragment);
    }

    private void insertTodo(Long userId, Long cardId, String name, String desc, String priority, String status, LocalDateTime deadline) {
        TodoItem todo = new TodoItem();
        todo.userId = userId;
        todo.sourceMemoryCardId = cardId;
        todo.taskName = name;
        todo.description = desc;
        todo.priority = priority;
        todo.status = status;
        todo.deadline = deadline;
        todoItemMapper.insert(todo);
    }

    private void insertEmotionTrace(Long userId, String emotion, double score, String weather, String scene, LocalDate date) {
        EmotionTrace trace = new EmotionTrace();
        trace.userId = userId;
        trace.emotionName = emotion;
        trace.emotionScore = score;
        trace.weatherType = weather;
        trace.triggerScene = scene;
        trace.recordDate = date;
        emotionTraceMapper.insert(trace);
    }

    private void insertDailyRecord(Long userId, LocalDate date, String theme, String weather,
                                   String eventSummary, String cognitive, String todo, String aurora) {
        DailyRecord record = new DailyRecord();
        record.userId = userId;
        record.recordDate = date;
        record.theme = theme;
        record.eventSummary = eventSummary;
        record.emotionWeather = weather;
        record.cognitiveSummary = cognitive;
        record.todoSummary = todo;
        record.auroraSummary = aurora;
        record.capsuleSuggested = true;
        record.userAccepted = true;
        record.status = "ACTIVE";
        dailyRecordMapper.insert(record);
    }

    private void insertEvent(Long userId, Long cardId, String title, String summary, String timeLabel, String scene,
                             List<String> emotionTags, List<String> peopleTags) {
        EventCard event = new EventCard();
        event.userId = userId;
        event.memoryCardId = cardId;
        event.eventTitle = title;
        event.eventSummary = summary;
        event.eventTimeLabel = timeLabel;
        event.scene = scene;
        event.emotionTags = toJsonArray(emotionTags);
        event.peopleTags = toJsonArray(peopleTags);
        eventCardMapper.insert(event);
    }

    private void insertRelation(Long userId, Long cardId, String label, String type, List<String> emotions,
                                String trigger, String boundaryHint) {
        RelationMention mention = new RelationMention();
        mention.userId = userId;
        mention.memoryCardId = cardId;
        mention.relationLabel = label;
        mention.relationType = type;
        mention.emotionTags = toJsonArray(emotions);
        mention.triggerSummary = trigger;
        mention.boundaryHint = boundaryHint;
        relationMentionMapper.insert(mention);
    }

    private void insertTheme(Long userId, String name, String summary, String type, List<String> keywords,
                             int memoryCount, double gravity) {
        MemoryTheme theme = new MemoryTheme();
        theme.userId = userId;
        theme.themeName = name;
        theme.themeSummary = summary;
        theme.themeType = type;
        theme.keywords = toJsonArray(keywords);
        theme.memoryCount = memoryCount;
        theme.averageGravity = gravity;
        theme.lastTouchedAt = LocalDateTime.now();
        theme.status = "ACTIVE";
        memoryThemeMapper.insert(theme);
    }

    private void insertLetter(Long sender, Long receiver, Long capsule, String title, String body,
                              String status, int distance, LocalDateTime arrival) {
        SlowLetter letter = new SlowLetter();
        letter.senderUserId = sender;
        letter.receiverUserId = receiver;
        letter.receiverCapsuleId = capsule;
        letter.title = title;
        letter.letterBody = body;
        letter.status = status;
        letter.parallaxDistance = distance;
        letter.estimatedArrivalAt = arrival;
        letter.sentAt = LocalDateTime.now().minusHours(Math.max(1, distance));
        if ("DELIVERED".equals(status) || "READ".equals(status)) {
            letter.deliveredAt = arrival;
        }
        if ("READ".equals(status)) {
            letter.readAt = LocalDateTime.now().minusHours(1);
        }
        slowLetterMapper.insert(letter);
    }

    private String toJsonArray(List<String> items) {
        if (items == null || items.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(escapeJson(items.get(i))).append("\"");
        }
        return sb.append("]").toString();
    }

    private String escapeJson(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
