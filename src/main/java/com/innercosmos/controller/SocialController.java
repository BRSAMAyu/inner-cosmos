package com.innercosmos.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.innercosmos.common.ApiResponse;
import com.innercosmos.common.ErrorCode;
import com.innercosmos.entity.FriendRelation;
import com.innercosmos.entity.BlockRelation;
import com.innercosmos.entity.SlowLetter;
import com.innercosmos.entity.SocialGroup;
import com.innercosmos.entity.SocialGroupMember;
import com.innercosmos.entity.User;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.mapper.FriendRelationMapper;
import com.innercosmos.mapper.BlockRelationMapper;
import com.innercosmos.mapper.SlowLetterMapper;
import com.innercosmos.mapper.SocialGroupMapper;
import com.innercosmos.mapper.SocialGroupMemberMapper;
import com.innercosmos.mapper.UserMapper;
import jakarta.servlet.http.HttpSession;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/social")
public class SocialController extends BaseController {
    private final UserMapper userMapper;
    private final FriendRelationMapper friendMapper;
    private final SocialGroupMapper groupMapper;
    private final SocialGroupMemberMapper memberMapper;
    private final SlowLetterMapper letterMapper;
    private final BlockRelationMapper blockMapper;

    public SocialController(UserMapper userMapper,
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

    @GetMapping("/people")
    public ApiResponse<List<Map<String, Object>>> people(HttpSession session) {
        Long me = currentUserId(session);
        List<User> users = userMapper.selectList(new QueryWrapper<User>()
                .ne("id", me)
                .eq("status", "ACTIVE")
                .in("account_kind", "HUMAN", "SHOWCASE")
                .orderByDesc("last_login_at")
                .orderByDesc("id")
                .last("LIMIT 30"));
        return ApiResponse.ok(users.stream().map(u -> {
            Map<String, Object> item = new HashMap<>();
            item.put("id", u.id);
            item.put("username", u.username);
            item.put("nickname", u.nickname == null ? u.username : u.nickname);
            item.put("relationStatus", relationStatus(me, u.id));
            return item;
        }).toList());
    }

    @GetMapping("/friends")
    public ApiResponse<List<Map<String, Object>>> friends(HttpSession session) {
        Long me = currentUserId(session);
        List<FriendRelation> rows = friendMapper.selectList(new QueryWrapper<FriendRelation>()
                .eq("status", "ACCEPTED")
                .and(q -> q.eq("requester_id", me).or().eq("addressee_id", me))
                .orderByDesc("updated_at"));
        return ApiResponse.ok(rows.stream().map(r -> friendView(me, r)).toList());
    }

    @GetMapping("/requests")
    public ApiResponse<Map<String, Object>> requests(HttpSession session) {
        Long me = currentUserId(session);
        List<FriendRelation> incoming = friendMapper.selectList(new QueryWrapper<FriendRelation>()
                .eq("addressee_id", me).eq("status", "PENDING").orderByDesc("id"));
        List<FriendRelation> outgoing = friendMapper.selectList(new QueryWrapper<FriendRelation>()
                .eq("requester_id", me).eq("status", "PENDING").orderByDesc("id"));
        return ApiResponse.ok(Map.of(
                "incoming", incoming.stream().map(r -> friendView(me, r)).toList(),
                "outgoing", outgoing.stream().map(r -> friendView(me, r)).toList()
        ));
    }

    @PostMapping("/friends/request")
    public ApiResponse<FriendRelation> requestFriend(@RequestBody Map<String, Object> body, HttpSession session) {
        Long me = currentUserId(session);
        Long target = Long.valueOf(String.valueOf(body.get("userId")));
        if (me.equals(target)) throw new BusinessException(ErrorCode.BAD_REQUEST, "不能添加自己");
        return ApiResponse.ok(createOrResumeRequest(me, target,
                String.valueOf(body.getOrDefault("source", "SOCIAL_PAGE"))));
    }

    @PostMapping("/connections/from-letter/{letterId}")
    public ApiResponse<FriendRelation> requestFromLetter(@PathVariable Long letterId, HttpSession session) {
        Long me = currentUserId(session);
        SlowLetter letter = letterMapper.selectById(letterId);
        if (letter == null || !me.equals(letter.receiverUserId)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "只有这封慢信的收件人可以发起连接");
        }
        if (!List.of("READ", "REPLIED").contains(letter.status)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请在慢信抵达并阅读后再决定是否认识对方");
        }
        if (isBlocked(me, letter.senderUserId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "双方存在屏蔽关系，不能发起连接");
        }
        return ApiResponse.ok(createOrResumeRequest(me, letter.senderUserId, "SLOW_LETTER:" + letterId));
    }

    private FriendRelation createOrResumeRequest(Long me, Long target, String source) {
        FriendRelation relation = friendMapper.selectOne(new QueryWrapper<FriendRelation>()
                .and(q -> q.eq("requester_id", me).eq("addressee_id", target)
                        .or()
                        .eq("requester_id", target).eq("addressee_id", me))
                .last("LIMIT 1"));
        if (relation == null) {
            relation = new FriendRelation();
            relation.requesterId = me;
            relation.addresseeId = target;
            relation.status = "PENDING";
            relation.source = source;
            friendMapper.insert(relation);
        } else if (List.of("DECLINED", "WITHDRAWN").contains(relation.status)) {
            relation.requesterId = me;
            relation.addresseeId = target;
            relation.status = "PENDING";
            relation.source = source;
            friendMapper.updateById(relation);
        }
        return relation;
    }

    @PostMapping("/friends/{id}/accept")
    public ApiResponse<FriendRelation> accept(@PathVariable Long id, HttpSession session) {
        Long me = currentUserId(session);
        FriendRelation relation = friendMapper.selectById(id);
        if (relation == null || !me.equals(relation.addresseeId) || !"PENDING".equals(relation.status)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "无权处理这条好友申请");
        }
        relation.status = "ACCEPTED";
        friendMapper.updateById(relation);
        return ApiResponse.ok(relation);
    }

    @PostMapping("/friends/{id}/decline")
    public ApiResponse<FriendRelation> decline(@PathVariable Long id, HttpSession session) {
        Long me = currentUserId(session);
        FriendRelation relation = friendMapper.selectById(id);
        if (relation == null || !me.equals(relation.addresseeId) || !"PENDING".equals(relation.status)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "无权处理这条好友申请");
        }
        relation.status = "DECLINED";
        friendMapper.updateById(relation);
        return ApiResponse.ok(relation);
    }

    @PostMapping("/friends/{id}/leave")
    public ApiResponse<FriendRelation> leave(@PathVariable Long id, HttpSession session) {
        Long me = currentUserId(session);
        FriendRelation relation = friendMapper.selectById(id);
        if (relation == null || !"ACCEPTED".equals(relation.status)
                || (!me.equals(relation.requesterId) && !me.equals(relation.addresseeId))) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "无权退出这段连接");
        }
        relation.status = "WITHDRAWN";
        friendMapper.updateById(relation);
        return ApiResponse.ok(relation);
    }

    @GetMapping("/groups")
    public ApiResponse<List<SocialGroup>> groups(HttpSession session) {
        Long me = currentUserId(session);
        List<Long> groupIds = memberMapper.selectList(new QueryWrapper<SocialGroupMember>()
                        .eq("user_id", me).eq("status", "ACTIVE"))
                .stream().map(m -> m.groupId).toList();
        if (groupIds.isEmpty()) return ApiResponse.ok(List.of());
        return ApiResponse.ok(groupMapper.selectList(new QueryWrapper<SocialGroup>().in("id", groupIds).orderByDesc("id")));
    }

    // Regression (Gemini audit / remaining-work-handoff.md 2.2.4): the group row and its OWNER
    // membership row were two independent, unguarded inserts -- a failure between them (or a
    // request abort) could leave an ownerless group. @Transactional makes them succeed or fail
    // together.
    @Transactional(rollbackFor = Exception.class)
    @PostMapping("/groups")
    public ApiResponse<SocialGroup> createGroup(@RequestBody Map<String, Object> body, HttpSession session) {
        Long me = currentUserId(session);
        String name = String.valueOf(body.getOrDefault("groupName", "")).trim();
        if (name.isBlank()) throw new BusinessException(ErrorCode.BAD_REQUEST, "群组名不能为空");
        SocialGroup group = new SocialGroup();
        group.ownerUserId = me;
        group.groupName = name;
        group.intro = String.valueOf(body.getOrDefault("intro", ""));
        group.visibility = String.valueOf(body.getOrDefault("visibility", "PRIVATE"));
        groupMapper.insert(group);
        SocialGroupMember member = new SocialGroupMember();
        member.groupId = group.id;
        member.userId = me;
        member.memberRole = "OWNER";
        member.status = "ACTIVE";
        memberMapper.insert(member);
        return ApiResponse.ok(group);
    }

    @PostMapping("/groups/{id}/invite")
    public ApiResponse<SocialGroupMember> inviteToGroup(@PathVariable Long id, @RequestBody Map<String, String> body, HttpSession session) {
        Long me = currentUserId(session);
        SocialGroupMember myMembership = memberMapper.selectOne(new QueryWrapper<SocialGroupMember>()
                .eq("group_id", id).eq("user_id", me).eq("status", "ACTIVE"));
        if (myMembership == null) throw new BusinessException(ErrorCode.UNAUTHORIZED, "只有群组成员可以邀请他人");
        Long targetUserId = Long.valueOf(body.get("userId"));
        // Regression (Gemini audit / remaining-work-handoff.md 2.2.4): self-invite, block, and
        // non-friend invites were previously accepted -- friendMapper/blockMapper were already
        // injected and used elsewhere in this controller (relationStatus/isBlocked) but never
        // consulted here.
        if (me.equals(targetUserId)) throw new BusinessException(ErrorCode.BAD_REQUEST, "不能邀请自己");
        if (isBlocked(me, targetUserId)) throw new BusinessException(ErrorCode.FORBIDDEN, "双方存在屏蔽关系，不能邀请");
        if (!"ACCEPTED".equals(relationStatus(me, targetUserId))) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "只能邀请已互相接受的好友加入群组");
        }
        if (userMapper.selectById(targetUserId) == null) throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        SocialGroupMember existing = memberMapper.selectOne(new QueryWrapper<SocialGroupMember>()
                .eq("group_id", id).eq("user_id", targetUserId));
        if (existing != null && !"DECLINED".equals(existing.status) && !"LEFT".equals(existing.status)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "对方已经在群组中或已有待处理邀请");
        }
        SocialGroupMember invite = existing != null ? existing : new SocialGroupMember();
        invite.groupId = id;
        invite.userId = targetUserId;
        invite.memberRole = "MEMBER";
        invite.status = "PENDING";
        if (existing != null) memberMapper.updateById(invite); else memberMapper.insert(invite);
        return ApiResponse.ok(invite);
    }

    @GetMapping("/groups/invites")
    public ApiResponse<List<Map<String, Object>>> groupInvites(HttpSession session) {
        Long me = currentUserId(session);
        List<SocialGroupMember> pending = memberMapper.selectList(new QueryWrapper<SocialGroupMember>()
                .eq("user_id", me).eq("status", "PENDING"));
        if (pending.isEmpty()) return ApiResponse.ok(List.of());
        List<Long> groupIds = pending.stream().map(m -> m.groupId).toList();
        Map<Long, SocialGroup> groups = groupMapper.selectList(new QueryWrapper<SocialGroup>().in("id", groupIds))
                .stream().collect(java.util.stream.Collectors.toMap(g -> g.id, g -> g));
        return ApiResponse.ok(pending.stream().map(m -> {
            Map<String, Object> row = new HashMap<>();
            row.put("memberId", m.id);
            row.put("groupId", m.groupId);
            SocialGroup group = groups.get(m.groupId);
            row.put("groupName", group == null ? "" : group.groupName);
            return row;
        }).toList());
    }

    private enum InviteDecision { ACCEPT, DECLINE }

    @PostMapping("/groups/invites/{memberId}/respond")
    public ApiResponse<SocialGroupMember> respondToGroupInvite(@PathVariable Long memberId, @RequestBody Map<String, String> body, HttpSession session) {
        Long me = currentUserId(session);
        SocialGroupMember member = memberMapper.selectById(memberId);
        if (member == null || !me.equals(member.userId)) throw new BusinessException(ErrorCode.UNAUTHORIZED, "无权操作此邀请");
        if (!"PENDING".equals(member.status)) throw new BusinessException(ErrorCode.BAD_REQUEST, "该邀请已被处理");
        // Regression (Gemini audit / remaining-work-handoff.md 2.2.4): `"accept".equals(decision)
        // ? ACTIVE : DECLINED` silently treated ANY non-"accept" value -- typos, null, empty -- as
        // a decline, instead of rejecting invalid input. Use an explicit enum of the two legal
        // decisions (matches the frontend's own "accept" | "decline" union in web/src/api.ts).
        InviteDecision decision;
        try {
            decision = InviteDecision.valueOf(String.valueOf(body.get("decision")).toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "decision 必须是 accept 或 decline");
        }
        String newStatus = decision == InviteDecision.ACCEPT ? "ACTIVE" : "DECLINED";
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
        return ApiResponse.ok(member);
    }

    @PostMapping("/groups/{id}/leave")
    public ApiResponse<Void> leaveGroup(@PathVariable Long id, HttpSession session) {
        Long me = currentUserId(session);
        SocialGroupMember member = memberMapper.selectOne(new QueryWrapper<SocialGroupMember>()
                .eq("group_id", id).eq("user_id", me).eq("status", "ACTIVE"));
        if (member == null) throw new BusinessException(ErrorCode.NOT_FOUND, "你不在这个群组中");
        if ("OWNER".equals(member.memberRole)) throw new BusinessException(ErrorCode.BAD_REQUEST, "群主不能直接退出，请先转让或解散群组");
        member.status = "LEFT";
        memberMapper.updateById(member);
        return ApiResponse.ok(null);
    }

    @GetMapping("/groups/{id}/members")
    public ApiResponse<List<Map<String, Object>>> groupMembers(@PathVariable Long id, HttpSession session) {
        Long me = currentUserId(session);
        long myCount = memberMapper.selectCount(new QueryWrapper<SocialGroupMember>()
                .eq("group_id", id).eq("user_id", me).eq("status", "ACTIVE"));
        if (myCount == 0) throw new BusinessException(ErrorCode.UNAUTHORIZED, "无权查看此群组成员");
        List<SocialGroupMember> members = memberMapper.selectList(new QueryWrapper<SocialGroupMember>()
                .eq("group_id", id).eq("status", "ACTIVE"));
        List<Long> userIds = members.stream().map(m -> m.userId).toList();
        Map<Long, User> users = userIds.isEmpty() ? Map.of() : userMapper.selectList(new QueryWrapper<User>().in("id", userIds))
                .stream().collect(java.util.stream.Collectors.toMap(u -> u.id, u -> u));
        return ApiResponse.ok(members.stream().map(m -> {
            Map<String, Object> row = new HashMap<>();
            row.put("userId", m.userId);
            row.put("memberRole", m.memberRole);
            User user = users.get(m.userId);
            row.put("nickname", user == null ? "" : user.nickname);
            return row;
        }).toList());
    }

    private String relationStatus(Long me, Long other) {
        FriendRelation relation = friendMapper.selectOne(new QueryWrapper<FriendRelation>()
                .and(q -> q.eq("requester_id", me).eq("addressee_id", other)
                        .or()
                        .eq("requester_id", other).eq("addressee_id", me))
                .last("LIMIT 1"));
        if (relation == null) return "NONE";
        if ("PENDING".equals(relation.status)) {
            return me.equals(relation.requesterId) ? "PENDING_OUT" : "PENDING_IN";
        }
        return relation.status;
    }

    private boolean isBlocked(Long first, Long second) {
        Long count = blockMapper.selectCount(new QueryWrapper<BlockRelation>()
                .and(q -> q.eq("blocker_user_id", first).eq("blocked_user_id", second)
                        .or().eq("blocker_user_id", second).eq("blocked_user_id", first)));
        return count != null && count > 0;
    }

    private Map<String, Object> friendView(Long me, FriendRelation relation) {
        Long otherId = me.equals(relation.requesterId) ? relation.addresseeId : relation.requesterId;
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
