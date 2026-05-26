package com.innercosmos.config;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.common.Constants;
import com.innercosmos.dto.RegisterRequest;
import com.innercosmos.entity.EchoCapsule;
import com.innercosmos.entity.MemoryCard;
import com.innercosmos.entity.SlowLetter;
import com.innercosmos.entity.TodoItem;
import com.innercosmos.entity.User;
import com.innercosmos.mapper.EchoCapsuleMapper;
import com.innercosmos.mapper.MemoryCardMapper;
import com.innercosmos.mapper.SlowLetterMapper;
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
    private final GravityService gravityService;
    private final UserService userService;

    public MockDataInitializer(UserMapper userMapper,
                               EchoCapsuleMapper capsuleMapper,
                               MemoryCardMapper memoryCardMapper,
                               TodoItemMapper todoItemMapper,
                               SlowLetterMapper slowLetterMapper,
                               GravityService gravityService,
                               UserService userService) {
        this.userMapper = userMapper;
        this.capsuleMapper = capsuleMapper;
        this.memoryCardMapper = memoryCardMapper;
        this.todoItemMapper = todoItemMapper;
        this.slowLetterMapper = slowLetterMapper;
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
        if (capsuleMapper.selectCount(query) >= 5) {
            return;
        }
        seed("斯多葛信使", "关注可控与不可控，帮助用户把注意力放回可行动之处。", "[\"哲学\",\"克制\"]");
        seed("苏格拉底之问", "通过追问帮助用户澄清信念，而不是替用户下结论。", "[\"追问\",\"信念\"]");
        seed("庄周之梦", "提供松弛、相对化与逍遥视角。", "[\"文学\",\"松弛\"]");
        seed("存在主义旅人", "关注自由、选择与意义创造。", "[\"意义\",\"选择\"]");
        seed("热烈的画家", "关注敏感、痛苦与艺术表达。", "[\"艺术\",\"表达\"]");
    }

    private void seed(String name, String intro, String tags) {
        EchoCapsule capsule = new EchoCapsule();
        capsule.capsuleType = "SEED_CAPSULE";
        capsule.pseudonym = name;
        capsule.intro = intro + " 基于公开思想与文学气质构建的哲学视角模拟体。";
        capsule.personaPrompt = "你是" + name + "，只能作为哲学视角模拟体回应。";
        capsule.publicTags = tags;
        capsule.authorizedMemoryIds = "[]";
        capsule.echoEnergy = 0.9;
        capsule.freshnessScore = 1.0;
        capsule.conversationLimitPerDay = 5;
        capsule.visibilityStatus = "PUBLIC";
        capsule.isPublic = true;
        capsuleMapper.insert(capsule);
    }

    private void ensureDemoAssets() {
        User demo = userMapper.selectOne(new QueryWrapper<User>().eq("username", "demo"));
        if (demo == null) {
            return;
        }
        if (memoryCardMapper.selectCount(new QueryWrapper<MemoryCard>().eq("user_id", demo.id)) == 0) {
            MemoryCard card = new MemoryCard();
            card.userId = demo.id;
            card.title = "一种没有推进的自责感";
            card.summary = "你反复提到任务没有推进。真正沉重的也许不是任务本身，而是你很快把事件解释成了“我又不行”。";
            card.memoryType = "COGNITION";
            card.emotionTags = "[\"自责\",\"疲惫\"]";
            card.keywordTags = "[\"任务\",\"自我评价\",\"行动\"]";
            card.peopleTags = "[]";
            card.intensityScore = 6.8;
            card.recurrenceCount = 2;
            card.userImportance = 5.0;
            card.triggerCount = 2;
            card.emotionalGravity = gravityService.calculateGravity(6.8, 2, 5.0, 2, 0);
            card.visibilityLevel = "CANDIDATE";
            card.status = "ACTIVE";
            memoryCardMapper.insert(card);

            TodoItem todo = new TodoItem();
            todo.userId = demo.id;
            todo.sourceMemoryCardId = card.id;
            todo.taskName = "打开 Java 项目并完成一个小提交";
            todo.description = "不是证明自己，只是把第一步拿回来。";
            todo.priority = "MEDIUM";
            todo.status = "TODO";
            todoItemMapper.insert(todo);
        }
        if (slowLetterMapper.selectCount(new QueryWrapper<SlowLetter>().eq("sender_user_id", demo.id)) == 0) {
            SlowLetter letter = new SlowLetter();
            letter.senderUserId = demo.id;
            letter.receiverUserId = 1L;
            letter.receiverCapsuleId = 1L;
            letter.title = "我在那句话里停了一下";
            letter.letterBody = "我不是急着认识谁，只是想认真告诉你：那种把自己从自责里慢慢捞出来的感觉，我好像也懂。";
            letter.status = "SENT";
            letter.parallaxDistance = 3;
            slowLetterMapper.insert(letter);
        }
    }
}
