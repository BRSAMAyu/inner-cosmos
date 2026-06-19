package com.innercosmos.ai.context;

import com.innercosmos.ai.portrait.AgentUserRelationshipService;
import com.innercosmos.ai.portrait.AuroraSelfProfileService;
import com.innercosmos.ai.portrait.UserPortraitService;
import com.innercosmos.ai.perception.GeocodingService;
import com.innercosmos.ai.perception.TimeContextService;
import com.innercosmos.ai.perception.WeatherContextService;
import com.innercosmos.ai.semantic.EmotionBaseline;
import com.innercosmos.entity.AgentUserRelationship;
import com.innercosmos.entity.AuroraSelfProfile;
import com.innercosmos.mapper.DailyRecordMapper;
import com.innercosmos.mapper.DialogMessageMapper;
import com.innercosmos.mapper.EmotionTraceMapper;
import com.innercosmos.mapper.MemoryCardMapper;
import com.innercosmos.mapper.MemoryThemeMapper;
import com.innercosmos.mapper.RelationMentionMapper;
import com.innercosmos.mapper.TodoItemMapper;
import com.innercosmos.mapper.UserProfileMapper;
import com.innercosmos.mapper.WeeklyReviewMapper;
import com.innercosmos.service.EmotionBaselineService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * IC-EMO-003: the mid-term emotion baseline line is injected into the 【User Portrait】
 * block of {@code buildThreeModelBlock}, coexisting with (not replacing) the Phase-2
 * real-time "此刻情绪" line. Degrades gracefully when the service is absent or no
 * baseline exists.
 */
@ExtendWith(MockitoExtension.class)
class AgentContextAssemblerBaselineTest {

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
    @Mock private AuroraSelfProfileService auroraSelfProfileService;
    @Mock private AgentUserRelationshipService relationshipService;
    @Mock private UserPortraitService userPortraitService;
    @Mock private EmotionBaselineService emotionBaselineService;

    private AgentContextAssembler assembler;
    private static final Long USER_ID = 42L;

    @BeforeEach
    void setUp() {
        assembler = new AgentContextAssembler(userProfileMapper, dialogMessageMapper,
                memoryCardMapper, todoItemMapper, dailyRecordMapper, weeklyReviewMapper,
                emotionTraceMapper, relationMentionMapper, memoryThemeMapper,
                geocodingService, weatherContextService, timeContextService);
        ReflectionTestUtils.setField(assembler, "auroraSelfProfileService", auroraSelfProfileService);
        ReflectionTestUtils.setField(assembler, "relationshipService", relationshipService);
        ReflectionTestUtils.setField(assembler, "userPortraitService", userPortraitService);
        ReflectionTestUtils.setField(assembler, "emotionBaselineService", emotionBaselineService);

        AuroraSelfProfile self = new AuroraSelfProfile();
        self.identityJson = "{\"name\":\"Aurora\"}";
        lenient().when(auroraSelfProfileService.get()).thenReturn(self);
        AgentUserRelationship rel = new AgentUserRelationship();
        rel.userId = USER_ID;
        lenient().when(relationshipService.getOrInit(USER_ID)).thenReturn(rel);
        lenient().when(userPortraitService.getAll(any())).thenReturn(List.of());
    }

    private EmotionBaseline presentBaseline() {
        EmotionBaseline b = new EmotionBaseline();
        b.present = true;
        b.dominantEmotion = "平静";
        b.intensityMean = 5.2;
        b.intensityVariance = 0.4;
        b.stabilityScore = 0.71;
        b.sampleCount = 6;
        b.windowDays = 14;
        b.baselineLabel = "近 14 日总体平稳，主基调「平静」，强度适中（均值 5.2/10）";
        return b;
    }

    @Test
    @DisplayName("buildThreeModelBlock injects the emotion-baseline line when a baseline exists")
    void baselineLineInjected() {
        when(emotionBaselineService.computeBaseline(USER_ID)).thenReturn(presentBaseline());

        String block = (String) ReflectionTestUtils.invokeMethod(
                assembler, "buildThreeModelBlock", USER_ID);

        assertNotNull(block);
        assertTrue(block.contains("情绪基线"), "baseline line present");
        assertTrue(block.contains("平静"), "dominant emotion surfaced");
        assertTrue(block.contains("【User Portrait】"), "baseline lives within the User Portrait block");
    }

    @Test
    @DisplayName("buildThreeModelBlock degrades to 暂无情绪基线 when no baseline")
    void baselineAbsentGraceful() {
        when(emotionBaselineService.computeBaseline(USER_ID))
                .thenReturn(EmotionBaseline.absent(14));

        String block = (String) ReflectionTestUtils.invokeMethod(
                assembler, "buildThreeModelBlock", USER_ID);

        assertNotNull(block);
        assertTrue(block.contains("暂无情绪基线"), "graceful empty-state baseline label");
    }

    @Test
    @DisplayName("buildThreeModelBlock works when the baseline service bean is absent (lightweight tests)")
    void baselineServiceNullSafe() {
        ReflectionTestUtils.setField(assembler, "emotionBaselineService", null);

        String block = (String) ReflectionTestUtils.invokeMethod(
                assembler, "buildThreeModelBlock", USER_ID);

        assertNotNull(block);
        assertFalse(block.isBlank());
        assertTrue(block.contains("【User Portrait】"));
    }
}
