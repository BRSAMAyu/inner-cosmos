package com.innercosmos.ai.context;

import com.innercosmos.ai.perception.GeocodingService;
import com.innercosmos.ai.perception.TimeContextService;
import com.innercosmos.ai.perception.TimeContextService.TimeContext;
import com.innercosmos.ai.perception.WeatherContextService;
import com.innercosmos.ai.semantic.EmotionInsight;
import com.innercosmos.ai.semantic.MomentMood;
import com.innercosmos.entity.UserProfile;
import com.innercosmos.mapper.DailyRecordMapper;
import com.innercosmos.mapper.DialogMessageMapper;
import com.innercosmos.mapper.EmotionTraceMapper;
import com.innercosmos.mapper.MemoryCardMapper;
import com.innercosmos.mapper.MemoryThemeMapper;
import com.innercosmos.mapper.RelationMentionMapper;
import com.innercosmos.mapper.TodoItemMapper;
import com.innercosmos.mapper.UserProfileMapper;
import com.innercosmos.mapper.WeeklyReviewMapper;
import com.innercosmos.service.EmotionInsightService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * IC-EMO-002 unit coverage for the "此刻情绪" (momentEmotionLabel) assembly in
 * {@link AgentContextAssembler}: it surfaces emotion + intensity + brief spectrum,
 * falls back gracefully when there is no mood, and respects the weather/emotion
 * opt-out — without disturbing the existing {@code weatherLabel} contract.
 */
@ExtendWith(MockitoExtension.class)
class AgentContextAssemblerMomentEmotionTest {

    @Mock private UserProfileMapper userProfileMapper;
    @Mock private DialogMessageMapper dialogMessageMapper;
    @Mock private MemoryCardMapper memoryCardMapper;
    @Mock private TodoItemMapper todoItemMapper;
    @Mock private DailyRecordMapper dailyRecordMapper;
    @Mock private WeeklyReviewMapper weeklyReviewMapper;
    @Mock private EmotionTraceMapper emotionTraceMapper;
    @Mock private RelationMentionMapper relationMentionMapper;
    @Mock private MemoryThemeMapper memoryThemeMapper;
    @Mock private GeocodingService geocodingService;
    @Mock private WeatherContextService weatherContextService;
    @Mock private TimeContextService timeContextService;
    @Mock private EmotionInsightService emotionInsightService;

    private AgentContextAssembler assembler;

    private static final Long USER_ID = 7L;

    @BeforeEach
    void setUp() {
        assembler = new AgentContextAssembler(userProfileMapper, dialogMessageMapper,
                memoryCardMapper, todoItemMapper, dailyRecordMapper, weeklyReviewMapper,
                emotionTraceMapper, relationMentionMapper, memoryThemeMapper,
                geocodingService, weatherContextService, timeContextService);
        // emotionInsightService is @Autowired(required=false) — set it as Spring would.
        ReflectionTestUtils.setField(assembler, "emotionInsightService", emotionInsightService);

        // Quiet, valid neighbours so assemble() runs end-to-end.
        TimeContext time = new TimeContext("下午", "2026-06-19", false, false, null);
        lenient().when(timeContextService.now(anyBoolean(), any())).thenReturn(time);
        lenient().when(dialogMessageMapper.selectList(any())).thenReturn(List.of());
        lenient().when(todoItemMapper.selectList(any())).thenReturn(List.of());
        lenient().when(todoItemMapper.selectOne(any())).thenReturn(null);
        lenient().when(dailyRecordMapper.selectList(any())).thenReturn(List.of());
        lenient().when(weeklyReviewMapper.selectList(any())).thenReturn(List.of());
        lenient().when(relationMentionMapper.selectList(any())).thenReturn(List.of());
        lenient().when(memoryThemeMapper.selectList(any())).thenReturn(List.of());
        lenient().when(memoryCardMapper.selectList(any())).thenReturn(List.of());
        lenient().when(emotionTraceMapper.selectOne(any())).thenReturn(null);
    }

    private void profile(UserProfile profile) {
        when(userProfileMapper.selectOne(any())).thenReturn(profile);
    }

    private MomentMood enrichedMood() {
        MomentMood mood = new MomentMood();
        mood.present = true;
        mood.primaryEmotion = "平静";
        mood.intensity = 4.0;
        mood.weatherType = "SUNNY";
        mood.spectrum = List.of(
                new EmotionInsight.SpectrumEntry("平静", 0.6),
                new EmotionInsight.SpectrumEntry("期待", 0.3));
        mood.momentLabel = "平静（平静 60% · 期待 30%）";
        return mood;
    }

    @Test
    @DisplayName("momentEmotionLabel: surfaces emotion + intensity + brief spectrum")
    void momentEmotion_enriched() {
        profile(null);
        when(emotionInsightService.latestMood(USER_ID)).thenReturn(enrichedMood());

        AgentContext ctx = assembler.assemble(USER_ID, null, "你好", false);

        assertNotNull(ctx.momentEmotionLabel);
        assertTrue(ctx.momentEmotionLabel.contains("平静"), "primary emotion present");
        assertTrue(ctx.momentEmotionLabel.contains("60%"), "brief spectrum present");
        assertTrue(ctx.momentEmotionLabel.contains("4") && ctx.momentEmotionLabel.contains("10"),
                "intensity on 0..10 present");
    }

    @Test
    @DisplayName("momentEmotionLabel: no trace -> graceful 暂无此刻情绪")
    void momentEmotion_noTrace() {
        profile(null);
        when(emotionInsightService.latestMood(USER_ID))
                .thenReturn(MomentMood.absent("此刻还没有读到你的情绪"));

        AgentContext ctx = assembler.assemble(USER_ID, null, "你好", false);

        assertEquals("暂无此刻情绪", ctx.momentEmotionLabel);
    }

    @Test
    @DisplayName("momentEmotionLabel: weather/emotion opt-out -> 用户关闭了情绪感知 (service not called)")
    void momentEmotion_optOut() {
        UserProfile profile = new UserProfile();
        profile.weatherAwarenessEnabled = false;
        profile(profile);

        AgentContext ctx = assembler.assemble(USER_ID, null, "你好", false);

        assertEquals("用户关闭了情绪感知", ctx.momentEmotionLabel);
        // weatherLabel must still honor the same opt-out (unbroken contract).
        assertEquals("用户关闭了天气感知", ctx.weatherLabel);
    }

    @Test
    @DisplayName("momentEmotionLabel: malformed spectrum degrades to emotion + intensity only")
    void momentEmotion_emotionOnly() {
        profile(null);
        MomentMood mood = new MomentMood();
        mood.present = true;
        mood.primaryEmotion = "焦虑";
        mood.intensity = 6.0;
        mood.weatherType = "FOGGY";
        mood.momentLabel = "焦虑"; // spectrum-less fallback from the service
        when(emotionInsightService.latestMood(USER_ID)).thenReturn(mood);

        AgentContext ctx = assembler.assemble(USER_ID, null, "你好", false);

        assertTrue(ctx.momentEmotionLabel.contains("焦虑"));
        assertTrue(ctx.momentEmotionLabel.contains("6") && ctx.momentEmotionLabel.contains("10"));
    }
}
