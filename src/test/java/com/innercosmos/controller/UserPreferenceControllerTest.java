package com.innercosmos.controller;

import com.innercosmos.ai.router.SessionModelRouter;
import com.innercosmos.common.Constants;
import com.innercosmos.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import java.util.Map;

import static org.mockito.Mockito.*;

/**
 * The M-030 by-user_id-not-by-PK regression is now pinned at the service layer in
 * {@code UserServiceImplPreferredModelTest} (G2.ARCH-MODULES: this controller was refactored to go
 * through {@link UserService} instead of injecting {@code UserProfileMapper} directly). This test
 * only covers the controller's own responsibility: extracting the session user id and the request
 * body's provider field, and delegating them unchanged to the service.
 */
class UserPreferenceControllerTest {

    @Test
    @DisplayName("setPreferredModel delegates the session user id and body provider to UserService")
    void setPreferredModel_delegatesToUserService() {
        UserService userService = mock(UserService.class);
        UserPreferenceController controller =
                new UserPreferenceController(userService, mock(SessionModelRouter.class));

        MockHttpSession session = new MockHttpSession();
        session.setAttribute(Constants.SESSION_USER_KEY, 2L);

        controller.setPreferredModel(Map.of("provider", "glm"), session);

        verify(userService).setPreferredModel(2L, "glm");
    }

    @Test
    @DisplayName("a missing provider field is passed through as null (service clears the preference)")
    void setPreferredModel_missingProviderPassesNull() {
        UserService userService = mock(UserService.class);
        UserPreferenceController controller =
                new UserPreferenceController(userService, mock(SessionModelRouter.class));

        MockHttpSession session = new MockHttpSession();
        session.setAttribute(Constants.SESSION_USER_KEY, 5L);

        controller.setPreferredModel(Map.of(), session);

        verify(userService).setPreferredModel(5L, null);
    }
}
