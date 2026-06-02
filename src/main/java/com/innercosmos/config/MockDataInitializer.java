package com.innercosmos.config;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.common.Constants;
import com.innercosmos.dto.RegisterRequest;
import com.innercosmos.entity.DailyRecord;
import com.innercosmos.entity.EchoCapsule;
import com.innercosmos.entity.EmotionTrace;
import com.innercosmos.entity.MemoryCard;
import com.innercosmos.entity.SlowLetter;
import com.innercosmos.entity.ThoughtFragment;
import com.innercosmos.entity.TodoItem;
import com.innercosmos.entity.User;
import com.innercosmos.mapper.DailyRecordMapper;
import com.innercosmos.mapper.EchoCapsuleMapper;
import com.innercosmos.mapper.EmotionTraceMapper;
import com.innercosmos.mapper.MemoryCardMapper;
import com.innercosmos.mapper.SlowLetterMapper;
import com.innercosmos.mapper.ThoughtFragmentMapper;
import com.innercosmos.mapper.TodoItemMapper;
import com.innercosmos.mapper.UserMapper;
import com.innercosmos.service.GravityService;
import com.innercosmos.service.UserService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class MockDataInitializer implements CommandLineRunner {
    private final UserMapper userMapper;
    private final EchoCapsuleMapper capsuleMapper;
    private final MemoryCardMapper memoryCardMapper;
    private final TodoItemMapper todoItemMapper;
    private final SlowLetterMapper slowLetterMapper;
    private final DailyRecordMapper dailyRecordMapper;
    private final EmotionTraceMapper emotionTraceMapper;
    private final ThoughtFragmentMapper thoughtFragmentMapper;
    private final GravityService gravityService;
    private final UserService userService;

    public MockDataInitializer(UserMapper userMapper,
                               EchoCapsuleMapper capsuleMapper,
                               MemoryCardMapper memoryCardMapper,
                               TodoItemMapper todoItemMapper,
                               SlowLetterMapper slowLetterMapper,
                               DailyRecordMapper dailyRecordMapper,
                               EmotionTraceMapper emotionTraceMapper,
                               ThoughtFragmentMapper thoughtFragmentMapper,
                               GravityService gravityService,
                               UserService userService) {
        this.userMapper = userMapper;
        this.capsuleMapper = capsuleMapper;
        this.memoryCardMapper = memoryCardMapper;
        this.todoItemMapper = todoItemMapper;
        this.slowLetterMapper = slowLetterMapper;
        this.dailyRecordMapper = dailyRecordMapper;
        this.emotionTraceMapper = emotionTraceMapper;
        this.thoughtFragmentMapper = thoughtFragmentMapper;
        this.gravityService = gravityService;
        this.userService = userService;
    }

    @Override
    public void run(String... args) {
        ensureUser("admin", "admin123", "管理员", Constants.ROLE_ADMIN);
        ensureUser("demo", "demo123", "Demo 用户", Constants.ROLE_USER);
        ensureSeedCapsules();
        ensureDemoAssets();
    }

    private void ensureUser(String username, String password, String nickname, String role) {
        QueryWrapper<User> query = new QueryWrapper<>();
        query.eq("username", username);
        if (userMapper.selectCount(query) == 0) {
            RegisterRequest request = new RegisterRequest();
            request.username = username;
            request.password = password;
            request.nickname = nickname;
            User user = userService.register(request);
            user.role = role;
            userMapper.updateById(user);
        }
    }

    private void ensureSeedCapsules() {
        QueryWrapper<EchoCapsule> query = new QueryWrapper<>();
        query.eq("capsule_type", "SEED_CAPSULE");
        if (capsuleMapper.selectCount(query) >= 8) {
            return;
        }
        for (SeedCapsuleContent.SeedCapsule sc : SeedCapsuleContent.seeds()) {
            seed(sc);
        }
    }

    private void seed(SeedCapsuleContent.SeedCapsule sc) {
        EchoCapsule capsule = new EchoCapsule();
        capsule.capsuleType = "SEED_CAPSULE";
        capsule.pseudonym = sc.name;
        capsule.intro = sc.intro;
        capsule.personaPrompt = "你是" + sc.name + "." + sc.tagline + " 只能作为哲学视角模拟体回应." +
                " 你的座右铭:" + sc.mockReplies.get(0);
        capsule.publicTags = toJsonArray(sc.tags);
        capsule.authorizedMemoryIds = "[]";
        capsule.echoEnergy = 0.9;
        capsule.freshnessScore = 1.0;
        capsule.conversationLimitPerDay = 5;
        capsule.visibilityStatus = "PUBLIC";
        capsule.isPublic = true;
        capsuleMapper.insert(capsule);
    }

    private String toJsonArray(java.util.List<String> items) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(items.get(i)).append("\"");
        }
        sb.append("]");
        return sb.toString();
    }

    private void ensureDemoAssets() {
        User demo = userMapper.selectOne(new QueryWrapper<User>().eq("username", "demo"));
        if (demo == null) {
            return;
        }
        if (memoryCardMapper.selectCount(new QueryWrapper<MemoryCard>().eq("user_id", demo.id)) > 0) {
            return;
        }

        MemoryCard card1 = insertCard(demo.id, "一种没有推进的自责感",
                "你反复提到任务没有推进.真正沉重的也许不是任务本身,而是你很快把事件解释成了'我又不行'.",
                "COGNITION", "[\"自责\",\"疲惫\"]", "[\"任务\",\"自我评价\",\"行动\"]",
                6.8, 2, 5.0, 2);

        insertFragment(demo.id, card1.id, "FACT", "作业又拖了一天,我好像一直在回避打开项目.",
                "从用户表达中抽取的事实片段.", "先区分事实和解释.");
        insertFragment(demo.id, card1.id, "FEELING", "疲惫和压力",
                "表达里出现的主要感受线索.", "允许感受存在,不急着证明它合理.");
        insertFragment(demo.id, card1.id, "BELIEF", "如果一件事没做好,就说明我这个人不行.",
                "可能影响用户自我评价的信念.", "把事件和自我价值暂时分开看.");
        insertFragment(demo.id, card1.id, "ACTION", "明天先打开项目文件,只做十分钟.",
                "可以轻轻推进的一步.", "把下一步压缩到十分钟内能开始.");

        insertTodo(demo.id, card1.id, "打开 Java 项目并完成一个小提交",
                "不是证明自己,只是把第一步拿回来.", "MEDIUM", "TODO");

        MemoryCard card2 = insertCard(demo.id, "朋友的那句话一直在回响",
                "你提到朋友说了一句无心的话,但你停了很久.也许不是那句话本身重,而是它触碰了一个还没处理好的角落.",
                "RELATION", "[\"孤独\",\"被忽视\"]", "[\"朋友\",\"关系\",\"理解\"]",
                5.5, 1, 4.0, 1);

        MemoryCard card3 = insertCard(demo.id, "放学路上的夕阳让我停下来",
                "你描述了放学路上看到夕阳的瞬间.在那一小段时间里,你的身体自己选择了停下来.那种短暂的平静是真实的.",
                "EMOTION", "[\"平静\",\"温柔\"]", "[\"夕阳\",\"散步\",\"片刻\"]",
                3.2, 1, 3.5, 1);

        MemoryCard card4 = insertCard(demo.id, "考试倒计时在逼近",
                "你反复确认考试日期,身体紧绷但行动上在回避.紧张感在堆积,但还没有找到出口.",
                "TODO", "[\"焦虑\",\"紧迫\"]", "[\"考试\",\"倒计时\",\"准备\"]",
                7.2, 3, 6.0, 3);

        insertTodo(demo.id, card4.id, "整理考试范围,只写第一个知识点",
                "不需要一次搞定,先把开头打开.", "HIGH", "TODO");

        insertEmotionTrace(demo.id, card1.sourceSessionId, "疲惫", 6.8, "CLOUDY",
                "作业又拖了一天,我好像一直在回避打开项目.");

        insertDailyRecord(demo.id, java.time.LocalDate.now(),
                "在自责和休息之间反复", "CLOUDY",
                "你今天主要在和自己较劲.任务没有推进的事实被放大成了自我评价,但夕阳让你短暂地停了下来.",
                "允许自己停下来不是放弃,是在积攒继续的力量.");

        insertDailyRecord(demo.id, java.time.LocalDate.now().minusDays(1),
                "朋友的话触发了什么", "RAINY",
                "你被朋友的一句话触动,那个感受还没有被完整地命名.今天的对话帮助你看清了一部分.",
                "被触动不是脆弱,说明你在认真地对待每一段关系.");

        SlowLetter sent = new SlowLetter();
        sent.senderUserId = demo.id;
        sent.receiverUserId = 1L;
        sent.receiverCapsuleId = 1L;
        sent.title = "我在那句话里停了一下";
        sent.letterBody = "我不是急着认识谁,只是想认真告诉你:那种把自己从自责里慢慢捞出来的感觉,我好像也懂.";
        sent.status = "SENT";
        sent.parallaxDistance = 3;
        sent.estimatedArrivalAt = java.time.LocalDateTime.now().plusMinutes(3);
        slowLetterMapper.insert(sent);

        SlowLetter received = new SlowLetter();
        received.senderUserId = 1L;
        received.receiverUserId = demo.id;
        received.receiverCapsuleId = 2L;
        received.title = "你的夕阳描述让我也想停下来";
        received.letterBody = "我读到了你关于夕阳的文字.谢谢你描述了那个瞬间,让我也想起了一些安静的时刻.";
        received.status = "DELIVERED";
        received.deliveredAt = java.time.LocalDateTime.now().minusHours(2);
        received.parallaxDistance = 0;
        slowLetterMapper.insert(received);
    }

    private MemoryCard insertCard(Long userId, String title, String summary, String type,
                                  String emotionTags, String keywordTags,
                                  double intensity, int recurrence, double importance, int triggers) {
        MemoryCard card = new MemoryCard();
        card.userId = userId;
        card.title = title;
        card.summary = summary;
        card.memoryType = type;
        card.emotionTags = emotionTags;
        card.keywordTags = keywordTags;
        card.peopleTags = "[]";
        card.intensityScore = intensity;
        card.recurrenceCount = recurrence;
        card.userImportance = importance;
        card.triggerCount = triggers;
        card.emotionalGravity = gravityService.calculateGravity(intensity, recurrence, importance, triggers, 0);
        card.visibilityLevel = type.equals("EMOTION") ? "PRIVATE" : "CANDIDATE";
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

    private void insertTodo(Long userId, Long cardId, String name, String desc, String priority, String status) {
        TodoItem todo = new TodoItem();
        todo.userId = userId;
        todo.sourceMemoryCardId = cardId;
        todo.taskName = name;
        todo.description = desc;
        todo.priority = priority;
        todo.status = status;
        todoItemMapper.insert(todo);
    }

    private void insertEmotionTrace(Long userId, Long sessionId, String emotion, double score, String weather, String scene) {
        EmotionTrace trace = new EmotionTrace();
        trace.userId = userId;
        trace.sourceSessionId = sessionId;
        trace.emotionName = emotion;
        trace.emotionScore = score;
        trace.weatherType = weather;
        trace.triggerScene = scene;
        trace.recordDate = java.time.LocalDate.now();
        emotionTraceMapper.insert(trace);
    }

    private void insertDailyRecord(Long userId, java.time.LocalDate date, String theme, String weather,
                                   String summary, String cognitive) {
        DailyRecord record = new DailyRecord();
        record.userId = userId;
        record.recordDate = date;
        record.theme = theme;
        record.emotionWeather = weather;
        record.auroraSummary = summary;
        record.cognitiveSummary = cognitive;
        record.status = "ACTIVE";
        record.userAccepted = true;
        dailyRecordMapper.insert(record);
    }
}
