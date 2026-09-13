package com.innercosmos.service.impl;

import com.innercosmos.entity.FriendRelation;
import com.innercosmos.entity.GroupReviewLedger;
import com.innercosmos.entity.SlowLetter;
import com.innercosmos.entity.SocialGroup;
import com.innercosmos.entity.SocialGroupMember;
import com.innercosmos.entity.SocialGroupMessage;
import com.innercosmos.entity.User;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.mapper.BlockRelationMapper;
import com.innercosmos.mapper.FriendRelationMapper;
import com.innercosmos.mapper.GroupReviewLedgerMapper;
import com.innercosmos.mapper.SlowLetterMapper;
import com.innercosmos.mapper.SocialGroupMapper;
import com.innercosmos.mapper.SocialGroupMemberMapper;
import com.innercosmos.mapper.SocialGroupMessageMapper;
import com.innercosmos.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.Duration;
import java.time.Instant;
import java.time.Clock;
import java.time.ZoneId;
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
    @Mock SocialGroupMessageMapper messageMapper;
    @Mock SlowLetterMapper letterMapper;
    @Mock BlockRelationMapper blockMapper;
    @Mock GroupReviewLedgerMapper reviewLedgerMapper;

    /** Deterministic, pinnable time source for CP-35 mute-expiry tests. */
    private static final class MutableClock extends Clock {
        private Instant instant;
        MutableClock(Instant start) { this.instant = start; }
        void advance(Duration duration) { instant = instant.plus(duration); }
        @Override public ZoneId getZone() { return ZoneId.systemDefault(); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }

    private MutableClock clock;
    private SocialServiceImpl service;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-09-13T10:00:00Z"));
        service = new SocialServiceImpl(userMapper, friendMapper, groupMapper, memberMapper,
                messageMapper, letterMapper, blockMapper, reviewLedgerMapper, clock, 20);
    }

    private SocialGroup activeGroup(Long id, Long ownerId) {
        SocialGroup group = new SocialGroup();
        group.id = id;
        group.ownerUserId = ownerId;
        group.groupName = "群";
        group.status = "ACTIVE";
        return group;
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
        assertTrue(sql.contains("account_kind"), sql);
        assertTrue(query.getValue().getParamNameValuePairs().containsValue("HUMAN"));
        assertFalse(query.getValue().getParamNameValuePairs().containsValue("SHOWCASE"));
        assertTrue(sql.contains("last_login_at"), sql);
        assertTrue(sql.contains("limit 60"), sql);
    }

    @Test
    void exactPeopleLookupUsesBoundCaseInsensitiveUsernameOrNicknameAndStaysHumanOnly() {
        when(userMapper.selectList(any())).thenReturn(List.of());

        service.discoverPeople(20L, "seat-b17");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<User>> query =
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.query.QueryWrapper.class);
        verify(userMapper).selectList(query.capture());
        String sql = query.getValue().getTargetSql().toLowerCase(java.util.Locale.ROOT);
        assertTrue(sql.contains("lower(username) = lower"), sql);
        assertTrue(sql.contains("lower(nickname) = lower"), sql);
        assertTrue(sql.contains("limit 10"), sql);
        assertTrue(query.getValue().getParamNameValuePairs().containsValue("seat-b17"));
        assertTrue(query.getValue().getParamNameValuePairs().containsValue("HUMAN"));
    }

    @Test
    void concurrentReverseFriendRequestReturnsTheDatabaseWinner() {
        FriendRelation winner = relation(9L, 10L, 20L, "PENDING");
        when(friendMapper.selectOne(any())).thenReturn(null, winner);
        when(friendMapper.insert(any(FriendRelation.class)))
                .thenThrow(new DuplicateKeyException("unordered pair already exists"));

        FriendRelation result = service.requestFriend(20L, 10L, "SOCIAL_PAGE");

        assertSame(winner, result);
        verify(friendMapper, times(2)).selectOne(any());
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
        when(groupMapper.selectById(5L)).thenReturn(activeGroup(5L, 20L));
        when(memberMapper.selectCount(any())).thenReturn(0L);

        BusinessException error = assertThrows(BusinessException.class, () -> service.listGroupMembers(20L, 5L));

        assertEquals("UNAUTHORIZED", error.code);
    }

    @Test
    void listsActiveMembersWithNicknames() {
        when(groupMapper.selectById(5L)).thenReturn(activeGroup(5L, 20L));
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

    @Test
    void onlyAnActiveMemberCanReadOrSendGroupMessages() {
        when(groupMapper.selectById(5L)).thenReturn(activeGroup(5L, 20L));
        when(memberMapper.selectOne(any())).thenReturn(null); // caller not an active member

        BusinessException readError = assertThrows(BusinessException.class,
                () -> service.listGroupMessages(20L, 5L));
        BusinessException sendError = assertThrows(BusinessException.class,
                () -> service.sendGroupMessage(20L, 5L, "hello"));

        assertEquals("UNAUTHORIZED", readError.code);
        assertEquals("UNAUTHORIZED", sendError.code);
        verifyNoInteractions(messageMapper);
    }

    @Test
    void activeMemberCanSendTrimmedMessageAndReadChronologicalConversation() {
        when(groupMapper.selectById(5L)).thenReturn(activeGroup(5L, 20L));
        when(memberMapper.selectOne(any())).thenReturn(member(1L, 5L, 20L, "OWNER", "ACTIVE"));
        User me = new User(); me.id = 20L; me.nickname = "我";
        when(userMapper.selectById(20L)).thenReturn(me);
        doAnswer(invocation -> {
            SocialGroupMessage inserted = invocation.getArgument(0);
            inserted.id = 9L;
            return 1;
        }).when(messageMapper).insert(any(SocialGroupMessage.class));

        Map<String, Object> sent = service.sendGroupMessage(20L, 5L, "  今晚一起复盘吗？  ");

        ArgumentCaptor<SocialGroupMessage> inserted = ArgumentCaptor.forClass(SocialGroupMessage.class);
        verify(messageMapper).insert(inserted.capture());
        assertEquals("今晚一起复盘吗？", inserted.getValue().messageBody);
        assertEquals("我", sent.get("senderNickname"));

        SocialGroupMessage newer = new SocialGroupMessage();
        newer.id = 2L; newer.groupId = 5L; newer.senderUserId = 20L; newer.messageBody = "第二条";
        SocialGroupMessage older = new SocialGroupMessage();
        older.id = 1L; older.groupId = 5L; older.senderUserId = 20L; older.messageBody = "第一条";
        when(messageMapper.selectList(any())).thenReturn(List.of(newer, older));

        List<Map<String, Object>> messages = service.listGroupMessages(20L, 5L);

        assertEquals(List.of("第一条", "第二条"),
                messages.stream().map(row -> row.get("messageBody")).toList());
    }

    @Test
    void blankGroupMessageIsRejected() {
        when(groupMapper.selectById(5L)).thenReturn(activeGroup(5L, 20L));
        when(memberMapper.selectOne(any())).thenReturn(member(1L, 5L, 20L, "MEMBER", "ACTIVE"));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.sendGroupMessage(20L, 5L, "   "));

        assertEquals("BAD_REQUEST", error.code);
        verifyNoInteractions(messageMapper);
    }

    // -- CP-35 governance: mute ------------------------------------------------

    @Test
    void hostCanMuteAMemberForBoundedMinutesAndTheWindowIsStored() {
        when(groupMapper.selectById(5L)).thenReturn(activeGroup(5L, 20L));
        when(memberMapper.selectOne(any()))
                .thenReturn(member(2L, 5L, 30L, "MEMBER", "ACTIVE")); // mute target
        java.time.LocalDateTime expectedUntil = java.time.LocalDateTime.now(clock).plusMinutes(10);

        service.muteGroupMember(20L, 5L, 30L, 10);

        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<SocialGroupMember>> captor =
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper.class);
        verify(memberMapper).update(any(), captor.capture());
        String sqlSet = captor.getValue().getSqlSet().toLowerCase(java.util.Locale.ROOT);
        assertTrue(sqlSet.contains("muted_at"), sqlSet);
        assertTrue(sqlSet.contains("muted_until"), sqlSet);
        assertTrue(sqlSet.contains("muted_by"), sqlSet);
        // muted_until = now(clock) + 10min -- zone-independent because the clock is pinned.
        assertTrue(captor.getValue().getParamNameValuePairs().containsValue(expectedUntil),
                String.valueOf(captor.getValue().getParamNameValuePairs()));
    }

    @Test
    void hostCanMuteAMemberUntilManuallyReleased() {
        when(groupMapper.selectById(5L)).thenReturn(activeGroup(5L, 20L));
        when(memberMapper.selectOne(any())).thenReturn(member(2L, 5L, 30L, "MEMBER", "ACTIVE"));

        service.muteGroupMember(20L, 5L, 30L, null);

        verify(memberMapper).update(any(), any());
    }

    @Test
    void nonHostCannotMuteAnyone() {
        when(groupMapper.selectById(5L)).thenReturn(activeGroup(5L, 20L)); // owner is 20, caller 30

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.muteGroupMember(30L, 5L, 40L, 10));

        assertEquals("FORBIDDEN", error.code);
        verify(memberMapper, never()).update(any(), any());
    }

    @Test
    void hostCannotMuteSelfOrTheOwnerOrANonMemberAndRejectsNonPositiveDurations() {
        when(groupMapper.selectById(5L)).thenReturn(activeGroup(5L, 20L));

        assertEquals("BAD_REQUEST", assertThrows(BusinessException.class,
                () -> service.muteGroupMember(20L, 5L, 20L, 10)).code); // self

        when(memberMapper.selectOne(any())).thenReturn(member(2L, 5L, 30L, "OWNER", "ACTIVE"));
        assertEquals("BAD_REQUEST", assertThrows(BusinessException.class,
                () -> service.muteGroupMember(20L, 5L, 30L, 10)).code); // the owner target

        when(memberMapper.selectOne(any())).thenReturn(null);
        assertEquals("NOT_FOUND", assertThrows(BusinessException.class,
                () -> service.muteGroupMember(20L, 5L, 30L, 10)).code); // not a member

        // Duration validation happens before the target lookup, so this row stub is only
        // needed if the order ever flips back -- lenient, not a contract.
        lenient().when(memberMapper.selectOne(any())).thenReturn(member(2L, 5L, 30L, "MEMBER", "ACTIVE"));
        assertEquals("BAD_REQUEST", assertThrows(BusinessException.class,
                () -> service.muteGroupMember(20L, 5L, 30L, 0)).code);
        assertEquals("BAD_REQUEST", assertThrows(BusinessException.class,
                () -> service.muteGroupMember(20L, 5L, 30L, -5)).code);
        verify(memberMapper, never()).update(any(), any());
    }

    @Test
    void hostCanLiftAMuteManually() {
        when(groupMapper.selectById(5L)).thenReturn(activeGroup(5L, 20L));
        when(memberMapper.selectOne(any())).thenReturn(member(2L, 5L, 30L, "MEMBER", "ACTIVE"));

        service.unmuteGroupMember(20L, 5L, 30L);

        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<SocialGroupMember>> captor =
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper.class);
        verify(memberMapper).update(any(), captor.capture());
        assertTrue(captor.getValue().getSqlSet().toLowerCase(java.util.Locale.ROOT).contains("muted_at"));
    }

    @Test
    void mutedMemberSendingIsRefusedLoudlyWithTheRemainingMinutesAndNothingIsPersisted() {
        when(groupMapper.selectById(5L)).thenReturn(activeGroup(5L, 20L));
        SocialGroupMember muted = member(2L, 5L, 30L, "MEMBER", "ACTIVE");
        muted.mutedAt = java.time.LocalDateTime.now(clock);
        muted.mutedUntil = java.time.LocalDateTime.now(clock).plusMinutes(30);
        when(memberMapper.selectOne(any())).thenReturn(muted);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.sendGroupMessage(30L, 5L, "还想说话"));

        assertEquals("FORBIDDEN", error.code);
        assertTrue(error.getMessage().contains("剩余"), error.getMessage());
        assertTrue(error.getMessage().contains("30"), error.getMessage());
        verifyNoInteractions(messageMapper); // not swallowed: plainly refused, nothing stored
    }

    @Test
    void indefiniteMuteNamesTheManualReleaseRequirement() {
        when(groupMapper.selectById(5L)).thenReturn(activeGroup(5L, 20L));
        SocialGroupMember muted = member(2L, 5L, 30L, "MEMBER", "ACTIVE");
        muted.mutedAt = java.time.LocalDateTime.now(clock);
        muted.mutedUntil = null;
        when(memberMapper.selectOne(any())).thenReturn(muted);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.sendGroupMessage(30L, 5L, "还想说话"));

        assertEquals("FORBIDDEN", error.code);
        assertTrue(error.getMessage().contains("手动解除"), error.getMessage());
        verifyNoInteractions(messageMapper);
    }

    @Test
    void expiredMuteSelfHealsAndSpeechResumesWithoutAJob() {
        when(groupMapper.selectById(5L)).thenReturn(activeGroup(5L, 20L));
        SocialGroupMember muted = member(2L, 5L, 30L, "MEMBER", "ACTIVE");
        muted.mutedAt = java.time.LocalDateTime.now(clock).minusMinutes(10);
        muted.mutedUntil = java.time.LocalDateTime.now(clock).minusMinutes(1); // already expired
        when(memberMapper.selectOne(any())).thenReturn(muted);
        User sender = new User(); sender.id = 30L; sender.nickname = "被禁言过的人";
        when(userMapper.selectById(30L)).thenReturn(sender);
        doAnswer(invocation -> {
            SocialGroupMessage inserted = invocation.getArgument(0);
            inserted.id = 9L;
            return 1;
        }).when(messageMapper).insert(any(SocialGroupMessage.class));

        Map<String, Object> sent = service.sendGroupMessage(30L, 5L, "禁言期满，我回来了");

        assertEquals("禁言期满，我回来了", sent.get("messageBody"));
        verify(memberMapper).update(any(), any()); // the lazy clear of the stale mute row
        verify(messageMapper).insert(any(SocialGroupMessage.class));
    }

    // -- CP-35 governance: ownership transfer ----------------------------------

    @Test
    void transferPromotesTheTargetAndDemotesTheOldOwnerAtomically() {
        SocialGroup group = activeGroup(5L, 20L);
        when(groupMapper.selectById(5L)).thenReturn(group);
        when(memberMapper.selectOne(any())).thenReturn(member(2L, 5L, 30L, "MEMBER", "ACTIVE"));
        when(memberMapper.update(any(), any())).thenReturn(1);
        doAnswer(invocation -> {
            SocialGroup updated = invocation.getArgument(0);
            assertEquals(30L, updated.ownerUserId);
            return 1;
        }).when(groupMapper).updateById(any(SocialGroup.class));

        service.transferGroupOwnership(20L, 5L, 30L);

        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<SocialGroupMember>> captor =
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper.class);
        verify(memberMapper, times(2)).update(any(), captor.capture());
        List<com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<SocialGroupMember>> updates = captor.getAllValues();
        // Promote first (role -> OWNER, mute lifted), then demote the old owner (role -> MEMBER).
        assertTrue(updates.get(0).getParamNameValuePairs().containsValue("OWNER"),
                String.valueOf(updates.get(0).getParamNameValuePairs()));
        assertTrue(updates.get(0).getSqlSet().toLowerCase(java.util.Locale.ROOT).contains("muted_at"));
        assertTrue(updates.get(1).getParamNameValuePairs().containsValue("MEMBER"),
                String.valueOf(updates.get(1).getParamNameValuePairs()));
        verify(groupMapper).updateById(any(SocialGroup.class));
    }

    @Test
    void nonOwnerCannotTransferOwnership() {
        when(groupMapper.selectById(5L)).thenReturn(activeGroup(5L, 20L));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.transferGroupOwnership(30L, 5L, 40L));

        assertEquals("FORBIDDEN", error.code);
        verify(memberMapper, never()).update(any(), any());
        verify(groupMapper, never()).updateById(any(SocialGroup.class));
    }

    @Test
    void transferToSelfOrANonMemberIsRefused() {
        when(groupMapper.selectById(5L)).thenReturn(activeGroup(5L, 20L));

        assertEquals("BAD_REQUEST", assertThrows(BusinessException.class,
                () -> service.transferGroupOwnership(20L, 5L, 20L)).code);

        when(memberMapper.selectOne(any())).thenReturn(null);
        assertEquals("BAD_REQUEST", assertThrows(BusinessException.class,
                () -> service.transferGroupOwnership(20L, 5L, 30L)).code);
        verify(memberMapper, never()).update(any(), any());
    }

    @Test
    void losingTheConcurrentTransferRaceIsRejectedNotHalfApplied() {
        when(groupMapper.selectById(5L)).thenReturn(activeGroup(5L, 20L));
        when(memberMapper.selectOne(any())).thenReturn(member(2L, 5L, 30L, "MEMBER", "ACTIVE"));
        when(memberMapper.update(any(), any())).thenReturn(0); // the promote UPDATE lost the race

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.transferGroupOwnership(20L, 5L, 30L));

        assertEquals("CONFLICT", error.code);
        verify(groupMapper, never()).updateById(any(SocialGroup.class));
    }

    // -- CP-35 governance: dissolve --------------------------------------------

    @Test
    void ownerDissolvingMarksTheGroupAndRemovesEveryMembershipRow() {
        SocialGroup group = activeGroup(5L, 20L);
        when(groupMapper.selectById(5L)).thenReturn(group);
        when(memberMapper.update(any(), any())).thenReturn(3);

        service.dissolveGroup(20L, 5L);

        assertEquals("DISSOLVED", group.status);
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<SocialGroupMember>> captor =
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper.class);
        verify(memberMapper).update(any(), captor.capture());
        assertTrue(captor.getValue().getParamNameValuePairs().containsValue("REMOVED"),
                String.valueOf(captor.getValue().getParamNameValuePairs()));
    }

    @Test
    void nonOwnerCannotDissolveAndADissolvedGroupRefusesEverything() {
        when(groupMapper.selectById(5L)).thenReturn(activeGroup(5L, 20L));
        assertEquals("FORBIDDEN", assertThrows(BusinessException.class,
                () -> service.dissolveGroup(30L, 5L)).code);

        SocialGroup dissolved = activeGroup(5L, 20L);
        dissolved.status = "DISSOLVED";
        when(groupMapper.selectById(5L)).thenReturn(dissolved);
        assertEquals("CONFLICT", assertThrows(BusinessException.class,
                () -> service.dissolveGroup(20L, 5L)).code); // re-dissolve is explicit, not silent
        BusinessException send = assertThrows(BusinessException.class,
                () -> service.sendGroupMessage(20L, 5L, "还想说话"));
        assertEquals("CONFLICT", send.code);
        assertTrue(send.getMessage().contains("群已解散"), send.getMessage());
        BusinessException read = assertThrows(BusinessException.class,
                () -> service.listGroupMessages(20L, 5L));
        assertEquals("CONFLICT", read.code);
        assertTrue(read.getMessage().contains("群已解散"), read.getMessage());
        verifyNoInteractions(messageMapper);
        verify(memberMapper, never()).update(any(), any());
    }

    @Test
    void listGroupsNeverSurfacesADissolvedGroup() {
        when(memberMapper.selectList(any())).thenReturn(List.of(member(1L, 5L, 20L, "MEMBER", "ACTIVE")));

        service.listGroups(20L);

        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SocialGroup>> captor =
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.query.QueryWrapper.class);
        verify(groupMapper).selectList(captor.capture());
        String sql = captor.getValue().getTargetSql().toLowerCase(java.util.Locale.ROOT);
        assertTrue(sql.contains("status"), sql);
        assertTrue(captor.getValue().getParamNameValuePairs().containsValue("ACTIVE"));
    }

    // -- CP-35 governance: history visibility -----------------------------------

    @Test
    void ordinaryMembersOnlySeeMessagesFromTheirJoinInstantOnward() {
        when(groupMapper.selectById(5L)).thenReturn(activeGroup(5L, 20L));
        SocialGroupMember lateJoiner = member(2L, 5L, 30L, "MEMBER", "ACTIVE");
        lateJoiner.joinedAt = java.time.LocalDateTime.of(2026, 9, 13, 9, 30);
        when(memberMapper.selectOne(any())).thenReturn(lateJoiner);
        when(messageMapper.selectList(any())).thenReturn(List.of());
        when(blockMapper.selectList(any())).thenReturn(List.of());

        service.listGroupMessages(30L, 5L);

        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SocialGroupMessage>> captor =
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.query.QueryWrapper.class);
        verify(messageMapper).selectList(captor.capture());
        String sql = captor.getValue().getTargetSql().toLowerCase(java.util.Locale.ROOT);
        assertTrue(sql.contains("created_at >="), sql); // the join instant bounds the window
        assertTrue(captor.getValue().getParamNameValuePairs().containsValue(lateJoiner.joinedAt));
    }

    @Test
    void theHostKeepsTheFullHistoryRegardlessOfJoinInstant() {
        when(groupMapper.selectById(5L)).thenReturn(activeGroup(5L, 20L));
        SocialGroupMember host = member(1L, 5L, 20L, "OWNER", "ACTIVE");
        host.joinedAt = java.time.LocalDateTime.of(2026, 9, 13, 9, 30);
        when(memberMapper.selectOne(any())).thenReturn(host);
        when(messageMapper.selectList(any())).thenReturn(List.of());
        when(blockMapper.selectList(any())).thenReturn(List.of());

        service.listGroupMessages(20L, 5L);

        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SocialGroupMessage>> captor =
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.query.QueryWrapper.class);
        verify(messageMapper).selectList(captor.capture());
        assertFalse(captor.getValue().getTargetSql().toLowerCase(java.util.Locale.ROOT).contains("created_at >="));
    }

    // -- CP-35 governance: review capacity ledger -------------------------------

    @Test
    void reportPersistsAPendingLedgerRowWhileUnderTheCapacityBound() {
        when(groupMapper.selectById(5L)).thenReturn(activeGroup(5L, 20L));
        when(memberMapper.selectOne(any())).thenReturn(member(2L, 5L, 30L, "MEMBER", "ACTIVE"));
        SocialGroupMessage reported = new SocialGroupMessage();
        reported.id = 9L; reported.groupId = 5L; reported.senderUserId = 40L; reported.messageBody = "不当言论";
        when(messageMapper.selectById(9L)).thenReturn(reported);
        when(reviewLedgerMapper.selectCount(any())).thenReturn(0L, 0L, 1L);
        doAnswer(invocation -> {
            GroupReviewLedger inserted = invocation.getArgument(0);
            inserted.id = 1L;
            return 1;
        }).when(reviewLedgerMapper).insert(any(GroupReviewLedger.class));

        Map<String, Object> view = service.reportGroupMessage(30L, 5L, 9L, "言语攻击");

        assertEquals("PENDING", view.get("status"));
        assertEquals(40L, view.get("targetUserId"));
        ArgumentCaptor<GroupReviewLedger> inserted = ArgumentCaptor.forClass(GroupReviewLedger.class);
        verify(reviewLedgerMapper).insert(inserted.capture());
        assertEquals("PENDING", inserted.getValue().status);
        assertEquals(9L, inserted.getValue().targetMessageId);
    }

    @Test
    void overCapacityTheNewReportIsExplicitlyRejectedAndNothingIsPersisted() {
        when(groupMapper.selectById(5L)).thenReturn(activeGroup(5L, 20L));
        when(memberMapper.selectOne(any())).thenReturn(member(2L, 5L, 30L, "MEMBER", "ACTIVE"));
        SocialGroupMessage reported = new SocialGroupMessage();
        reported.id = 9L; reported.groupId = 5L; reported.senderUserId = 40L; reported.messageBody = "x";
        when(messageMapper.selectById(9L)).thenReturn(reported);
        when(reviewLedgerMapper.selectCount(any())).thenReturn(0L).thenReturn(20L); // dedup 0, pending at cap

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.reportGroupMessage(30L, 5L, 9L, "言语攻击"));

        assertEquals("CONFLICT", error.code);
        assertTrue(error.getMessage().contains("上限"), error.getMessage());
        verify(reviewLedgerMapper, never()).insert(any(GroupReviewLedger.class));
    }

    @Test
    void duplicatePendingReportsOfTheSameMessageAndForeignMessagesAreRefused() {
        when(groupMapper.selectById(5L)).thenReturn(activeGroup(5L, 20L));
        when(memberMapper.selectOne(any())).thenReturn(member(2L, 5L, 30L, "MEMBER", "ACTIVE"));
        SocialGroupMessage reported = new SocialGroupMessage();
        reported.id = 9L; reported.groupId = 5L; reported.senderUserId = 40L; reported.messageBody = "x";
        when(messageMapper.selectById(9L)).thenReturn(reported);
        when(reviewLedgerMapper.selectCount(any())).thenReturn(1L); // same reporter, same message, PENDING

        assertEquals("BAD_REQUEST", assertThrows(BusinessException.class,
                () -> service.reportGroupMessage(30L, 5L, 9L, "再报一次")).code);

        SocialGroupMessage foreign = new SocialGroupMessage();
        foreign.id = 10L; foreign.groupId = 6L; foreign.senderUserId = 40L; foreign.messageBody = "y";
        when(messageMapper.selectById(10L)).thenReturn(foreign);
        // A foreign message is refused at the lookup, before the dedup count is consulted.
        lenient().when(reviewLedgerMapper.selectCount(any())).thenReturn(0L);
        assertEquals("NOT_FOUND", assertThrows(BusinessException.class,
                () -> service.reportGroupMessage(30L, 5L, 10L, "别的群的消息")).code);
        verify(reviewLedgerMapper, never()).insert(any(GroupReviewLedger.class));
    }

    @Test
    void hostResolvesAPendingReviewAndFreesTheSlotButOthersCannot() {
        when(groupMapper.selectById(5L)).thenReturn(activeGroup(5L, 20L));
        GroupReviewLedger pending = new GroupReviewLedger();
        pending.id = 3L; pending.groupId = 5L; pending.reporterUserId = 30L;
        pending.targetMessageId = 9L; pending.reason = "言语攻击"; pending.status = "PENDING";
        when(reviewLedgerMapper.selectById(3L)).thenReturn(pending);
        when(reviewLedgerMapper.update(any(), any())).thenReturn(1);

        Map<String, Object> view = service.resolveGroupReview(20L, 5L, 3L, "dismiss", "不构成违规");

        assertEquals("DISMISSED", view.get("status"));
        assertEquals(20L, view.get("resolvedBy"));

        pending.status = "RESOLVED";
        assertEquals("BAD_REQUEST", assertThrows(BusinessException.class,
                () -> service.resolveGroupReview(20L, 5L, 3L, "resolve", null)).code);

        when(groupMapper.selectById(5L)).thenReturn(activeGroup(5L, 99L)); // 99 is not the host
        assertEquals("FORBIDDEN", assertThrows(BusinessException.class,
                () -> service.resolveGroupReview(20L, 5L, 3L, "resolve", null)).code);
    }
}
