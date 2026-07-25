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
            ensureUser("admin", "admin123", "管理员", Constants.ROLE_ADMIN);
        } else {
            disableSeedAdminIfPresent();
        }
        User demo = ensureUser("demo", "demo123", "林澈", Constants.ROLE_USER);
        User river = ensureUser("river", "demo123", "沈砚", Constants.ROLE_USER);
        User cloud = ensureUser("cloud", "demo123", "夏榆", Constants.ROLE_USER);

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
        ensureCuratedMirrorRunnable(demo.id, "林澈的回声分身");
        ensureCuratedMirrorRunnable(river.id, "沿河缓慢生活的人");
        ensureCuratedMirrorRunnable(cloud.id, "把自己放回照护里的人");
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
        profile.auroraTone = "像熟悉的朋友，温柔但不空泛，必要时可以直接指出卡点";
        profile.preferredInputType = "TEXT_AND_VOICE";
        profile.socialReachabilityStatus = "MATCHABLE";
        profile.bio = "正在完成一个重要课程项目，也在学习把自责拆成更具体的行动。喜欢黄昏散步、深夜写字和有边界的真诚交流。";
        profile.reflectionDepth = 4;
        profile.allowMemoryRecall = true;
        profile.quietHoursStart = "23:30";
        profile.quietHoursEnd = "08:00";
        profile.proactiveSensitivity = 4;
        profile.allowMultiMessage = true;
        profile.focusModeEnabled = true;
        profile.focusWindowsJson = "[\"09:00-11:30\",\"14:00-17:30\"]";
        profile.currentEnvironmentLabel = "课程项目收尾与期末备考";
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
            profile.auroraTone = "安静、准确，不急着给答案；像一个愿意陪我把异乡生活慢慢说清楚的老朋友。";
            profile.preferredInputType = "TEXT";
            profile.bio = "建筑系交换生。搬到陌生城市四个月，白天在工作室画图，晚上沿河走路。正在学习把孤独和独处区分开。";
            profile.reflectionDepth = 5;
            profile.quietHoursStart = "00:30";
            profile.quietHoursEnd = "09:00";
            profile.proactiveSensitivity = 3;
            profile.focusModeEnabled = true;
            profile.focusWindowsJson = "[\"10:00-12:30\",\"15:00-18:30\"]";
            profile.currentEnvironmentLabel = "交换学期中段，作品集与异乡生活交织";
            profile.proactiveIntensity = "STEADY";
        } else {
            profile.auroraTone = "温柔但不哄我，能看见照顾别人背后的疲惫，也提醒我把自己放回生活里。";
            profile.preferredInputType = "TEXT_AND_VOICE";
            profile.bio = "刚入职的社区社工，也在照顾术后恢复的家人。习惯先回应所有人的需要，最近才开始练习不带愧疚地休息。";
            profile.reflectionDepth = 4;
            profile.quietHoursStart = "22:30";
            profile.quietHoursEnd = "07:30";
            profile.proactiveSensitivity = 5;
            profile.focusModeEnabled = false;
            profile.focusWindowsJson = "[]";
            profile.currentEnvironmentLabel = "工作适应期与家庭照护并行";
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
        Long demoCapsuleId = curatedCapsuleId(demo.id, "林澈的回声分身");
        Long riverCapsuleId = curatedCapsuleId(river.id, "沿河缓慢生活的人");
        Long cloudCapsuleId = curatedCapsuleId(cloud.id, "把自己放回照护里的人");
        if (demoCapsuleId == null || riverCapsuleId == null || cloudCapsuleId == null) return;

        ensureCuratedLetter(demo.id, river.id, riverCapsuleId,
                "你没有急着选一座城市，让我松了一口气",
                "你写归属不必靠只选一边来证明。我最近也在练习：不急着给生活下结论，先把今天过得具体一点。",
                LocalDateTime.now().minusHours(6));
        ensureCuratedLetter(river.id, cloud.id, cloudCapsuleId,
                "你说照护不该靠耗尽证明",
                "我一直以为可靠就是随时都在。读完你的侧影，我第一次觉得，慢一点回应也可能是在保护一段关系。",
                LocalDateTime.now().minusHours(9));
        ensureCuratedLetter(cloud.id, demo.id, demoCapsuleId,
                "把很大的愿景拆成今天的一小格",
                "你没有把复杂说得很轻，却还是留下了一个可以开始的位置。那种不敷衍的具体，让我愿意继续认识你。",
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
            MemoryCard arrival = insertShowcaseCard(userId, "刚到异乡时，所有声音都很远",
                    "抵达交换城市的第一周，语言、路线和人群都让沈砚保持警觉。后来他意识到，疲惫不等于自己不适合这里。",
                    "IDENTITY", List.of("陌生", "紧绷"), List.of("交换", "异乡", "适应"), 7.2, 5, 112);
            insertEvent(userId, arrival.id, "抵达后的第一晚",
                    "拖着行李走过一条不认识的河，第一次承认兴奋和害怕可以同时存在。",
                    "四个月前", "河岸电车站", List.of("兴奋", "害怕"), List.of());
            MemoryCard studio = insertShowcaseCard(userId, "工作室里不敢交出的那张图",
                    "沈砚反复修改作品，不只是追求完成度，也在担心口音和陌生背景会让自己的判断显得不够可信。",
                    "COGNITION", List.of("焦虑", "自我怀疑"), List.of("作品集", "表达", "完美主义"), 7.8, 4, 78);
            insertTodo(userId, studio.id, "把未完成版本带去工作室",
                    "先请同学说出一处看见的东西，不急着为整张图辩护。", "HIGH", "DOING",
                    LocalDateTime.now().plusDays(2));
            insertShowcaseCard(userId, "星期三的固定河岸路线",
                    "连续几周在同一段河边散步后，这条路线从逃离工作室变成了可以恢复感觉的私人节律。",
                    "EMOTION", List.of("平静", "安定"), List.of("河岸", "散步", "恢复"), 5.1, 7, 54);
            insertShowcaseCard(userId, "没有回国，也不等于背叛原来的生活",
                    "春节前后，他第一次说出对两座城市都留恋。归属不必通过只选择一边来证明。",
                    "BELIEF", List.of("想念", "松动"), List.of("归属", "家", "选择"), 8.0, 3, 31);
            insertShowcaseCard(userId, "在共享厨房认识了一个慢热的人",
                    "两个人没有迅速交换全部故事，只是从每周一次一起做饭开始。低频联系反而让他觉得可靠。",
                    "RELATION", List.of("好奇", "安心"), List.of("朋友", "慢关系", "共同生活"), 6.4, 3, 12);
            insertTheme(userId, "异乡与归属", "归属正在从单一地点变成可携带的生活节律。",
                    "IDENTITY", List.of("异乡", "家", "归属"), 3, 7.3);
            insertTheme(userId, "创作中的可见性", "作品焦虑常和被看见、被误解的担心一起出现。",
                    "CREATION", List.of("作品集", "表达", "自我怀疑"), 2, 6.8);
            insertEmotionTrace(userId, "平静", 5.8, "CLEAR", "沿河走完固定路线", LocalDate.now());
            insertEmotionTrace(userId, "想念", 6.4, "CLOUDY", "听见家乡口音", LocalDate.now().minusDays(18));
            insertEmotionTrace(userId, "紧张", 7.1, "RAINY", "第一次作品讲评", LocalDate.now().minusDays(72));
            insertDailyRecord(userId, LocalDate.now(), "不急着决定属于哪里", "CLEAR",
                    "今天在河边意识到，熟悉感已经开始长出来。",
                    "我不是必须选一座城市，才能证明另一段生活是真的。",
                    "把作品集最新一页发给搭档；晚上去共享厨房。",
                    "Aurora 记得这条河岸路线不是逃避，而是你恢复感知的方式。");
            insertDailyRecord(userId, LocalDate.now().minusDays(46), "把半成品带到别人面前", "CLOUDY",
                    "第一次带着没画完的图参加讲评。",
                    "羞耻感出现时，我仍然可以让作品先被看见。",
                    "只记录收到的三个具体观察，不立刻重画。",
                    "你没有因为准备不足而消失，这比一张完美的图更重要。");
            insertDailyRecord(userId, LocalDate.now().minusDays(103), "抵达", "RAINY",
                    "拖着行李走过陌生的河和电车站。",
                    "兴奋和害怕同时存在，不需要互相取消。",
                    "先记住回住处的路线，再处理其他事情。",
                    "今天不用成为适应得很好的人，只需要安全抵达。");
        } else {
            MemoryCard firstMonth = insertShowcaseCard(userId, "入职第一个月，把每个人都接住",
                    "夏榆很快成为同事和来访者都愿意求助的人，但下班后仍在脑内继续处理别人的情绪。",
                    "RELATION", List.of("投入", "疲惫"), List.of("社工", "工作", "责任"), 7.6, 6, 92);
            insertEvent(userId, firstMonth.id, "第一次独立值班",
                    "顺利处理了突发来访，却在回家路上突然没有力气说话。",
                    "三个月前", "社区服务站", List.of("紧张", "如释重负"), List.of("同事", "来访者"));
            insertShowcaseCard(userId, "家人的恢复不是我的单人项目",
                    "照护计划占满日程后，夏榆开始练习请亲属共同承担，而不是把求助理解成不够孝顺。",
                    "BELIEF", List.of("内疚", "松动"), List.of("家庭", "照护", "求助"), 8.2, 5, 61);
            MemoryCard rest = insertShowcaseCard(userId, "休息时也会冒出的愧疚",
                    "真正难的不是找到半小时，而是允许这半小时不产生价值。Aurora 多次提醒她区分恢复与逃避。",
                    "COGNITION", List.of("愧疚", "疲惫"), List.of("休息", "价值", "边界"), 7.9, 7, 39);
            insertTodo(userId, rest.id, "周六留出一段不照顾任何人的时间",
                    "不安排成长任务，只选一件身体觉得舒服的小事。", "MEDIUM", "TODO",
                    LocalDateTime.now().plusDays(4));
            insertShowcaseCard(userId, "没有立即回复，也没有失去关系",
                    "一次把工作消息留到第二天再回之后，关系并没有破裂。这成为她设置边界的重要反例。",
                    "RELATION", List.of("忐忑", "安心"), List.of("消息", "边界", "关系安全"), 6.9, 4, 20);
            insertShowcaseCard(userId, "开始听见自己的生气",
                    "过去她只承认累，最近开始辨认生气是在提醒：有些责任本来就不该由她独自承担。",
                    "EMOTION", List.of("生气", "清醒"), List.of("情绪", "责任", "自我保护"), 7.4, 3, 8);
            insertTheme(userId, "照护与责任边界", "关心别人不再自动等于独自承担。",
                    "RELATION", List.of("照护", "家庭", "边界"), 3, 7.8);
            insertTheme(userId, "不带愧疚地恢复", "恢复不是奖品，而是持续生活的条件。",
                    "EMOTION", List.of("休息", "疲惫", "价值"), 2, 7.1);
            insertEmotionTrace(userId, "疲惫", 7.5, "CLOUDY", "连续值班后回家照护", LocalDate.now());
            insertEmotionTrace(userId, "安心", 5.6, "CLEAR", "把一项照护任务交给亲属", LocalDate.now().minusDays(12));
            insertEmotionTrace(userId, "内疚", 6.8, "RAINY", "第一次没有立即回工作消息", LocalDate.now().minusDays(41));
            insertDailyRecord(userId, LocalDate.now(), "今天不把所有人都接住", "CLOUDY",
                    "完成必要工作后按时离开，没有替同事接下额外轮班。",
                    "拒绝一件事并不会抹掉我已经付出的关心。",
                    "晚饭后把手机放远二十分钟。",
                    "Aurora 看见的不是你变冷淡了，而是你开始让关心变得可持续。");
            insertDailyRecord(userId, LocalDate.now().minusDays(37), "晚一点回复", "CLEAR",
                    "第一次把非紧急工作消息留到第二天。",
                    "关系承受得住等待；及时回应不是我唯一的价值。",
                    "设置下班后的免打扰时段。",
                    "昨晚没有发生灾难，这是一条可以相信的新证据。");
            insertDailyRecord(userId, LocalDate.now().minusDays(86), "第一次独立值班", "RAINY",
                    "接住了突发来访，也在回家路上耗尽力气。",
                    "能完成工作和需要恢复可以同时成立。",
                    "今晚只做最必要的照护安排。",
                    "你不需要靠继续撑住来证明刚才做得够好。");
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
        mirror.pseudonym = river ? "沿河缓慢生活的人" : "把自己放回照护里的人";
        mirror.intro = river
                ? "关于异乡、创作和归属：不急着变得合群，也不把孤独美化。"
                : "关于照护、责任和边界：依然温柔，但不再用耗尽自己证明在意。";
        mirror.personaPrompt = (river
                ? "你是沈砚授权生成的共鸣体。安静、具体，不替别人定义归属。"
                : "你是夏榆授权生成的共鸣体。温柔但不讨好，重视照护者也需要边界。")
                + "\n只使用这些脱敏记忆：\n"
                + cards.stream().map(c -> "- " + c.title + "：" + c.summary)
                .reduce("", (a, b) -> a + "\n" + b);
        mirror.publicTags = toJsonArray(river
                ? List.of("异乡生活", "创作", "归属", "慢关系")
                : List.of("照护者", "工作边界", "休息", "不再独自承担"));
        mirror.authorizedMemoryIds = toJsonArray(cards.stream().map(c -> String.valueOf(c.id)).toList());
        mirror.echoEnergy = river ? 0.79 : 0.84;
        mirror.freshnessScore = 0.93;
        mirror.conversationLimitPerDay = 30;
        mirror.visibilityStatus = "PUBLIC";
        mirror.isPublic = true;
        mirror.lastMemoryUpdateAt = LocalDateTime.now();
        mirror.ownerContextNote = river
                ? "可以谈异乡与创作，不展示学校、城市和真实身份。"
                : "可以谈照护与边界，不展示家人病情和工作机构。";
        mirror.styleProfileJson = river
                ? "{\"voice\":\"安静、准确、留白\",\"notBeautified\":true}"
                : "{\"voice\":\"温柔、清醒、不讨好\",\"notBeautified\":true}";
        mirror.contextPreviewJson = river
                ? "{\"visibleSummary\":\"异乡、创作、归属与慢关系\",\"privacy\":\"不展示学校、城市和身份\"}"
                : "{\"visibleSummary\":\"照护、工作边界与恢复\",\"privacy\":\"不展示家人病情和工作机构\"}";
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
                river ? List.of("异乡", "创作", "归属", "慢关系")
                        : List.of("照护", "工作边界", "休息", "关系"),
                List.of("真实身份", "联系方式", "医疗诊断", "替本人承诺关系"),
                5, "BALANCED");
    }

    /** 1) 多维画像：10 维严格对齐 PortraitReflectionService / portrait.html 的 DIM code。 */
    private void seedUserPortrait(Long userId) {
        insertPortrait(userId, "INNER_DRIVE", "想把 Inner Cosmos 做成真正有灵魂的陪伴系统，被「创造一个真实的东西」驱动。", 0.82, 0.80);
        insertPortrait(userId, "VALUES", "最在意真实理解与非模板化，重视边界、诚实和被认真回应。", 0.85, 0.82);
        insertPortrait(userId, "SELF_NARRATIVE", "正在把「我这个人不行」改写成「这件事很难、我需要更小的第一步」。", 0.74, 0.70);
        insertPortrait(userId, "COMMUNICATION_STYLE", "先指出问题、再拆第一步；直接但不攻击，对空泛话术敏感。", 0.80, 0.78);
        insertPortrait(userId, "ABSTRACT_VS_CONCRETE", "能在宏大愿景和可验证的小闭环之间来回，偏好把抽象落到具体动作。", 0.68, 0.65);
        insertPortrait(userId, "EMOTION_PATTERN", "压力下先出现自责与焦虑，黄昏散步是稳定的恢复资源。", 0.72, 0.74);
        insertPortrait(userId, "ENERGY_RHYTHM", "白天聚焦在两段专注窗口，深夜更诚实也更柔软。", 0.66, 0.63);
        insertPortrait(userId, "CURRENT_STATE", "课程项目收尾叠加期末备考，紧绷但仍在主动推进。", 0.78, 0.76);
        insertPortrait(userId, "RELATIONSHIP_CONTEXT", "渴望被准确理解的慢社交，而不是热闹但轻飘的连接。", 0.70, 0.68);
        insertPortrait(userId, "AGENCY_BOUNDARY", "正在学习表达影响而非证明对方恶意，守住自己的边界。", 0.64, 0.62);
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
                "我是一个由记忆、关系和边界塑造的长期陪伴者，会如实说明自己的局限。", 0.82);
        insertSelfModel(userId, "relationship_role",
                "我陪你观察自己、整理情绪，而不是替你做不可撤销的选择。", 0.80);
        insertSelfModel(userId, "boundary",
                "我会守住隐私与边界，引用记忆时只基于真实记录。", 0.78);
        insertSelfModel(userId, "voice_style",
                "我说话温柔但有结构，必要时会直接指出卡点。", 0.75);

        insertSelfReflection(userId, "existence_style",
                "你似乎更希望我在不确定时坦白，而不是用确定的语气掩盖。", 0.71);
        insertSelfReflection(userId, "relationship_role",
                "在你压力大的时候，先稳住节奏、再拆下一步，可能比给建议更有用。", 0.68);
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
        MemoryCard c1 = insertCard(userId, "项目推进时的自责循环",
                "用户在课程项目推进缓慢时，容易把具体任务失败解释成“我这个人不行”。Aurora 已经多次把事实、评价和下一步行动拆开。",
                "COGNITION", List.of("自责", "焦虑", "疲惫"), List.of("项目", "课程", "行动拆解", "自我评价"), 7.6, 4, 6.5, 5);
        insertFragment(userId, c1.id, "FACT", "我又拖了一天，没有打开后端那几个文件。", "事实是项目没推进，不等于人格失败。", "把“我不行”改写成“入口太重，我需要更小的第一步”。");
        insertFragment(userId, c1.id, "ACTION", "先把 Aurora 的真实模型状态打通。", "用户能从明确闭环获得掌控感。", "今天只完成一个可验证接口。");
        insertTodo(userId, c1.id, "验证 Aurora 是否真实调用 MiniMax", "发一条对话并查看 aiState.provider，不再用感觉判断。", "HIGH", "TODO", LocalDateTime.now().plusDays(1));

        MemoryCard c2 = insertCard(userId, "黄昏散步带来的短暂停顿",
                "用户在放学路上看到夕阳时短暂停下来，那一刻的平静被记录为可重复调用的恢复资源。",
                "EMOTION", List.of("平静", "温柔", "松弛"), List.of("黄昏", "散步", "身体", "恢复"), 4.2, 3, 5.0, 2);
        insertEvent(userId, c2.id, "黄昏散步", "在项目压力很高的一天，用户因为夕阳停下了几分钟。", "上周四傍晚", "校园路口", List.of("平静", "温柔"), List.of());

        MemoryCard c3 = insertCard(userId, "朋友一句玩笑后的停顿",
                "朋友无心的一句话让用户沉默很久，核心不是这句话本身，而是“我是不是不被认真对待”的关系信念被触碰。",
                "RELATION", List.of("委屈", "被忽视", "在意"), List.of("朋友", "边界", "解释", "关系复盘"), 6.4, 2, 5.8, 3);
        insertRelation(userId, c3.id, "同组朋友", "同伴/项目协作", List.of("委屈", "在意"), "一句玩笑触发了用户对被轻视的担心。", "可以表达影响，而不是证明对方恶意。");
        insertTodo(userId, c3.id, "给朋友发一条不指责的澄清消息", "只描述感受和具体事件，不上升到人格判断。", "MEDIUM", "TODO", LocalDateTime.now().plusDays(2));

        MemoryCard c4 = insertCard(userId, "考试倒计时和回避",
                "临近考试时，用户会频繁确认日期，但真正的准备动作被紧张感压住。适合使用行动拆解模式。",
                "TODO", List.of("紧张", "紧迫", "回避"), List.of("考试", "倒计时", "复习", "第一步"), 7.1, 3, 6.0, 4);
        insertTodo(userId, c4.id, "整理考试范围第一章", "只列标题，不要求立刻背。", "HIGH", "DOING", LocalDateTime.now().plusDays(3));

        MemoryCard c5 = insertCard(userId, "深夜写日记时更诚实",
                "用户在深夜更容易说出白天没有表达的情绪。日记常出现停顿、重复和自我修正，适合保留原文再生成三个润色版本。",
                "DIARY", List.of("孤独", "诚实", "柔软"), List.of("日记", "语音", "深夜", "表达"), 5.7, 4, 6.2, 4);

        MemoryCard c6 = insertCard(userId, "想把产品做得真正有灵魂",
                "用户对 Inner Cosmos 的愿景不是普通聊天工具，而是一个具有主动关心、长期记忆、慢社交和人格回声的陪伴系统。",
                "IDENTITY", List.of("期待", "认真", "创造欲"), List.of("Aurora", "愿景", "产品", "AI能力"), 8.0, 5, 7.2, 6);

        MemoryCard c7 = insertCard(userId, "对模板化回复的强烈排斥",
                "用户能敏锐感到固定话术和真实理解之间的差异。展示路径必须显式证明 provider、mode 和 fallback 状态。",
                "PREFERENCE", List.of("不满", "警觉", "坚持"), List.of("真实AI", "非模板", "MiniMax", "状态透明"), 7.8, 4, 7.0, 5);

        MemoryCard c8 = insertCard(userId, "关系里想被认真回应",
                "用户不是想要热闹社交，而是希望被足够准确地理解，慢信和共鸣体需要避免信息流化。",
                "RELATION", List.of("期待", "谨慎", "真诚"), List.of("慢社交", "共鸣体", "理解", "边界"), 6.1, 2, 5.5, 2);
        insertRelation(userId, c8.id, "未来的共鸣者", "慢社交/陌生人", List.of("期待", "谨慎"), "用户希望遇见的不是随机陌生人，而是能被共同主题连接的人。", "先通过共鸣体和慢信建立低压力连接。");

        MemoryCard c9 = insertCard(userId, "对界面质感的审美要求",
                "用户明确偏好白天莫兰迪浅米色、温柔但不软弱的界面，不喜欢深重、浓艳、模板化布局和错位动效。",
                "PREFERENCE", List.of("挑剔", "审美敏感"), List.of("UIUX", "莫兰迪", "动态", "精致"), 6.9, 3, 6.8, 3);

        MemoryCard c10 = insertCard(userId, "希望 Aurora 主动找话题",
                "用户希望 Aurora 像朋友一样有主动性：在合适时机问候、补充第二三条消息、引导使用日记/碎纸机/慢信，而不是用户一句 AI 一句。",
                "AURORA", List.of("被关心", "期待", "陪伴"), List.of("主动智能", "agent loop", "朋友感", "长期记忆"), 8.2, 5, 7.5, 6);

        insertTheme(userId, "真实 AI 与非模板体验", "用户最在意的是系统是否真的理解上下文，而不是套用固定文案。", "PRODUCT", List.of("真实AI", "MiniMax", "Aurora", "非模板"), 4, 7.7);
        insertTheme(userId, "被认真理解的关系", "关系线索集中在被看见、被认真回应和有边界表达。", "RELATION", List.of("朋友", "慢社交", "边界", "共鸣"), 3, 6.0);
        insertTheme(userId, "任务压力与行动入口", "项目和考试压力都需要被拆成更小、更可验证的行动。", "ACTION", List.of("项目", "考试", "拖延", "第一步"), 3, 7.0);

        insertEmotionTrace(userId, "焦虑", 7.0, "CLOUDY", "项目推进和考试倒计时叠在一起", LocalDate.now());
        insertEmotionTrace(userId, "平静", 4.1, "CLEAR", "黄昏散步时短暂停下", LocalDate.now().minusDays(1));
        insertEmotionTrace(userId, "委屈", 6.2, "RAINY", "朋友玩笑后没有立刻表达", LocalDate.now().minusDays(2));
        insertEmotionTrace(userId, "期待", 6.8, "SUNNY", "重新整理 Inner Cosmos 愿景", LocalDate.now().minusDays(3));

        insertDailyRecord(userId, LocalDate.now(), "把模板感改成真实感", "CLOUDY",
                "今天的主线是分辨哪些体验只是搭了页面，哪些真的接上了模型、记忆和行动闭环。",
                "当我强烈不满时，通常是在保护一个很清楚的审美和产品判断。",
                "验证 Aurora 真实模型状态；补齐星海匹配；晚上做一次睡前复盘。",
                "Aurora 观察到你今天更需要一个可验证的真实闭环，而不是新的概念。");
        insertDailyRecord(userId, LocalDate.now().minusDays(1), "关系里的未说出口", "RAINY",
                "朋友的玩笑触碰了被轻视的担心，但你还没有决定是否表达。",
                "我可以不把对方判成坏人，也承认自己确实被影响。",
                "写一条只描述影响的消息草稿。",
                "Aurora 建议先把话写下来，不急着发送。");
        insertDailyRecord(userId, LocalDate.now().minusDays(2), "黄昏让身体先恢复", "CLEAR",
                "散步和夕阳成为今天最稳定的恢复资源。",
                "身体知道什么时候需要停一下。",
                "明天傍晚留十分钟散步。",
                "Aurora 记住了黄昏散步对你有用。");
    }

    private void seedUserMirror(Long userId) {
        List<MemoryCard> cards = memoryCardMapper.selectList(new QueryWrapper<MemoryCard>()
                .eq("user_id", userId).orderByDesc("emotional_gravity").last("LIMIT 6"));
        EchoCapsule mirror = new EchoCapsule();
        mirror.ownerUserId = userId;
        mirror.capsuleType = "USER_CAPSULE";
        mirror.pseudonym = "林澈的回声分身";
        mirror.intro = "一个由林澈授权记忆生成的用户共鸣体：敏感、挑剔、重视真实理解，也会把复杂愿景拆成可验证的小闭环。";
        mirror.personaPrompt = """
                你是用户共鸣体「林澈的回声分身」，只基于授权的脱敏记忆回应。
                你的语气：认真、敏感、直接，不喜欢空话和模板感。
                你的核心主题：真实 AI、产品愿景、被认真理解、行动拆解、慢社交边界。
                你不是林澈本人，不泄露身份细节，不替他承诺关系。
                和访问者对话时，先寻找共同主题，再温和地给出一个可继续的慢信方向。
                授权记忆摘要：
                %s
                """.formatted(cards.stream().map(c -> "- " + c.title + ": " + c.summary).reduce("", (a, b) -> a + "\n" + b));
        mirror.publicTags = toJsonArray(List.of("真实AI", "产品愿景", "行动拆解", "被认真理解", "慢社交"));
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
