package com.innercosmos.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.innercosmos.common.ErrorCode;
import com.innercosmos.entity.BlockRelation;
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
import com.innercosmos.service.SocialService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SocialServiceImpl implements SocialService {
    private final UserMapper userMapper;
    private final FriendRelationMapper friendMapper;
    private final SocialGroupMapper groupMapper;
    private final SocialGroupMemberMapper memberMapper;
    private final SlowLetterMapper letterMapper;
    private final BlockRelationMapper blockMapper;

    public SocialServiceImpl(UserMapper userMapper,
                             FriendRelationMapper friendMapper,
                             SocialGroupMapper groupMapper,
                             SocialGroupMemberMapper memberMapper,
                             SlowLetterMapper letterMapper,
                             BlockRelationMapper blockMapper) {
        this.userMapper = userMapper;
        this.friendMapper = friendMapper;
        this.groupMapper = groupMapper;
        this.memberMapper = memberMapper;
        this.letterMapper = letterMapper;
        this.blockMapper = blockMapper;
    }

    @Override
    public List<Map<String, Object>> discoverPeople(Long userId) {
        List<User> users = userMapper.selectList(new QueryWrapper<User>()
                .ne("id", userId)
                .eq("status", "ACTIVE")
                .in("account_kind", "HUMAN", "SHOWCASE")
                .orderByDesc("last_login_at")
                .orderByDesc("id")
                .last("LIMIT 30"));
        return users.stream().map(u -> {
            Map<String, Object> item = new HashMap<>();
            item.put("id", u.id);
            item.put("username", u.username);
            item.put("nickname", u.nickname == null ? u.username : u.nickname);
            item.put("relationStatus", relationStatus(userId, u.id));
            return item;
        }).toList();
    }

    @Override
    public List<Map<String, Object>> listFriends(Long userId) {
        List<FriendRelation> rows = friendMapper.selectList(new QueryWrapper<FriendRelation>()
                .eq("status", "ACCEPTED")
                .and(q -> q.eq("requester_id", userId).or().eq("addressee_id", userId))
                .orderByDesc("updated_at"));
        return rows.stream().map(r -> friendView(userId, r)).toList();
    }

    @Override
    public Map<String, Object> listFriendRequests(Long userId) {
        List<FriendRelation> incoming = friendMapper.selectList(new QueryWrapper<FriendRelation>()
                .eq("addressee_id", userId).eq("status", "PENDING").orderByDesc("id"));
        List<FriendRelation> outgoing = friendMapper.selectList(new QueryWrapper<FriendRelation>()
                .eq("requester_id", userId).eq("status", "PENDING").orderByDesc("id"));
        return Map.of(
                "incoming", incoming.stream().map(r -> friendView(userId, r)).toList(),
                "outgoing", outgoing.stream().map(r -> friendView(userId, r)).toList()
        );
    }

    @Override
    public FriendRelation requestFriend(Long userId, Long targetUserId, String source) {
        if (userId.equals(targetUserId)) throw new BusinessException(ErrorCode.BAD_REQUEST, "不能添加自己");
        return createOrResumeRequest(userId, targetUserId, source);
    }

    @Override
    public FriendRelation requestFriendFromLetter(Long userId, Long letterId) {
        SlowLetter letter = letterMapper.selectById(letterId);
        if (letter == null || !userId.equals(letter.receiverUserId)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "只有这封慢信的收件人可以发起连接");
        }
        if (!List.of("READ", "REPLIED").contains(letter.status)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请在慢信抵达并阅读后再决定是否认识对方");
        }
        if (isBlocked(userId, letter.senderUserId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "双方存在屏蔽关系，不能发起连接");
        }
        return createOrResumeRequest(userId, letter.senderUserId, "SLOW_LETTER:" + letterId);
    }

    private FriendRelation createOrResumeRequest(Long userId, Long targetUserId, String source) {
        FriendRelation relation = friendMapper.selectOne(new QueryWrapper<FriendRelation>()
                .and(q -> q.eq("requester_id", userId).eq("addressee_id", targetUserId)
                        .or()
                        .eq("requester_id", targetUserId).eq("addressee_id", userId))
                .last("LIMIT 1"));
        if (relation == null) {
            relation = new FriendRelation();
            relation.requesterId = userId;
            relation.addresseeId = targetUserId;
            relation.status = "PENDING";
            relation.source = source;
            friendMapper.insert(relation);
        } else if (List.of("DECLINED", "WITHDRAWN").contains(relation.status)) {
            relation.requesterId = userId;
            relation.addresseeId = targetUserId;
            relation.status = "PENDING";
            relation.source = source;
            friendMapper.updateById(relation);
        }
        return relation;
    }

    @Override
    public FriendRelation acceptFriendRequest(Long userId, Long relationId) {
        FriendRelation relation = friendMapper.selectById(relationId);
        if (relation == null || !userId.equals(relation.addresseeId) || !"PENDING".equals(relation.status)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "无权处理这条好友申请");
        }
        relation.status = "ACCEPTED";
        friendMapper.updateById(relation);
        return relation;
    }

    @Override
    public FriendRelation declineFriendRequest(Long userId, Long relationId) {
        FriendRelation relation = friendMapper.selectById(relationId);
        if (relation == null || !userId.equals(relation.addresseeId) || !"PENDING".equals(relation.status)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "无权处理这条好友申请");
        }
        relation.status = "DECLINED";
        friendMapper.updateById(relation);
        return relation;
    }

    @Override
    public FriendRelation leaveFriendRelation(Long userId, Long relationId) {
        FriendRelation relation = friendMapper.selectById(relationId);
        if (relation == null || !"ACCEPTED".equals(relation.status)
                || (!userId.equals(relation.requesterId) && !userId.equals(relation.addresseeId))) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "无权退出这段连接");
        }
        relation.status = "WITHDRAWN";
        friendMapper.updateById(relation);
        return relation;
    }

    @Override
    public List<SocialGroup> listGroups(Long userId) {
        List<Long> groupIds = memberMapper.selectList(new QueryWrapper<SocialGroupMember>()
                        .eq("user_id", userId).eq("status", "ACTIVE"))
                .stream().map(m -> m.groupId).toList();
        if (groupIds.isEmpty()) return List.of();
        return groupMapper.selectList(new QueryWrapper<SocialGroup>().in("id", groupIds).orderByDesc("id"));
    }

    // Regression (Gemini audit / remaining-work-handoff.md 2.2.4): the group row and its OWNER
    // membership row were two independent, unguarded inserts -- a failure between them (or a
    // request abort) could leave an ownerless group. @Transactional makes them succeed or fail
    // together.
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SocialGroup createGroup(Long userId, String name, String intro, String visibility) {
        String trimmedName = name == null ? "" : name.trim();
        if (trimmedName.isBlank()) throw new BusinessException(ErrorCode.BAD_REQUEST, "群组名不能为空");
        SocialGroup group = new SocialGroup();
        group.ownerUserId = userId;
        group.groupName = trimmedName;
        group.intro = intro == null ? "" : intro;
        group.visibility = visibility == null ? "PRIVATE" : visibility;
        groupMapper.insert(group);
        SocialGroupMember member = new SocialGroupMember();
        member.groupId = group.id;
        member.userId = userId;
        member.memberRole = "OWNER";
        member.status = "ACTIVE";
        memberMapper.insert(member);
        return group;
    }

    @Override
    public SocialGroupMember inviteToGroup(Long userId, Long groupId, Long targetUserId) {
        SocialGroupMember myMembership = memberMapper.selectOne(new QueryWrapper<SocialGroupMember>()
                .eq("group_id", groupId).eq("user_id", userId).eq("status", "ACTIVE"));
        if (myMembership == null) throw new BusinessException(ErrorCode.UNAUTHORIZED, "只有群组成员可以邀请他人");
        // Regression (Gemini audit / remaining-work-handoff.md 2.2.4): self-invite, block, and
        // non-friend invites were previously accepted -- friendMapper/blockMapper were already
        // injected and used elsewhere in this controller (relationStatus/isBlocked) but never
        // consulted here.
        if (userId.equals(targetUserId)) throw new BusinessException(ErrorCode.BAD_REQUEST, "不能邀请自己");
        if (isBlocked(userId, targetUserId)) throw new BusinessException(ErrorCode.FORBIDDEN, "双方存在屏蔽关系，不能邀请");
        if (!"ACCEPTED".equals(relationStatus(userId, targetUserId))) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "只能邀请已互相接受的好友加入群组");
        }
        if (userMapper.selectById(targetUserId) == null) throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        SocialGroupMember existing = memberMapper.selectOne(new QueryWrapper<SocialGroupMember>()
                .eq("group_id", groupId).eq("user_id", targetUserId));
        if (existing != null && !"DECLINED".equals(existing.status) && !"LEFT".equals(existing.status)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "对方已经在群组中或已有待处理邀请");
        }
        SocialGroupMember invite = existing != null ? existing : new SocialGroupMember();
        invite.groupId = groupId;
        invite.userId = targetUserId;
        invite.memberRole = "MEMBER";
        invite.status = "PENDING";
        if (existing != null) memberMapper.updateById(invite); else memberMapper.insert(invite);
        return invite;
    }

    @Override
    public List<Map<String, Object>> listGroupInvites(Long userId) {
        List<SocialGroupMember> pending = memberMapper.selectList(new QueryWrapper<SocialGroupMember>()
                .eq("user_id", userId).eq("status", "PENDING"));
        if (pending.isEmpty()) return List.of();
        List<Long> groupIds = pending.stream().map(m -> m.groupId).toList();
        Map<Long, SocialGroup> groups = groupMapper.selectList(new QueryWrapper<SocialGroup>().in("id", groupIds))
                .stream().collect(java.util.stream.Collectors.toMap(g -> g.id, g -> g));
        return pending.stream().map(m -> {
            Map<String, Object> row = new HashMap<>();
            row.put("memberId", m.id);
            row.put("groupId", m.groupId);
            SocialGroup group = groups.get(m.groupId);
            row.put("groupName", group == null ? "" : group.groupName);
            return row;
        }).toList();
    }

    private enum InviteDecision { ACCEPT, DECLINE }

    @Override
    public SocialGroupMember respondToGroupInvite(Long userId, Long memberId, String decision) {
        SocialGroupMember member = memberMapper.selectById(memberId);
        if (member == null || !userId.equals(member.userId)) throw new BusinessException(ErrorCode.UNAUTHORIZED, "无权操作此邀请");
        if (!"PENDING".equals(member.status)) throw new BusinessException(ErrorCode.BAD_REQUEST, "该邀请已被处理");
        // Regression (Gemini audit / remaining-work-handoff.md 2.2.4): `"accept".equals(decision)
        // ? ACTIVE : DECLINED` silently treated ANY non-"accept" value -- typos, null, empty -- as
        // a decline, instead of rejecting invalid input. Use an explicit enum of the two legal
        // decisions (matches the frontend's own "accept" | "decline" union in web/src/api.ts).
        InviteDecision parsed;
        try {
            parsed = InviteDecision.valueOf(String.valueOf(decision).toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "decision 必须是 accept 或 decline");
        }
        String newStatus = parsed == InviteDecision.ACCEPT ? "ACTIVE" : "DECLINED";
        // Regression: plain updateById() after a read-then-check is a race -- two concurrent
        // respond calls could both pass the "PENDING".equals(member.status) check above before
        // either writes. Use an atomic conditional UPDATE ... WHERE id=? AND status='PENDING',
        // matching the pattern in DialogServiceImpl#finishSession; 0 rows means someone else
        // already resolved this invite in the race window.
        int updated = memberMapper.update(null, new UpdateWrapper<SocialGroupMember>()
                .eq("id", memberId).eq("status", "PENDING")
                .set("status", newStatus));
        if (updated == 0) throw new BusinessException(ErrorCode.BAD_REQUEST, "该邀请已被处理");
        member.status = newStatus;
        return member;
    }

    @Override
    public void leaveGroup(Long userId, Long groupId) {
        SocialGroupMember member = memberMapper.selectOne(new QueryWrapper<SocialGroupMember>()
                .eq("group_id", groupId).eq("user_id", userId).eq("status", "ACTIVE"));
        if (member == null) throw new BusinessException(ErrorCode.NOT_FOUND, "你不在这个群组中");
        if ("OWNER".equals(member.memberRole)) throw new BusinessException(ErrorCode.BAD_REQUEST, "群主不能直接退出，请先转让或解散群组");
        member.status = "LEFT";
        memberMapper.updateById(member);
    }

    @Override
    public List<Map<String, Object>> listGroupMembers(Long userId, Long groupId) {
        long myCount = memberMapper.selectCount(new QueryWrapper<SocialGroupMember>()
                .eq("group_id", groupId).eq("user_id", userId).eq("status", "ACTIVE"));
        if (myCount == 0) throw new BusinessException(ErrorCode.UNAUTHORIZED, "无权查看此群组成员");
        List<SocialGroupMember> members = memberMapper.selectList(new QueryWrapper<SocialGroupMember>()
                .eq("group_id", groupId).eq("status", "ACTIVE"));
        List<Long> userIds = members.stream().map(m -> m.userId).toList();
        Map<Long, User> users = userIds.isEmpty() ? Map.of() : userMapper.selectList(new QueryWrapper<User>().in("id", userIds))
                .stream().collect(java.util.stream.Collectors.toMap(u -> u.id, u -> u));
        return members.stream().map(m -> {
            Map<String, Object> row = new HashMap<>();
            row.put("userId", m.userId);
            row.put("memberRole", m.memberRole);
            User user = users.get(m.userId);
            row.put("nickname", user == null ? "" : user.nickname);
            return row;
        }).toList();
    }

    private String relationStatus(Long userId, Long other) {
        FriendRelation relation = friendMapper.selectOne(new QueryWrapper<FriendRelation>()
                .and(q -> q.eq("requester_id", userId).eq("addressee_id", other)
                        .or()
                        .eq("requester_id", other).eq("addressee_id", userId))
                .last("LIMIT 1"));
        if (relation == null) return "NONE";
        if ("PENDING".equals(relation.status)) {
            return userId.equals(relation.requesterId) ? "PENDING_OUT" : "PENDING_IN";
        }
        return relation.status;
    }

    private boolean isBlocked(Long first, Long second) {
        Long count = blockMapper.selectCount(new QueryWrapper<BlockRelation>()
                .and(q -> q.eq("blocker_user_id", first).eq("blocked_user_id", second)
                        .or().eq("blocker_user_id", second).eq("blocked_user_id", first)));
        return count != null && count > 0;
    }

    private Map<String, Object> friendView(Long userId, FriendRelation relation) {
        Long otherId = userId.equals(relation.requesterId) ? relation.addresseeId : relation.requesterId;
        User other = userMapper.selectById(otherId);
        Map<String, Object> item = new HashMap<>();
        item.put("id", relation.id);
        item.put("status", relation.status);
        item.put("userId", otherId);
        item.put("nickname", other == null ? "未知用户" : (other.nickname == null ? other.username : other.nickname));
        item.put("username", other == null ? "" : other.username);
        item.put("source", relation.source);
        return item;
    }
}
