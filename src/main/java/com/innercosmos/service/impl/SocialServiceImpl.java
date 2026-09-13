package com.innercosmos.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.innercosmos.common.ErrorCode;
import com.innercosmos.entity.BlockRelation;
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
import com.innercosmos.service.SocialService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.ArrayList;
import java.util.Collections;

@Service
public class SocialServiceImpl implements SocialService {
    /** tb_social_group.status values (V50). */
    private static final String GROUP_ACTIVE = "ACTIVE";
    private static final String GROUP_DISSOLVED = "DISSOLVED";
    private static final String ROLE_OWNER = "OWNER";

    private final UserMapper userMapper;
    private final FriendRelationMapper friendMapper;
    private final SocialGroupMapper groupMapper;
    private final SocialGroupMemberMapper memberMapper;
    private final SocialGroupMessageMapper messageMapper;
    private final SlowLetterMapper letterMapper;
    private final BlockRelationMapper blockMapper;
    private final GroupReviewLedgerMapper reviewLedgerMapper;
    /** CP-35: single injected instant source so mute expiry is testable (ClockConfig). */
    private final Clock clock;
    /** CP-35: per-group host pending-review bound, honest fail-closed once reached. */
    private final int groupReviewPendingCapacity;

    public SocialServiceImpl(UserMapper userMapper,
                             FriendRelationMapper friendMapper,
                             SocialGroupMapper groupMapper,
                             SocialGroupMemberMapper memberMapper,
                             SocialGroupMessageMapper messageMapper,
                             SlowLetterMapper letterMapper,
                             BlockRelationMapper blockMapper,
                             GroupReviewLedgerMapper reviewLedgerMapper,
                             Clock clock,
                             @Value("${inner-cosmos.social.group-review-pending-capacity:20}")
                             int groupReviewPendingCapacity) {
        this.userMapper = userMapper;
        this.friendMapper = friendMapper;
        this.groupMapper = groupMapper;
        this.memberMapper = memberMapper;
        this.messageMapper = messageMapper;
        this.letterMapper = letterMapper;
        this.blockMapper = blockMapper;
        this.reviewLedgerMapper = reviewLedgerMapper;
        this.clock = clock;
        this.groupReviewPendingCapacity = groupReviewPendingCapacity;
    }

    @Override
    public List<Map<String, Object>> discoverPeople(Long userId) {
        return discoverPeople(userId, null);
    }

    @Override
    public List<Map<String, Object>> discoverPeople(Long userId, String exactQuery) {
        String queryText = exactQuery == null ? "" : exactQuery.trim();
        if (queryText.length() > 120) return List.of();
        QueryWrapper<User> query = new QueryWrapper<User>()
                .ne("id", userId)
                .eq("status", "ACTIVE")
                .eq("account_kind", "HUMAN");
        // CP-59 拉黑全触达一致: discovery never surfaces either side of a block relation.
        Set<Long> blocked = blockedCounterpartIds(userId);
        if (!blocked.isEmpty()) {
            query.notIn("id", blocked);
        }
        if (!queryText.isBlank()) {
            query.and(q -> q.apply("LOWER(username) = LOWER({0})", queryText)
                    .or()
                    .apply("LOWER(nickname) = LOWER({0})", queryText))
                    .orderByDesc("last_login_at")
                    .orderByDesc("id")
                    .last("LIMIT 10");
        } else {
            query.orderByDesc("last_login_at")
                    .orderByDesc("id")
                    .last("LIMIT 60");
        }
        List<User> users = userMapper.selectList(query);
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
        // CP-34 unified blocking + re-approach cooldown, indistinguishable to the sender:
        // "对方暂不接受新的好友请求" covers both an existing block and a recent decline, so
        // the other party's exact choice (block vs decline) never leaks.
        if (isBlocked(userId, targetUserId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, GENERIC_NOT_ACCEPTING);
        }
        FriendRelation relation = findRelationBetween(userId, targetUserId);
        if (relation != null && "DECLINED".equals(relation.status)
                && relation.updatedAt != null
                && relation.updatedAt.isAfter(java.time.LocalDateTime.now().minusDays(30))) {
            throw new BusinessException(ErrorCode.FORBIDDEN, GENERIC_NOT_ACCEPTING);
        }
        if (relation == null) {
            relation = new FriendRelation();
            relation.requesterId = userId;
            relation.addresseeId = targetUserId;
            relation.status = "PENDING";
            relation.source = source;
            try {
                friendMapper.insert(relation);
            } catch (DuplicateKeyException concurrentPairRequest) {
                FriendRelation winner = findRelationBetween(userId, targetUserId);
                if (winner != null) return winner;
                throw new BusinessException(ErrorCode.CONFLICT,
                        "双方正在同时发起连接，请刷新后回应已有邀请");
            }
        } else if (List.of("DECLINED", "WITHDRAWN").contains(relation.status)) {
            relation.requesterId = userId;
            relation.addresseeId = targetUserId;
            relation.status = "PENDING";
            relation.source = source;
            friendMapper.updateById(relation);
        }
        return relation;
    }

    private FriendRelation findRelationBetween(Long firstUserId, Long secondUserId) {
        return friendMapper.selectOne(new QueryWrapper<FriendRelation>()
                .and(q -> q.eq("requester_id", firstUserId).eq("addressee_id", secondUserId)
                        .or()
                        .eq("requester_id", secondUserId).eq("addressee_id", firstUserId))
                .orderByDesc("id")
                .last("LIMIT 1"));
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
        // CP-35: dissolved groups never appear in a member's list -- belt-and-braces on top of
        // dissolveGroup flipping every membership row to REMOVED.
        return groupMapper.selectList(new QueryWrapper<SocialGroup>()
                .in("id", groupIds).eq("status", GROUP_ACTIVE).orderByDesc("id"));
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
        group.status = GROUP_ACTIVE;
        groupMapper.insert(group);
        SocialGroupMember member = new SocialGroupMember();
        member.groupId = group.id;
        member.userId = userId;
        member.memberRole = ROLE_OWNER;
        member.status = "ACTIVE";
        // CP-35: the owner's history window opens at creation (MybatisMetaObjectHandler's
        // LocalDateTime.now() source, not the UTC clock bean -- joinedAt is compared in SQL
        // against message.created_at filled by that same handler).
        member.joinedAt = LocalDateTime.now();
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
        // CP-35: a resurrected row (re-invite after LEFT/DECLINED) must not keep the previous
        // stay's join instant -- the new history window opens when THIS invitation is accepted.
        invite.joinedAt = null;
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
        // CP-35: invites carry a 7-day validity; an expired one flips to EXPIRED instead of
        // staying forever-pending, and the invited user is told plainly.
        if (member.createdAt != null
                && member.createdAt.isBefore(java.time.LocalDateTime.now().minusDays(7))) {
            memberMapper.update(null, new UpdateWrapper<SocialGroupMember>()
                    .eq("id", memberId).eq("status", "PENDING").set("status", "EXPIRED"));
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "这条邀请已过期（有效期 7 天）。如果对方仍然希望邀请你，需要重新发起。");
        }
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
        // CP-35: acceptance stamps joined_at -- the history-visibility boundary -- at the
        // accept instant (LocalDateTime.now(), the same source MybatisMetaObjectHandler uses
        // for message.created_at so the SQL comparison stays zone-consistent).
        UpdateWrapper<SocialGroupMember> acceptUpdate = new UpdateWrapper<SocialGroupMember>()
                .eq("id", memberId).eq("status", "PENDING")
                .set("status", newStatus);
        if (parsed == InviteDecision.ACCEPT) {
            acceptUpdate.set("joined_at", LocalDateTime.now());
        }
        int updated = memberMapper.update(null, acceptUpdate);
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
        requireUsableGroup(groupId);
        requireActiveGroupMember(userId, groupId);
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

    @Override
    public List<Map<String, Object>> listGroupMessages(Long userId, Long groupId) {
        requireUsableGroup(groupId);
        SocialGroupMember me = requireActiveMembership(userId, groupId);
        // CP-35 history visibility: the join instant is the boundary -- ordinary members see
        // only messages created at/after their own joined_at (paging and any future by-id read
        // path must go through this same query). The host (OWNER) is NOT restricted and keeps
        // the full history; this is the documented one-choice.
        QueryWrapper<SocialGroupMessage> query = new QueryWrapper<SocialGroupMessage>()
                .eq("group_id", groupId);
        if (!ROLE_OWNER.equals(me.memberRole)) {
            LocalDateTime since = me.joinedAt != null ? me.joinedAt : me.createdAt;
            if (since != null) {
                query.ge("created_at", since);
            }
        }
        List<SocialGroupMessage> latest = new ArrayList<>(messageMapper.selectList(
                query.orderByDesc("id").last("LIMIT 100")));
        // CP-59 拉黑全触达一致: messages from either side of a block relation are
        // hidden from the reader in shared groups, mirroring letters/plaza.
        Set<Long> hidden = blockedCounterpartIds(userId);
        if (!hidden.isEmpty()) {
            latest.removeIf(message -> message.senderUserId != null
                    && hidden.contains(message.senderUserId));
        }
        Collections.reverse(latest);
        return latest.stream().map(this::groupMessageView).toList();
    }

    @Override
    public Map<String, Object> sendGroupMessage(Long userId, Long groupId, String messageBody) {
        requireUsableGroup(groupId);
        SocialGroupMember me = requireActiveMembership(userId, groupId);
        rejectIfMuted(me);
        String trimmed = messageBody == null ? "" : messageBody.trim();
        if (trimmed.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "群聊消息不能为空");
        }
        if (trimmed.length() > 2000) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "群聊消息不能超过 2000 个字符");
        }
        SocialGroupMessage message = new SocialGroupMessage();
        message.groupId = groupId;
        message.senderUserId = userId;
        message.messageBody = trimmed;
        messageMapper.insert(message);
        return groupMessageView(message);
    }

    private void requireActiveGroupMember(Long userId, Long groupId) {
        long myCount = memberMapper.selectCount(new QueryWrapper<SocialGroupMember>()
                .eq("group_id", groupId).eq("user_id", userId).eq("status", "ACTIVE"));
        if (myCount == 0) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "只有群组成员可以查看和参与群聊");
        }
    }

    /** The caller's own ACTIVE membership row (CP-35 needs its role, join instant and mute state). */
    private SocialGroupMember requireActiveMembership(Long userId, Long groupId) {
        SocialGroupMember membership = memberMapper.selectOne(new QueryWrapper<SocialGroupMember>()
                .eq("group_id", groupId).eq("user_id", userId).eq("status", "ACTIVE")
                .last("LIMIT 1"));
        if (membership == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "只有群组成员可以查看和参与群聊");
        }
        return membership;
    }

    /**
     * CP-35: the group must exist and not be dissolved. Dissolved groups refuse with an
     * explicit 「群已解散」 semantic (409 CONFLICT) on every group endpoint that reaches this
     * check -- no zombie-readable data.
     */
    private SocialGroup requireUsableGroup(Long groupId) {
        SocialGroup group = groupMapper.selectById(groupId);
        if (group == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "群组不存在");
        }
        if (GROUP_DISSOLVED.equals(group.status)) {
            throw new BusinessException(ErrorCode.CONFLICT, "群已解散，无法查看或发言");
        }
        return group;
    }

    /** CP-35 host gate: the group owner is the 主持人. */
    private SocialGroup requireGroupHost(Long actorUserId, Long groupId) {
        SocialGroup group = requireUsableGroup(groupId);
        if (!actorUserId.equals(group.ownerUserId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只有群主（主持人）可以进行该操作");
        }
        return group;
    }

    /**
     * CP-35 mute gate, fail-loud: a muted sender gets an explicit FORBIDDEN naming the remaining
     * duration (or the manual-release requirement) and nothing is persisted -- the message is
     * never silently swallowed. An expired mute self-heals: the row is lazily cleared so
     * speaking resumes at expiry without any background job.
     */
    private void rejectIfMuted(SocialGroupMember membership) {
        if (membership.mutedAt == null) return;
        LocalDateTime now = LocalDateTime.now(clock);
        if (membership.mutedUntil != null && !membership.mutedUntil.isAfter(now)) {
            memberMapper.update(null, new UpdateWrapper<SocialGroupMember>()
                    .eq("id", membership.id).eq("user_id", membership.userId)
                    .set("muted_at", null).set("muted_until", null).set("muted_by", null));
            return;
        }
        if (membership.mutedUntil == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "你已被群内禁言，需群主手动解除后才能发言");
        }
        long remainingMinutes = Math.max(1, Duration.between(now, membership.mutedUntil).toMinutes());
        throw new BusinessException(ErrorCode.FORBIDDEN,
                "你已被群内禁言，剩余约 " + remainingMinutes + " 分钟");
    }

    private Map<String, Object> groupMessageView(SocialGroupMessage message) {
        User sender = userMapper.selectById(message.senderUserId);
        Map<String, Object> row = new HashMap<>();
        row.put("id", message.id);
        row.put("groupId", message.groupId);
        row.put("senderUserId", message.senderUserId);
        row.put("senderNickname", sender == null
                ? "未知用户"
                : (sender.nickname == null ? sender.username : sender.nickname));
        row.put("messageBody", message.messageBody);
        row.put("createdAt", message.createdAt);
        return row;
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

    /** CP-34: the sender-facing reason for both blocked and recently-declined re-approach. */
    private static final String GENERIC_NOT_ACCEPTING = "对方暂不接受新的好友请求";

    private Set<Long> blockedCounterpartIds(Long userId) {
        Set<Long> ids = new HashSet<>();
        for (BlockRelation relation : blockMapper.selectList(
                new QueryWrapper<BlockRelation>().eq("blocker_user_id", userId))) {
            ids.add(relation.blockedUserId);
        }
        for (BlockRelation relation : blockMapper.selectList(
                new QueryWrapper<BlockRelation>().eq("blocked_user_id", userId))) {
            ids.add(relation.blockerUserId);
        }
        return ids;
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

    // ------------------------------------------------------------------
    // CP-35 group governance (closing-checklist §2-7). 主持人 = 群主（OWNER）。
    // ------------------------------------------------------------------

    @Override
    public void muteGroupMember(Long actorUserId, Long groupId, Long targetUserId, Integer durationMinutes) {
        requireGroupHost(actorUserId, groupId);
        if (actorUserId.equals(targetUserId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不能禁言自己");
        }
        if (durationMinutes != null && durationMinutes <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "禁言时长必须是正整数分钟（不传表示需手动解除）");
        }
        SocialGroupMember target = memberMapper.selectOne(new QueryWrapper<SocialGroupMember>()
                .eq("group_id", groupId).eq("user_id", targetUserId).eq("status", "ACTIVE")
                .last("LIMIT 1"));
        if (target == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "该成员不在群组中，无法禁言");
        }
        if (ROLE_OWNER.equals(target.memberRole)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不能禁言群主");
        }
        // muted_until NULL = muted until the host manually lifts it; a fresh mute overwrites
        // any previous window (including an unexpired one).
        LocalDateTime now = LocalDateTime.now(clock);
        memberMapper.update(null, new UpdateWrapper<SocialGroupMember>()
                .eq("id", target.id).eq("status", "ACTIVE")
                .set("muted_at", now)
                .set("muted_until", durationMinutes == null ? null : now.plusMinutes(durationMinutes))
                .set("muted_by", actorUserId));
    }

    @Override
    public void unmuteGroupMember(Long actorUserId, Long groupId, Long targetUserId) {
        requireGroupHost(actorUserId, groupId);
        SocialGroupMember target = memberMapper.selectOne(new QueryWrapper<SocialGroupMember>()
                .eq("group_id", groupId).eq("user_id", targetUserId).eq("status", "ACTIVE")
                .last("LIMIT 1"));
        if (target == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "该成员不在群组中");
        }
        memberMapper.update(null, new UpdateWrapper<SocialGroupMember>()
                .eq("id", target.id).eq("user_id", targetUserId)
                .set("muted_at", null).set("muted_until", null).set("muted_by", null));
    }

    // Regression-safe by construction: the group row, the promotion and the demotion are three
    // conditional UPDATEs inside one transaction -- either the crown moves completely or not
    // at all, so no window exists with two (or zero) OWNER rows.
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void transferGroupOwnership(Long ownerUserId, Long groupId, Long targetUserId) {
        requireGroupHost(ownerUserId, groupId);
        if (ownerUserId.equals(targetUserId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不能把群主移交给自己");
        }
        SocialGroupMember target = memberMapper.selectOne(new QueryWrapper<SocialGroupMember>()
                .eq("group_id", groupId).eq("user_id", targetUserId).eq("status", "ACTIVE")
                .last("LIMIT 1"));
        if (target == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "移交对象必须是群内正式成员");
        }
        // Promote first (conditional on the target still being ACTIVE); promotion also lifts
        // any mute, keeping the "the owner can never be muted" invariant.
        int promoted = memberMapper.update(null, new UpdateWrapper<SocialGroupMember>()
                .eq("id", target.id).eq("status", "ACTIVE")
                .set("member_role", ROLE_OWNER)
                .set("muted_at", null).set("muted_until", null).set("muted_by", null));
        if (promoted == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "移交失败：对方已不是群内正式成员，请刷新后重试");
        }
        int demoted = memberMapper.update(null, new UpdateWrapper<SocialGroupMember>()
                .eq("group_id", groupId).eq("user_id", ownerUserId).eq("member_role", ROLE_OWNER)
                .eq("status", "ACTIVE")
                .set("member_role", "MEMBER"));
        if (demoted == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "群主身份已发生变化，请刷新后重试");
        }
        SocialGroup group = new SocialGroup();
        group.id = groupId;
        group.ownerUserId = targetUserId;
        groupMapper.updateById(group);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void dissolveGroup(Long ownerUserId, Long groupId) {
        SocialGroup group = requireUsableGroup(groupId); // re-dissolve answers 「群已解散」CONFLICT
        if (!ownerUserId.equals(group.ownerUserId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只有群主可以解散群组");
        }
        group.status = GROUP_DISSOLVED;
        groupMapper.updateById(group);
        // Flip every membership row (including still-PENDING invitations) to REMOVED so no
        // zombie membership survives: the group vanishes from every member's list and no
        // member-scoped endpoint can still resolve an active membership against it.
        memberMapper.update(null, new UpdateWrapper<SocialGroupMember>()
                .eq("group_id", groupId).in("status", "ACTIVE", "PENDING")
                .set("status", "REMOVED"));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> reportGroupMessage(Long reporterUserId, Long groupId, Long messageId, String reason) {
        requireUsableGroup(groupId);
        requireActiveMembership(reporterUserId, groupId); // a muted member may still report
        String trimmed = reason == null ? "" : reason.trim();
        if (trimmed.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "举报理由不能为空");
        }
        if (trimmed.length() > 400) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "举报理由不能超过 400 个字符");
        }
        SocialGroupMessage message = messageId == null ? null : messageMapper.selectById(messageId);
        if (message == null || !groupId.equals(message.groupId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "被举报的消息不存在或不在这个群组中");
        }
        Long duplicate = reviewLedgerMapper.selectCount(new QueryWrapper<GroupReviewLedger>()
                .eq("group_id", groupId).eq("reporter_user_id", reporterUserId)
                .eq("target_message_id", messageId).eq("status", "PENDING"));
        if (duplicate != null && duplicate > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "你已举报过这条消息，正在等待主持人处理");
        }
        // CP-35 capacity gate, fail-closed: over the bound the report is explicitly REJECTED
        // (honest CONFLICT naming the bound) and nothing is persisted -- never silently
        // queued, never silently dropped, no unbounded pile.
        long pending = pendingReviewCount(groupId);
        if (pending >= groupReviewPendingCapacity) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "该群待处理审核已达上限（" + groupReviewPendingCapacity
                            + " 条），暂不接受新的举报，请等待群主处理积压后重试");
        }
        GroupReviewLedger row = new GroupReviewLedger();
        row.groupId = groupId;
        row.reporterUserId = reporterUserId;
        row.targetUserId = message.senderUserId;
        row.targetMessageId = messageId;
        row.reason = trimmed;
        row.status = "PENDING";
        reviewLedgerMapper.insert(row);
        // Race re-check inside the same transaction: if a concurrent report squeezed past the
        // pre-check and pushed the queue over the bound, roll this insert back too.
        if (pendingReviewCount(groupId) > groupReviewPendingCapacity) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "该群待处理审核已达上限（" + groupReviewPendingCapacity
                            + " 条），本次举报已被回滚，请稍后重试");
        }
        return reviewView(row);
    }

    @Override
    public Map<String, Object> resolveGroupReview(Long hostUserId, Long groupId, Long reviewId,
                                                  String decision, String note) {
        requireGroupHost(hostUserId, groupId);
        GroupReviewLedger row = reviewLedgerMapper.selectById(reviewId);
        if (row == null || !groupId.equals(row.groupId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "审核记录不存在");
        }
        if (!"PENDING".equals(row.status)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该审核已处理完毕");
        }
        boolean resolve = "resolve".equalsIgnoreCase(decision);
        boolean dismiss = "dismiss".equalsIgnoreCase(decision);
        if (!resolve && !dismiss) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "decision 必须是 resolve 或 dismiss");
        }
        String trimmedNote = note == null ? "" : note.trim();
        if (trimmedNote.length() > 400) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "处理说明不能超过 400 个字符");
        }
        // Conditional UPDATE: losing a concurrent resolve/dismiss race reads as "already done".
        int updated = reviewLedgerMapper.update(null, new UpdateWrapper<GroupReviewLedger>()
                .eq("id", reviewId).eq("status", "PENDING")
                .set("status", resolve ? "RESOLVED" : "DISMISSED")
                .set("resolution_note", trimmedNote.isBlank() ? null : trimmedNote)
                .set("resolved_by", hostUserId)
                .set("resolved_at", LocalDateTime.now(clock)));
        if (updated == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该审核已处理完毕");
        }
        row.status = resolve ? "RESOLVED" : "DISMISSED";
        row.resolutionNote = trimmedNote.isBlank() ? null : trimmedNote;
        row.resolvedBy = hostUserId;
        return reviewView(row);
    }

    @Override
    public Map<String, Object> listGroupReviews(Long hostUserId, Long groupId) {
        requireGroupHost(hostUserId, groupId);
        List<GroupReviewLedger> rows = reviewLedgerMapper.selectList(new QueryWrapper<GroupReviewLedger>()
                .eq("group_id", groupId).orderByDesc("id").last("LIMIT 100"));
        Map<String, Object> view = new HashMap<>();
        view.put("capacity", groupReviewPendingCapacity);
        view.put("pendingCount", pendingReviewCount(groupId));
        view.put("reviews", rows.stream().map(this::reviewView).toList());
        return view;
    }

    private long pendingReviewCount(Long groupId) {
        Long count = reviewLedgerMapper.selectCount(new QueryWrapper<GroupReviewLedger>()
                .eq("group_id", groupId).eq("status", "PENDING"));
        return count == null ? 0L : count;
    }

    private Map<String, Object> reviewView(GroupReviewLedger row) {
        Map<String, Object> view = new HashMap<>();
        view.put("reviewId", row.id);
        view.put("groupId", row.groupId);
        view.put("reporterUserId", row.reporterUserId);
        view.put("targetUserId", row.targetUserId);
        view.put("targetMessageId", row.targetMessageId);
        view.put("reason", row.reason);
        view.put("status", row.status);
        view.put("resolutionNote", row.resolutionNote);
        view.put("resolvedBy", row.resolvedBy);
        view.put("resolvedAt", row.resolvedAt);
        view.put("createdAt", row.createdAt);
        view.put("capacity", groupReviewPendingCapacity);
        view.put("pendingCount", row.groupId == null ? null : pendingReviewCount(row.groupId));
        return view;
    }
}
