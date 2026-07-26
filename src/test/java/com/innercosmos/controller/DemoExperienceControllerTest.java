package com.innercosmos.controller;

import com.innercosmos.common.Constants;
import com.innercosmos.entity.User;
import com.innercosmos.service.DemoSandboxService;
import com.innercosmos.service.UserService;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DemoExperienceControllerTest {

    @Test
    void disabledDemoDoesNotAdvertisePersonas() {
        UserService users = mock(UserService.class);
        Environment env = mock(Environment.class);
        when(env.getActiveProfiles()).thenReturn(new String[]{"dev"});
        when(env.getProperty("inner-cosmos.demo.public-entry-enabled", Boolean.class, false))
                .thenReturn(false);
        DemoExperienceController controller = controller(users, mock(DemoSandboxService.class), env);

        assertThat(controller.personas(new MockHttpServletRequest()).data).isEmpty();
    }

    @Test
    void enabledDemoCreatesAnIsolatedSandboxInsteadOfEnteringSharedIdentity() {
        UserService users = mock(UserService.class);
        DemoSandboxService sandboxes = mock(DemoSandboxService.class);
        Environment env = mock(Environment.class);
        when(env.getActiveProfiles()).thenReturn(new String[]{"dev"});
        when(env.getProperty("inner-cosmos.demo.public-entry-enabled", Boolean.class, false))
                .thenReturn(true);
        User riverSandbox = user(42L, "sandbox-a", "SANDBOX");
        when(sandboxes.createPersonalSandbox("shen-yan")).thenReturn(riverSandbox);
        DemoExperienceController controller = controller(users, sandboxes, env);
        MockHttpServletRequest request = new MockHttpServletRequest();
        String anonymousSessionId = request.getSession(true).getId();

        var response = controller.enter("shen-yan", request);

        HttpSession session = request.getSession(false);
        assertThat(response.data.username).isEqualTo("sandbox-a");
        assertThat(session).isNotNull();
        assertThat(session.getId()).isNotEqualTo(anonymousSessionId);
        assertThat(session.getAttribute(Constants.SESSION_USER_KEY)).isEqualTo(42L);
        verify(sandboxes).createPersonalSandbox("shen-yan");
        verify(users, never()).findPublicDemoPersona("river");
    }

    @Test
    void returningToAStoryReusesOnlyThatSessionsSandbox() {
        UserService users = mock(UserService.class);
        DemoSandboxService sandboxes = mock(DemoSandboxService.class);
        Environment env = enabledEnvironment();
        User personalCopy = user(42L, "sandbox-a", "SANDBOX");
        when(sandboxes.createPersonalSandbox("lin-che")).thenReturn(personalCopy);
        when(users.current(42L)).thenReturn(personalCopy);
        DemoExperienceController controller = controller(users, sandboxes, env);
        MockHttpServletRequest request = new MockHttpServletRequest();

        controller.enter("lin-che", request);
        controller.enter("lin-che", request);

        verify(sandboxes).createPersonalSandbox("lin-che");
        verify(users, times(2)).current(42L);
        assertThat(controller.personas(request).data)
                .filteredOn(DemoExperienceController.DemoPersona::active)
                .extracting(DemoExperienceController.DemoPersona::key)
                .containsExactly("lin-che");
    }

    @Test
    void separateBrowserSessionsReceiveSeparateOwners() {
        UserService users = mock(UserService.class);
        DemoSandboxService sandboxes = mock(DemoSandboxService.class);
        when(sandboxes.createPersonalSandbox("xia-yu"))
                .thenReturn(user(51L, "sandbox-one", "SANDBOX"))
                .thenReturn(user(52L, "sandbox-two", "SANDBOX"));
        DemoExperienceController controller = controller(users, sandboxes, enabledEnvironment());
        MockHttpServletRequest first = new MockHttpServletRequest();
        MockHttpServletRequest second = new MockHttpServletRequest();

        controller.enter("xia-yu", first);
        controller.enter("xia-yu", second);

        assertThat(first.getSession(false).getAttribute(Constants.SESSION_USER_KEY)).isEqualTo(51L);
        assertThat(second.getSession(false).getAttribute(Constants.SESSION_USER_KEY)).isEqualTo(52L);
    }

    @Test
    void ordinaryRegisteredUserCannotSeeOrEnterStorySwitcher() {
        UserService users = mock(UserService.class);
        DemoSandboxService sandboxes = mock(DemoSandboxService.class);
        User human = user(7L, "alex", "HUMAN");
        when(users.current(7L)).thenReturn(human);
        DemoExperienceController controller = controller(users, sandboxes, enabledEnvironment());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession(true).setAttribute(Constants.SESSION_USER_KEY, 7L);

        assertThat(controller.personas(request).data).isEmpty();
        assertThatThrownBy(() -> controller.enter("lin-che", request))
                .hasMessageContaining("available before sign-up");
        verify(sandboxes, never()).createPersonalSandbox("lin-che");
    }

    @Test
    void enabledDemoAdvertisesAnEnglishClassroomJourney() {
        UserService users = mock(UserService.class);
        Environment env = mock(Environment.class);
        when(env.getActiveProfiles()).thenReturn(new String[]{"dev"});
        when(env.getProperty("inner-cosmos.demo.public-entry-enabled", Boolean.class, false))
                .thenReturn(true);

        var personas = controller(users, mock(DemoSandboxService.class), env)
                .personas(new MockHttpServletRequest()).data;

        assertThat(personas).hasSize(3);
        assertThat(personas).allSatisfy(persona -> {
            assertThat(persona.name()).doesNotContainPattern("[\\p{IsHan}]");
            assertThat(persona.headline()).doesNotContainPattern("[\\p{IsHan}]");
            assertThat(persona.story()).doesNotContainPattern("[\\p{IsHan}]");
        });
    }

    @Test
    void prodProfileRefusesPublicDemoEvenWhenFlagWasSet() {
        UserService users = mock(UserService.class);
        Environment env = mock(Environment.class);
        when(env.getActiveProfiles()).thenReturn(new String[]{"prod"});
        when(env.getProperty("inner-cosmos.demo.public-entry-enabled", Boolean.class, false))
                .thenReturn(true);
        DemoExperienceController controller = controller(users, mock(DemoSandboxService.class), env);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession(true);

        assertThat(controller.personas(request).data).isEmpty();
        assertThatThrownBy(() -> controller.enter("lin-che", request))
                .hasMessageContaining("not enabled");
    }

    private static DemoExperienceController controller(UserService users, DemoSandboxService sandboxes,
                                                       Environment environment) {
        @SuppressWarnings("unchecked")
        ObjectProvider<DemoSandboxService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(sandboxes);
        return new DemoExperienceController(users, provider, environment);
    }

    private static Environment enabledEnvironment() {
        Environment env = mock(Environment.class);
        when(env.getActiveProfiles()).thenReturn(new String[]{"dev"});
        when(env.getProperty("inner-cosmos.demo.public-entry-enabled", Boolean.class, false))
                .thenReturn(true);
        return env;
    }

    private static User user(Long id, String username, String accountKind) {
        User user = new User();
        user.id = id;
        user.username = username;
        user.nickname = username;
        user.role = Constants.ROLE_USER;
        user.status = "ACTIVE";
        user.accountKind = accountKind;
        return user;
    }
}
