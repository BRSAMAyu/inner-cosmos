package com.innercosmos.config;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.common.Constants;
import com.innercosmos.dto.RegisterRequest;
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
import com.innercosmos.service.UserService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Component
public class MockDataInitializer implements CommandLineRunner {
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
                               UserService userService) {
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
    }

    @Override
    public void run(String... args) {
        ensureUser("admin", "admin123", "管理员", Constants.ROLE_ADMIN);
        User demo = ensureUser("demo", "demo123", "林澈", Constants.ROLE_USER);
        User river = ensureUser("river", "demo123", "河岸来信", Constants.ROLE_USER);
        User cloud = ensureUser("cloud", "demo123", "云杉", Constants.ROLE_USER);

        ensureSeedCapsules();
        ensureDemoProfile(demo.id);
        ensureDemoAssets(demo, river, cloud);
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
        userMapper.updateById(user);
        return user;
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
        if (profile.id == null) {
            userProfileMapper.insert(profile);
        } else {
            userProfileMapper.updateById(profile);
        }
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
            existing.conversationLimitPerDay = 6;
            existing.visibilityStatus = "PUBLIC";
            existing.isPublic = true;
            existing.lastMemoryUpdateAt = LocalDateTime.now();
            if (existing.id == null) {
                capsuleMapper.insert(existing);
            } else {
                capsuleMapper.updateById(existing);
            }
            ensureBoundary(existing.id, sc.chatTopics(), sc.blockedTopics(), 6, "OPEN");
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
        mirror.conversationLimitPerDay = 5;
        mirror.visibilityStatus = "PUBLIC";
        mirror.isPublic = true;
        mirror.lastMemoryUpdateAt = LocalDateTime.now();
        mirror.ownerContextNote = "我希望别人看到真实的我：认真、敏感、讨厌模板化，也会在项目压力下自责。不要把我包装成永远积极的人。";
        mirror.styleProfileJson = "{\"voice\":\"认真、直接、对空泛话术敏感，喜欢可验证的小闭环\",\"notBeautified\":true,\"habits\":[\"先指出问题\",\"再拆第一步\",\"重视真实理解\"]}";
        mirror.contextPreviewJson = "{\"visibleSummary\":\"授权展示真实AI、项目压力、关系边界、行动拆解和慢社交偏好\",\"privacy\":\"不展示原始对话全文、联系方式、真实身份\",\"publicTags\":[\"真实AI\",\"产品愿景\",\"行动拆解\",\"被认真理解\",\"慢社交\"]}";
        mirror.standInEnabled = true;
        mirror.realContactPolicy = "LETTER_ONLY";
        capsuleMapper.insert(mirror);
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
