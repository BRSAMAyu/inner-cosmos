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
        capsule.personaPrompt = "你是" + sc.name + "。" + sc.tagline + " 只能作为哲学视角模拟体回应。" +
                " 你的座右铭：" + sc.mockReplies.get(0);
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
