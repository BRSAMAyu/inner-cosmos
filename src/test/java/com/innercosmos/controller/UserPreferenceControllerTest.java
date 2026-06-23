package com.innercosmos.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.ai.router.SessionModelRouter;
import com.innercosmos.common.Constants;
import com.innercosmos.entity.UserProfile;
import com.innercosmos.mapper.UserProfileMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** M-030: preferred-model must be loaded by user_id (FK), not by the profile PK id. */
class UserPreferenceControllerTest {

    @Test
    @DisplayName("M-030: setPreferredModel selects the profile by user_id, never selectById")
    void setPreferredModel_usesUserIdKey() {
        UserProfileMapper mapper = mock(UserProfileMapper.class);
        UserProfile profile = new UserProfile();
        profile.id = 7L;          // profile PK (≠ userId on purpose)
        profile.userId = 2L;      // the FK the lookup must use
        when(mapper.selectOne(any(QueryWrapper.class))).thenReturn(profile);
        when(mapper.updateById(any(UserProfile.class))).thenReturn(1);

        UserPreferenceController controller =
                new UserPreferenceController(mapper, mock(SessionModelRouter.class));

        MockHttpSession session = new MockHttpSession();
        session.setAttribute(Constants.SESSION_USER_KEY, 2L);

        controller.setPreferredModel(Map.of("provider", "glm"), session);

        verify(mapper).selectOne(any(QueryWrapper.class)); // by user_id
        verify(mapper, never()).selectById(any());         // never the wrong PK lookup
        assertEquals("GLM", profile.preferredModel);
    }
}
