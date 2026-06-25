package com.innercosmos.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.common.ApiResponse;
import com.innercosmos.common.ErrorCode;
import com.innercosmos.entity.FriendRelation;
import com.innercosmos.entity.SocialGroup;
import com.innercosmos.entity.SocialGroupMember;
import com.innercosmos.entity.User;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.mapper.FriendRelationMapper;
import com.innercosmos.mapper.SocialGroupMapper;
import com.innercosmos.mapper.SocialGroupMemberMapper;
import com.innercosmos.mapper.UserMapper;
import jakarta.servlet.http.HttpSession;
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

    public SocialController(UserMapper userMapper,
                            FriendRelationMapper friendMapper,
                            SocialGroupMapper groupMapper,
                            SocialGroupMemberMapper memberMapper) {
        this.userMapper = userMapper;
        this.friendMapper = friendMapper;
        this.groupMapper = groupMapper;
        this.memberMapper = memberMapper;
    }

    @GetMapping("/people")
    public ApiResponse<List<Map<String, Object>>> people(HttpSession session) {
        Long me = currentUserId(session);
        List<User> users = userMapper.selectList(new QueryWrapper<User>()
                .ne("id", me)
                .eq("status", "ACTIVE")
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
            relation.source = String.valueOf(body.getOrDefault("source", "SOCIAL_PAGE"));
            friendMapper.insert(relation);
        }
        return ApiResponse.ok(relation);
    }

    @PostMapping("/friends/{id}/accept")
    public ApiResponse<FriendRelation> accept(@PathVariable Long id, HttpSession session) {
        Long me = currentUserId(session);
        FriendRelation relation = friendMapper.selectById(id);
        if (relation == null || !me.equals(relation.addresseeId)) {
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
        if (relation == null || !me.equals(relation.addresseeId)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "无权处理这条好友申请");
        }
        relation.status = "DECLINED";
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

    private Map<String, Object> friendView(Long me, FriendRelation relation) {
        Long otherId = me.equals(relation.requesterId) ? relation.addresseeId : relation.requesterId;
        User other = userMapper.selectById(otherId);
        Map<String, Object> item = new HashMap<>();
        item.put("id", relation.id);
        item.put("status", relation.status);
        item.put("userId", otherId);
        item.put("nickname", other == null ? "未知用户" : (other.nickname == null ? other.username : other.nickname));
        item.put("username", other == null ? "" : other.username);
        return item;
    }
}
