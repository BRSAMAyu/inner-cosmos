package com.innercosmos.controller;

import com.innercosmos.common.Constants;
import com.innercosmos.entity.User;
import com.innercosmos.service.UserService;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DemoExperienceControllerTest {

    @Test
    void disabledDemoDoesNotAdvertisePersonas() {
        UserService users = mock(UserService.class);
        Environment env = mock(Environment.class);
        when(env.getActiveProfiles()).thenReturn(new String[]{"dev"});
        when(env.getProperty("inner-cosmos.demo.public-entry-enabled", Boolean.class, false))
                .thenReturn(false);
        DemoExperienceController controller = new DemoExperienceController(users, env);

        assertThat(controller.personas().data).isEmpty();
    }

    @Test
    void enabledDemoEntersOnlyCuratedNonAdminIdentity() {
        UserService users = mock(UserService.class);
        Environment env = mock(Environment.class);
        when(env.getActiveProfiles()).thenReturn(new String[]{"dev"});
        when(env.getProperty("inner-cosmos.demo.public-entry-enabled", Boolean.class, false))
                .thenReturn(true);
        User river = new User();
        river.id = 42L;
        river.username = "river";
        river.nickname = "沈砚";
        river.role = Constants.ROLE_USER;
        river.status = "ACTIVE";
        river.accountKind = "SHOWCASE";
        when(users.findPublicDemoPersona("river")).thenReturn(river);
        DemoExperienceController controller = new DemoExperienceController(users, env);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession(true);

        var response = controller.enter("shen-yan", request);

        HttpSession session = request.getSession(false);
        assertThat(response.data.username).isEqualTo("river");
        assertThat(session).isNotNull();
        assertThat(session.getAttribute(Constants.SESSION_USER_KEY)).isEqualTo(42L);
    }

    @Test
    void prodProfileRefusesPublicDemoEvenWhenFlagWasSet() {
        UserService users = mock(UserService.class);
        Environment env = mock(Environment.class);
        when(env.getActiveProfiles()).thenReturn(new String[]{"prod"});
        when(env.getProperty("inner-cosmos.demo.public-entry-enabled", Boolean.class, false))
                .thenReturn(true);
        DemoExperienceController controller = new DemoExperienceController(users, env);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession(true);

        assertThat(controller.personas().data).isEmpty();
        assertThatThrownBy(() -> controller.enter("lin-che", request))
                .hasMessageContaining("未开启");
    }
}
