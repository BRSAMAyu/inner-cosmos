package com.innercosmos.controller;

import com.innercosmos.common.Constants;
import com.innercosmos.entity.FriendRelation;
import com.innercosmos.entity.SlowLetter;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SocialConnectionControllerTest {
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

    @Test
    void readLetterReceiverCanInviteSenderWithoutReceivingTheirUserIdFromTheClient() {
        SlowLetter letter = letter(10L, 20L, "READ");
        when(letterMapper.selectById(7L)).thenReturn(letter);
        when(blockMapper.selectCount(any())).thenReturn(0L);
        when(friendMapper.selectOne(any())).thenReturn(null);

        controller.requestFromLetter(7L, session);

        ArgumentCaptor<FriendRelation> inserted = ArgumentCaptor.forClass(FriendRelation.class);
        verify(friendMapper).insert(inserted.capture());
        assertEquals(20L, inserted.getValue().requesterId);
        assertEquals(10L, inserted.getValue().addresseeId);
        assertEquals("PENDING", inserted.getValue().status);
        assertEquals("SLOW_LETTER:7", inserted.getValue().source);
    }

    @Test
    void senderCannotTurnTheirOwnLetterIntoAnUnsolicitedConnectionRequest() {
        session.setAttribute(Constants.SESSION_USER_KEY, 10L);
        when(letterMapper.selectById(7L)).thenReturn(letter(10L, 20L, "READ"));

        BusinessException error = assertThrows(BusinessException.class,
                () -> controller.requestFromLetter(7L, session));

        assertEquals("UNAUTHORIZED", error.code);
        verify(friendMapper, never()).insert(any(FriendRelation.class));
    }

    @Test
    void acceptingRequiresThePendingAddresseeAndLeavingRequiresAnAcceptedParty() {
        FriendRelation pending = relation(1L, 20L, 10L, "PENDING");
        when(friendMapper.selectById(1L)).thenReturn(pending);
        session.setAttribute(Constants.SESSION_USER_KEY, 10L);

        assertEquals("ACCEPTED", controller.accept(1L, session).data.status);

        pending.status = "ACCEPTED";
        assertEquals("WITHDRAWN", controller.leave(1L, session).data.status);
        verify(friendMapper, org.mockito.Mockito.times(2)).updateById(pending);
    }

    @Test
    void peopleDiscoveryFiltersSyntheticAccountsAtTheDatabaseBoundary() {
        when(userMapper.selectList(any())).thenReturn(java.util.List.of());

        controller.people(session);

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
}
