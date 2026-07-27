package com.innercosmos.ai.router;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.ai.client.LlmClient;
import com.innercosmos.config.LlmConfig;
import com.innercosmos.entity.UserProfile;
import com.innercosmos.mapper.DialogSessionMapper;
import com.innercosmos.mapper.UserProfileMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SessionModelRouterTest {

    @Test
    void resolvesProfileByUserForeignKeyInsteadOfUnrelatedProfilePrimaryKey() {
        DialogSessionMapper sessionMapper = mock(DialogSessionMapper.class);
        UserProfileMapper userMapper = mock(UserProfileMapper.class);
        LlmClient gemini = mock(LlmClient.class);
        LlmClient deepSeek = mock(LlmClient.class);

        LlmConfig config = new LlmConfig();
        config.provider = "gemini";
        config.gemini.model = "gemini-3.6-flash";
        config.deepseek.model = "deepseek-v4-flash";

        Map<String, LlmClient> named = new LinkedHashMap<>();
        named.put("GEMINI", gemini);
        named.put("DEEPSEEK", deepSeek);

        // This row represents the regression: its profile PK happens to equal the current
        // user's ID, but its user_id belongs to somebody else and prefers DeepSeek.
        UserProfile unrelatedPrimaryKeyMatch = new UserProfile();
        unrelatedPrimaryKeyMatch.id = 42L;
        unrelatedPrimaryKeyMatch.userId = 7L;
        unrelatedPrimaryKeyMatch.preferredModel = "DEEPSEEK";
        when(userMapper.selectById(42L)).thenReturn(unrelatedPrimaryKeyMatch);

        // The current user has no explicit preference, so the configured Gemini default wins.
        when(userMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);

        SessionModelRouter router = new SessionModelRouter();
        ReflectionTestUtils.setField(router, "named", named);
        ReflectionTestUtils.setField(router, "sessionMapper", sessionMapper);
        ReflectionTestUtils.setField(router, "userMapper", userMapper);
        ReflectionTestUtils.setField(router, "llmConfig", config);

        ResolvedModel resolved = router.resolve(42L, null);

        assertEquals("GEMINI", resolved.provider());
        assertEquals("gemini-3.6-flash", resolved.model());
        assertSame(gemini, resolved.client());
        verify(userMapper).selectOne(any(QueryWrapper.class));
        verify(userMapper, never()).selectById(42L);
    }

    @Test
    void honorsPreferenceFromProfileBelongingToCurrentUser() {
        DialogSessionMapper sessionMapper = mock(DialogSessionMapper.class);
        UserProfileMapper userMapper = mock(UserProfileMapper.class);
        LlmClient gemini = mock(LlmClient.class);
        LlmClient deepSeek = mock(LlmClient.class);

        LlmConfig config = new LlmConfig();
        config.provider = "gemini";
        config.gemini.model = "gemini-3.6-flash";
        config.deepseek.model = "deepseek-v4-flash";

        UserProfile profile = new UserProfile();
        profile.id = 900L;
        profile.userId = 42L;
        profile.preferredModel = "DEEPSEEK";
        when(userMapper.selectOne(any(QueryWrapper.class))).thenReturn(profile);

        SessionModelRouter router = new SessionModelRouter();
        ReflectionTestUtils.setField(router, "named", Map.of(
                "GEMINI", gemini,
                "DEEPSEEK", deepSeek));
        ReflectionTestUtils.setField(router, "sessionMapper", sessionMapper);
        ReflectionTestUtils.setField(router, "userMapper", userMapper);
        ReflectionTestUtils.setField(router, "llmConfig", config);

        ResolvedModel resolved = router.resolve(42L, null);

        assertEquals("DEEPSEEK", resolved.provider());
        assertEquals("deepseek-v4-flash", resolved.model());
        assertSame(deepSeek, resolved.client());
    }
}
