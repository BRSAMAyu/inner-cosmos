package com.innercosmos.service.impl;

import com.innercosmos.entity.FriendRelation;
import com.innercosmos.entity.SlowLetter;
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

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * G2.ARCH-MODULES: business-rule and owner-scope coverage for the mapper access SocialController
 * used to hold directly. Moved (not just renamed) from the old SocialConnectionControllerTest and
 * SocialGroupControllerTest, which now only cover the controller's own responsibility
 * (session/body -> service delegation).
 */
@ExtendWith(MockitoExtension.class)
class SocialServiceImplTest {
    @Mock UserMapper userMapper;
    @Mock FriendRelationMapper friendMapper;
    @Mock SocialGroupMapper groupMapper;
    @Mock SocialGroupMemberMapper memberMapper;
    @Mock SlowLetterMapper letterMapper;
    @Mock BlockRelationMapper blockMapper;

    private SocialServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SocialServiceImpl(userMapper, friendMapper, groupMapper, memberMapper, letterMapper, blockMapper);
    }

    private SlowLetter letter(Long sender, Long receiver, String status) {
        SlowLetter letter = new SlowLetter();
        letter.senderUserId = sender;
        letter.receiverUserId = receiver;
        letter.status = status;
        return letter;
    }

    private FriendRelation relation(Long id, Long requester, Long addressee, String status) {
        FriendRelation relation = new FriendRelation();
        relation.id = id;
        relation.requesterId = requester;
        relation.addresseeId = addressee;
        relation.status = status;
        return relation;
    }

    private SocialGroupMember member(Long id, Long groupId, Long userId, String role, String status) {
        SocialGroupMember m = new SocialGroupMember();
        m.id = id; m.groupId = groupId; m.userId = userId; m.memberRole = role; m.status = status;
        return m;
    }

    private FriendRelation accepted(Long requesterId, Long addresseeId) {
        FriendRelation r = new FriendRelation();
        r.requesterId = requesterId; r.addresseeId = addresseeId; r.status = "ACCEPTED";
        return r;
    }

    // -- friend requests --

    @Test
    void readLetterReceiverCanInviteSenderWithoutReceivingTheirUserIdFromTheClient() {
        SlowLetter letter = letter(10L, 20L, "READ");
        when(letterMapper.selectById(7L)).thenReturn(letter);
        when(blockMapper.selectCount(any())).thenReturn(0L);
        when(friendMapper.selectOne(any())).thenReturn(null);

        service.requestFriendFromLetter(20L, 7L);

        ArgumentCaptor<FriendRelation> inserted = ArgumentCaptor.forClass(FriendRelation.class);
        verify(friendMapper).insert(inserted.capture());
        assertEquals(20L, inserted.getValue().requesterId);
        assertEquals(10L, inserted.getValue().addresseeId);
        assertEquals("PENDING", inserted.getValue().status);
        assertEquals("SLOW_LETTER:7", inserted.getValue().source);
    }

    @Test
    void senderCannotTurnTheirOwnLetterIntoAnUnsolicitedConnectionRequest() {
        when(letterMapper.selectById(7L)).thenReturn(letter(10L, 20L, "READ"));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.requestFriendFromLetter(10L, 7L));

        assertEquals("UNAUTHORIZED", error.code);
        verify(friendMapper, never()).insert(any(FriendRelation.class));
    }

    @Test
    void cannotRequestYourself() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.requestFriend(20L, 20L, "SOCIAL_PAGE"));

        assertEquals("BAD_REQUEST", error.code);
        verify(friendMapper, never()).insert(any(FriendRelation.class));
    }

    @Test
    void acceptingRequiresThePendingAddresseeAndLeavingRequiresAnAcceptedParty() {
        FriendRelation pending = relation(1L, 20L, 10L, "PENDING");
        when(friendMapper.selectById(1L)).thenReturn(pending);

        assertEquals("ACCEPTED", service.acceptFriendRequest(10L, 1L).status);

        pending.status = "ACCEPTED";
        assertEquals("WITHDRAWN", service.leaveFriendRelation(10L, 1L).status);
        verify(friendMapper, times(2)).updateById(pending);
    }

    @Test
    void peopleDiscoveryFiltersSyntheticAccountsAtTheDatabaseBoundary() {
        when(userMapper.selectList(any())).thenReturn(List.of());

        service.discoverPeople(20L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<User>> query =
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.query.QueryWrapper.class);
        verify(userMapper).selectList(query.capture());
        String sql = query.getValue().getTargetSql().toLowerCase(java.util.Locale.ROOT);
        assertTrue(sql.contains("account_kind in"), sql);
        assertTrue(query.getValue().getParamNameValuePairs().containsValue("HUMAN"));
        assertTrue(query.getValue().getParamNameValuePairs().containsValue("SHOWCASE"));
        assertTrue(sql.contains("last_login_at"), sql);
    }

    // -- groups --

    @Test
    void groupNameCannotBeBlank() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.createGroup(20L, "   ", "", "PRIVATE"));

        assertEquals("BAD_REQUEST", error.code);
        verify(groupMapper, never()).insert(any(SocialGroup.class));
    }

    @Test
    void creatingAGroupAlsoInsertsTheOwnerMembership() {
        SocialGroup created = service.createGroup(20L, "老朋友们", "intro", "PRIVATE");

        assertEquals(20L, created.ownerUserId);
        assertEquals("老朋友们", created.groupName);
        ArgumentCaptor<SocialGroupMember> captor = ArgumentCaptor.forClass(SocialGroupMember.class);
        verify(memberMapper).insert(captor.capture());
        assertEquals(20L, captor.getValue().userId);
        assertEquals("OWNER", captor.getValue().memberRole);
        assertEquals("ACTIVE", captor.getValue().status);
    }

    @Test
    void onlyAnActiveMemberCanInviteSomeoneElseIntoTheGroup() {
        when(memberMapper.selectOne(any())).thenReturn(null); // caller not an active member

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.inviteToGroup(20L, 5L, 30L));

        assertEquals("UNAUTHORIZED", error.code);
        verify(memberMapper, never()).insert(any(SocialGroupMember.class));
    }

    @Test
    void cannotInviteYourself() {
        when(memberMapper.selectOne(any())).thenReturn(member(1L, 5L, 20L, "OWNER", "ACTIVE"));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.inviteToGroup(20L, 5L, 20L));

        assertEquals("BAD_REQUEST", error.code);
        verify(memberMapper, never()).insert(any(SocialGroupMember.class));
    }

    @Test
    void cannotInviteABlockedUser() {
        when(memberMapper.selectOne(any())).thenReturn(member(1L, 5L, 20L, "OWNER", "ACTIVE"));
        when(blockMapper.selectCount(any())).thenReturn(1L);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.inviteToGroup(20L, 5L, 30L));

        assertEquals("FORBIDDEN", error.code);
        verify(memberMapper, never()).insert(any(SocialGroupMember.class));
    }

    @Test
    void cannotInviteSomeoneWhoIsNotYetAnAcceptedFriend() {
        when(memberMapper.selectOne(any())).thenReturn(member(1L, 5L, 20L, "OWNER", "ACTIVE"));
        when(friendMapper.selectOne(any())).thenReturn(null); // no relation at all

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.inviteToGroup(20L, 5L, 30L));

        assertEquals("BAD_REQUEST", error.code);
        verify(memberMapper, never()).insert(any(SocialGroupMember.class));
    }

    @Test
    void invitingCreatesAPendingMembershipForTheTargetUser() {
        when(memberMapper.selectOne(any()))
                .thenReturn(member(1L, 5L, 20L, "OWNER", "ACTIVE")) // caller's own membership
                .thenReturn(null); // target has no existing row
        when(friendMapper.selectOne(any())).thenReturn(accepted(20L, 30L));
        when(userMapper.selectById(30L)).thenReturn(new User());

        service.inviteToGroup(20L, 5L, 30L);

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
        when(friendMapper.selectOne(any())).thenReturn(accepted(20L, 30L));
        when(userMapper.selectById(30L)).thenReturn(new User());

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.inviteToGroup(20L, 5L, 30L));

        assertEquals("BAD_REQUEST", error.code);
        verify(memberMapper, never()).insert(any(SocialGroupMember.class));
    }

    @Test
    void listsOnlyMyPendingInvitesWithTheGroupName() {
        when(memberMapper.selectList(any())).thenReturn(List.of(member(9L, 5L, 20L, "MEMBER", "PENDING")));
        SocialGroup group = new SocialGroup(); group.id = 5L; group.groupName = "老朋友们";
        when(groupMapper.selectList(any())).thenReturn(List.of(group));

        List<Map<String, Object>> invites = service.listGroupInvites(20L);

        assertEquals(1, invites.size());
        assertEquals(5L, invites.get(0).get("groupId"));
        assertEquals("老朋友们", invites.get(0).get("groupName"));
        assertEquals(9L, invites.get(0).get("memberId"));
    }

    @Test
    void acceptingAnInviteActivatesMembershipAndDecliningMarksItDeclined() {
        SocialGroupMember pending = member(9L, 5L, 20L, "MEMBER", "PENDING");
        when(memberMapper.selectById(9L)).thenReturn(pending);
        when(memberMapper.update(any(), any())).thenReturn(1);

        service.respondToGroupInvite(20L, 9L, "accept");
        assertEquals("ACTIVE", pending.status);

        pending.status = "PENDING";
        service.respondToGroupInvite(20L, 9L, "decline");
        assertEquals("DECLINED", pending.status);
    }

    @Test
    void cannotRespondToSomeoneElsesInvite() {
        SocialGroupMember pending = member(9L, 5L, 999L, "MEMBER", "PENDING");
        when(memberMapper.selectById(9L)).thenReturn(pending);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.respondToGroupInvite(20L, 9L, "accept"));

        assertEquals("UNAUTHORIZED", error.code);
    }

    @Test
    void respondingWithAnythingOtherThanAcceptOrDeclineIsRejectedInsteadOfSilentlyDeclining() {
        // Regression: the old code did `"accept".equals(decision) ? ACTIVE : DECLINED`, so a typo,
        // empty string, or missing key silently became a decline instead of a rejected request.
        SocialGroupMember pending = member(9L, 5L, 20L, "MEMBER", "PENDING");
        when(memberMapper.selectById(9L)).thenReturn(pending);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.respondToGroupInvite(20L, 9L, "accpet"));

        assertEquals("BAD_REQUEST", error.code);
        assertEquals("PENDING", pending.status); // untouched, not silently declined
        verify(memberMapper, never()).update(any(), any());
    }

    @Test
    void losingTheConcurrentRespondRaceIsRejectedNotSilentlyOverwritten() {
        // Regression: plain updateById() after a read-then-check "PENDING".equals() is a race --
        // two concurrent respond calls could both pass the check before either writes. The
        // conditional UPDATE ... WHERE status='PENDING' must report the loss (0 rows) instead of
        // pretending the second caller's decision won.
        SocialGroupMember pending = member(9L, 5L, 20L, "MEMBER", "PENDING");
        when(memberMapper.selectById(9L)).thenReturn(pending);
        when(memberMapper.update(any(), any())).thenReturn(0); // someone else's UPDATE won the race first

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.respondToGroupInvite(20L, 9L, "accept"));

        assertEquals("BAD_REQUEST", error.code);
    }

    @Test
    void leavingRemovesAnOrdinaryMemberButNotTheOwner() {
        SocialGroupMember mine = member(1L, 5L, 20L, "MEMBER", "ACTIVE");
        when(memberMapper.selectOne(any())).thenReturn(mine);

        service.leaveGroup(20L, 5L);

        assertEquals("LEFT", mine.status);
    }

    @Test
    void ownerCannotLeaveViaTheOrdinaryLeaveEndpoint() {
        when(memberMapper.selectOne(any())).thenReturn(member(1L, 5L, 20L, "OWNER", "ACTIVE"));

        BusinessException error = assertThrows(BusinessException.class, () -> service.leaveGroup(20L, 5L));

        assertEquals("BAD_REQUEST", error.code);
        verify(memberMapper, never()).updateById(any(SocialGroupMember.class));
    }

    @Test
    void onlyAnActiveMemberCanListGroupMembers() {
        when(memberMapper.selectCount(any())).thenReturn(0L);

        BusinessException error = assertThrows(BusinessException.class, () -> service.listGroupMembers(20L, 5L));

        assertEquals("UNAUTHORIZED", error.code);
    }

    @Test
    void listsActiveMembersWithNicknames() {
        when(memberMapper.selectCount(any())).thenReturn(1L);
        when(memberMapper.selectList(any())).thenReturn(List.of(member(1L, 5L, 20L, "OWNER", "ACTIVE")));
        User me = new User(); me.id = 20L; me.nickname = "我";
        when(userMapper.selectList(any())).thenReturn(List.of(me));

        List<Map<String, Object>> members = service.listGroupMembers(20L, 5L);

        assertEquals(1, members.size());
        assertEquals(20L, members.get(0).get("userId"));
        assertEquals("我", members.get(0).get("nickname"));
        assertEquals("OWNER", members.get(0).get("memberRole"));
    }
}
