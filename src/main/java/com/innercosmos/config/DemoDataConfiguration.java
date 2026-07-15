package com.innercosmos.config;

import com.innercosmos.mapper.AuroraConstitutionMapper;
import com.innercosmos.mapper.AuroraSelfModelMapper;
import com.innercosmos.mapper.AuroraSelfProfileMapper;
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
import com.innercosmos.mapper.UserPortraitMapper;
import com.innercosmos.mapper.UserProfileMapper;
import com.innercosmos.service.EmotionBaselineService;
import com.innercosmos.service.GravityService;
import com.innercosmos.service.UserService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Demo data is opt-in and is structurally absent from the production profile.
 * The profile exclusion is deliberately independent of the property condition:
 * even a mistaken {@code inner-cosmos.demo.seed-enabled=true} cannot create demo
 * accounts while {@code prod} is active.
 */
@Configuration(proxyBeanMethods = false)
@Profile("!prod")
@ConditionalOnProperty(prefix = "inner-cosmos.demo", name = "seed-enabled", havingValue = "true")
public class DemoDataConfiguration {

    @Bean
    MockDataInitializer mockDataInitializer(
            UserMapper userMapper,
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
            EmotionBaselineService emotionBaselineService,
            AuthorizedMemoryRefMapper authorizedMemoryRefMapper) {
        return new MockDataInitializer(userMapper, userProfileMapper, capsuleMapper, boundaryMapper,
                memoryCardMapper, todoItemMapper, slowLetterMapper, dailyRecordMapper,
                emotionTraceMapper, thoughtFragmentMapper, eventCardMapper,
                relationMentionMapper, memoryThemeMapper, gravityService, userService,
                auroraSelfProfileMapper, auroraConstitutionMapper, userPortraitMapper,
                auroraSelfModelMapper, auroraSelfReflectionMapper, beliefPatternMapper,
                emotionBaselineService, authorizedMemoryRefMapper);
    }
}
