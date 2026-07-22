package com.innercosmos.controller;

import com.innercosmos.common.Constants;
import com.innercosmos.entity.SocialGroup;
import com.innercosmos.entity.SocialGroupMember;
import com.innercosmos.entity.User;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.mapper.BlockRelationMapper;
import com.innercosmos.mapper.FriendRelationMapper;
import com.innercosmos.mapper.SlowLetterMapper;
import com.innercosmos.mapper.SocialGroupMapper;
import com.innercosmos.mapper.SocialGroupMemberMapper;
import com.innercosmos.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpSession;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * The Groups tab in RelationsView needs invite/respond/leave/members endpoints that the audit in
 * the closure plan flagged as possibly missing beyond list+create -- confirmed genuinely absent.
 * These tests pin the new SocialController group-membership methods.
 */
@ExtendWith(MockitoExtension.class)
class SocialGroupControllerTest {
    @Mock UserMapper userMapper;
    @Mock FriendRelationMapper friendMapper;
    @Mock SocialGroupMapper groupMapper;
    @Mock SocialGroupMemberMapper memberMapper;
    @Mock SlowLetterMapper letterMapper;
    @Mock BlockRelationMapper blockMapper;

    private SocialController controller;
    private MockHttpSession session;

    @BeforeEach
    void setUp() {
        controller = new SocialController(userMapper, friendMapper, groupMapper, memberMapper,
                letterMapper, blockMapper);
        session = new MockHttpSession();
        session.setAttribute(Constants.SESSION_USER_KEY, 20L);
    }

    private SocialGroupMember member(Long id, Long groupId, Long userId, String role, String status) {
        SocialGroupMember m = new SocialGroupMember();
        m.id = id; m.groupId = groupId; m.userId = userId; m.memberRole = role; m.status = status;
        return m;
    }

    @Test
    void onlyAnActiveMemberCanInviteSomeoneElseIntoTheGroup() {
        when(memberMapper.selectOne(any())).thenReturn(null); // caller not an active member

        BusinessException error = assertThrows(BusinessException.class,
                () -> controller.inviteToGroup(5L, Map.of("userId", "30"), session));

        assertEquals("UNAUTHORIZED", error.code);
        verify(memberMapper, never()).insert(any(SocialGroupMember.class));
    }

    @Test
    void invitingCreatesAPendingMembershipForTheTargetUser() {
        when(memberMapper.selectOne(any()))
                .thenReturn(member(1L, 5L, 20L, "OWNER", "ACTIVE")) // caller's own membership
                .thenReturn(null); // target has no existing row
        when(userMapper.selectById(30L)).thenReturn(new User());

        controller.inviteToGroup(5L, Map.of("userId", "30"), session);

        ArgumentCaptor<SocialGroupMember> captor = ArgumentCaptor.forClass(SocialGroupMember.class);
        verify(memberMapper).insert(captor.capture());
        assertEquals(5L, captor.getValue().groupId);
        assertEquals(30L, captor.getValue().userId);
        assertEquals("PENDING", captor.getValue().status);
    }

    @Test
    void cannotInviteSomeoneAlreadyActiveOrAlreadyPending() {
        when(memberMapper.selectOne(any()))
                .thenReturn(member(1L, 5L, 20L, "OWNER", "ACTIVE"))
                .thenReturn(member(2L, 5L, 30L, "MEMBER", "PENDING"));
        when(userMapper.selectById(30L)).thenReturn(new User());

        BusinessException error = assertThrows(BusinessException.class,
                () -> controller.inviteToGroup(5L, Map.of("userId", "30"), session));

        assertEquals("BAD_REQUEST", error.code);
        verify(memberMapper, never()).insert(any(SocialGroupMember.class));
    }

    @Test
    void listsOnlyMyPendingInvitesWithTheGroupName() {
        when(memberMapper.selectList(any())).thenReturn(List.of(member(9L, 5L, 20L, "MEMBER", "PENDING")));
        SocialGroup group = new SocialGroup(); group.id = 5L; group.groupName = "老朋友们";
        when(groupMapper.selectList(any())).thenReturn(List.of(group));

        List<Map<String, Object>> invites = controller.groupInvites(session).data;

        assertEquals(1, invites.size());
        assertEquals(5L, invites.get(0).get("groupId"));
        assertEquals("老朋友们", invites.get(0).get("groupName"));
        assertEquals(9L, invites.get(0).get("memberId"));
    }

    @Test
    void acceptingAnInviteActivatesMembershipAndDecliningMarksItDeclined() {
        SocialGroupMember pending = member(9L, 5L, 20L, "MEMBER", "PENDING");
        when(memberMapper.selectById(9L)).thenReturn(pending);

        controller.respondToGroupInvite(9L, Map.of("decision", "accept"), session);
        assertEquals("ACTIVE", pending.status);

        pending.status = "PENDING";
        controller.respondToGroupInvite(9L, Map.of("decision", "decline"), session);
        assertEquals("DECLINED", pending.status);
    }

    @Test
    void cannotRespondToSomeoneElsesInvite() {
        SocialGroupMember pending = member(9L, 5L, 999L, "MEMBER", "PENDING");
        when(memberMapper.selectById(9L)).thenReturn(pending);

        BusinessException error = assertThrows(BusinessException.class,
                () -> controller.respondToGroupInvite(9L, Map.of("decision", "accept"), session));

        assertEquals("UNAUTHORIZED", error.code);
    }

    @Test
    void leavingRemovesAnOrdinaryMemberButNotTheOwner() {
        SocialGroupMember mine = member(1L, 5L, 20L, "MEMBER", "ACTIVE");
        when(memberMapper.selectOne(any())).thenReturn(mine);

        controller.leaveGroup(5L, session);

        assertEquals("LEFT", mine.status);
    }

    @Test
    void ownerCannotLeaveViaTheOrdinaryLeaveEndpoint() {
        when(memberMapper.selectOne(any())).thenReturn(member(1L, 5L, 20L, "OWNER", "ACTIVE"));

        BusinessException error = assertThrows(BusinessException.class, () -> controller.leaveGroup(5L, session));

        assertEquals("BAD_REQUEST", error.code);
        verify(memberMapper, never()).updateById(any(SocialGroupMember.class));
    }

    @Test
    void onlyAnActiveMemberCanListGroupMembers() {
        when(memberMapper.selectCount(any())).thenReturn(0L);

        BusinessException error = assertThrows(BusinessException.class, () -> controller.groupMembers(5L, session));

        assertEquals("UNAUTHORIZED", error.code);
    }

    @Test
    void listsActiveMembersWithNicknames() {
        when(memberMapper.selectCount(any())).thenReturn(1L);
        when(memberMapper.selectList(any())).thenReturn(List.of(member(1L, 5L, 20L, "OWNER", "ACTIVE")));
        User me = new User(); me.id = 20L; me.nickname = "我";
        when(userMapper.selectList(any())).thenReturn(List.of(me));

        List<Map<String, Object>> members = controller.groupMembers(5L, session).data;

        assertEquals(1, members.size());
        assertEquals(20L, members.get(0).get("userId"));
        assertEquals("我", members.get(0).get("nickname"));
        assertEquals("OWNER", members.get(0).get("memberRole"));
    }
}
