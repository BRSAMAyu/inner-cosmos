package com.innercosmos.controller;

import com.innercosmos.common.Constants;
import com.innercosmos.entity.FriendRelation;
import com.innercosmos.service.SocialService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpSession;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * G2.ARCH-MODULES: SocialController now delegates every friend-connection operation to
 * SocialService -- this only pins the controller's own responsibility (session -> caller id,
 * request body -> typed args, service result -> ApiResponse). The business rules and owner-scope
 * checks these tests used to cover live in SocialServiceImplTest now.
 */
@ExtendWith(MockitoExtension.class)
class SocialConnectionControllerTest {
    @Mock SocialService socialService;

    private SocialController controller;
    private MockHttpSession session;

    @BeforeEach
    void setUp() {
        controller = new SocialController(socialService);
        session = new MockHttpSession();
        session.setAttribute(Constants.SESSION_USER_KEY, 20L);
    }

    @Test
    void requestFromLetterDelegatesCallerAndLetterIdToTheService() {
        FriendRelation expected = new FriendRelation();
        when(socialService.requestFriendFromLetter(20L, 7L)).thenReturn(expected);

        FriendRelation result = controller.requestFromLetter(7L, session).data;

        assertSame(expected, result);
        verify(socialService).requestFriendFromLetter(20L, 7L);
    }

    @Test
    void requestFriendParsesTargetUserIdAndSourceFromTheBody() {
        FriendRelation expected = new FriendRelation();
        when(socialService.requestFriend(20L, 30L, "SOCIAL_PAGE")).thenReturn(expected);

        FriendRelation result = controller.requestFriend(Map.of("userId", "30"), session).data;

        assertSame(expected, result);
        verify(socialService).requestFriend(20L, 30L, "SOCIAL_PAGE");
    }

    @Test
    void acceptDelegatesCallerAndRelationIdToTheService() {
        FriendRelation expected = new FriendRelation();
        expected.status = "ACCEPTED";
        when(socialService.acceptFriendRequest(20L, 1L)).thenReturn(expected);

        assertEquals("ACCEPTED", controller.accept(1L, session).data.status);
        verify(socialService).acceptFriendRequest(20L, 1L);
    }

    @Test
    void leaveDelegatesCallerAndRelationIdToTheService() {
        FriendRelation expected = new FriendRelation();
        expected.status = "WITHDRAWN";
        when(socialService.leaveFriendRelation(20L, 1L)).thenReturn(expected);

        assertEquals("WITHDRAWN", controller.leave(1L, session).data.status);
        verify(socialService).leaveFriendRelation(20L, 1L);
    }

    @Test
    void peopleDelegatesToTheServiceWithTheCallerId() {
        when(socialService.discoverPeople(20L)).thenReturn(java.util.List.of());

        controller.people(session);

        verify(socialService).discoverPeople(20L);
    }
}
