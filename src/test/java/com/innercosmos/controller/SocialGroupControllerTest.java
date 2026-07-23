package com.innercosmos.controller;

import com.innercosmos.common.Constants;
import com.innercosmos.entity.SocialGroupMember;
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
 * G2.ARCH-MODULES: the Groups tab endpoints on SocialController now delegate to SocialService --
 * this only pins the controller's own responsibility (session -> caller id, request body -> typed
 * args, service result -> ApiResponse). The invite/respond/leave/members business rules these
 * tests used to cover live in SocialServiceImplTest now.
 */
@ExtendWith(MockitoExtension.class)
class SocialGroupControllerTest {
    @Mock SocialService socialService;

    private SocialController controller;
    private MockHttpSession session;

    @BeforeEach
    void setUp() {
        controller = new SocialController(socialService);
        session = new MockHttpSession();
        session.setAttribute(Constants.SESSION_USER_KEY, 20L);
    }

    private SocialGroupMember member(Long id, Long groupId, Long userId, String role, String status) {
        SocialGroupMember m = new SocialGroupMember();
        m.id = id; m.groupId = groupId; m.userId = userId; m.memberRole = role; m.status = status;
        return m;
    }

    @Test
    void inviteToGroupParsesTheTargetUserIdFromTheBody() {
        SocialGroupMember expected = member(2L, 5L, 30L, "MEMBER", "PENDING");
        when(socialService.inviteToGroup(20L, 5L, 30L)).thenReturn(expected);

        SocialGroupMember result = controller.inviteToGroup(5L, Map.of("userId", "30"), session).data;

        assertSame(expected, result);
        verify(socialService).inviteToGroup(20L, 5L, 30L);
    }

    @Test
    void groupInvitesDelegatesToTheServiceWithTheCallerId() {
        when(socialService.listGroupInvites(20L)).thenReturn(java.util.List.of());

        controller.groupInvites(session);

        verify(socialService).listGroupInvites(20L);
    }

    @Test
    void respondToGroupInvitePassesTheDecisionThrough() {
        SocialGroupMember expected = member(9L, 5L, 20L, "MEMBER", "ACTIVE");
        when(socialService.respondToGroupInvite(20L, 9L, "accept")).thenReturn(expected);

        SocialGroupMember result = controller.respondToGroupInvite(9L, Map.of("decision", "accept"), session).data;

        assertSame(expected, result);
        verify(socialService).respondToGroupInvite(20L, 9L, "accept");
    }

    @Test
    void leaveGroupDelegatesCallerAndGroupIdToTheService() {
        controller.leaveGroup(5L, session);

        verify(socialService).leaveGroup(20L, 5L);
    }

    @Test
    void groupMembersDelegatesCallerAndGroupIdToTheService() {
        SocialGroupMember owner = member(1L, 5L, 20L, "OWNER", "ACTIVE");
        Map<String, Object> row = Map.of("userId", owner.userId, "memberRole", owner.memberRole, "nickname", "我");
        when(socialService.listGroupMembers(20L, 5L)).thenReturn(java.util.List.of(row));

        var members = controller.groupMembers(5L, session).data;

        assertEquals(1, members.size());
        assertEquals(20L, members.get(0).get("userId"));
        assertEquals("我", members.get(0).get("nickname"));
        verify(socialService).listGroupMembers(20L, 5L);
    }
}
