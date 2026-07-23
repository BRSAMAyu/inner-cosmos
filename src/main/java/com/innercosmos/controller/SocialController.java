package com.innercosmos.controller;

import com.innercosmos.common.ApiResponse;
import com.innercosmos.entity.FriendRelation;
import com.innercosmos.entity.SocialGroup;
import com.innercosmos.entity.SocialGroupMember;
import com.innercosmos.service.SocialService;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/social")
public class SocialController extends BaseController {
    private final SocialService socialService;

    public SocialController(SocialService socialService) {
        this.socialService = socialService;
    }

    @GetMapping("/people")
    public ApiResponse<List<Map<String, Object>>> people(HttpSession session) {
        return ApiResponse.ok(socialService.discoverPeople(currentUserId(session)));
    }

    @GetMapping("/friends")
    public ApiResponse<List<Map<String, Object>>> friends(HttpSession session) {
        return ApiResponse.ok(socialService.listFriends(currentUserId(session)));
    }

    @GetMapping("/requests")
    public ApiResponse<Map<String, Object>> requests(HttpSession session) {
        return ApiResponse.ok(socialService.listFriendRequests(currentUserId(session)));
    }

    @PostMapping("/friends/request")
    public ApiResponse<FriendRelation> requestFriend(@RequestBody Map<String, Object> body, HttpSession session) {
        Long target = Long.valueOf(String.valueOf(body.get("userId")));
        String source = String.valueOf(body.getOrDefault("source", "SOCIAL_PAGE"));
        return ApiResponse.ok(socialService.requestFriend(currentUserId(session), target, source));
    }

    @PostMapping("/connections/from-letter/{letterId}")
    public ApiResponse<FriendRelation> requestFromLetter(@PathVariable Long letterId, HttpSession session) {
        return ApiResponse.ok(socialService.requestFriendFromLetter(currentUserId(session), letterId));
    }

    @PostMapping("/friends/{id}/accept")
    public ApiResponse<FriendRelation> accept(@PathVariable Long id, HttpSession session) {
        return ApiResponse.ok(socialService.acceptFriendRequest(currentUserId(session), id));
    }

    @PostMapping("/friends/{id}/decline")
    public ApiResponse<FriendRelation> decline(@PathVariable Long id, HttpSession session) {
        return ApiResponse.ok(socialService.declineFriendRequest(currentUserId(session), id));
    }

    @PostMapping("/friends/{id}/leave")
    public ApiResponse<FriendRelation> leave(@PathVariable Long id, HttpSession session) {
        return ApiResponse.ok(socialService.leaveFriendRelation(currentUserId(session), id));
    }

    @GetMapping("/groups")
    public ApiResponse<List<SocialGroup>> groups(HttpSession session) {
        return ApiResponse.ok(socialService.listGroups(currentUserId(session)));
    }

    @PostMapping("/groups")
    public ApiResponse<SocialGroup> createGroup(@RequestBody Map<String, Object> body, HttpSession session) {
        String name = String.valueOf(body.getOrDefault("groupName", ""));
        String intro = String.valueOf(body.getOrDefault("intro", ""));
        String visibility = String.valueOf(body.getOrDefault("visibility", "PRIVATE"));
        return ApiResponse.ok(socialService.createGroup(currentUserId(session), name, intro, visibility));
    }

    @PostMapping("/groups/{id}/invite")
    public ApiResponse<SocialGroupMember> inviteToGroup(@PathVariable Long id, @RequestBody Map<String, String> body, HttpSession session) {
        Long targetUserId = Long.valueOf(body.get("userId"));
        return ApiResponse.ok(socialService.inviteToGroup(currentUserId(session), id, targetUserId));
    }

    @GetMapping("/groups/invites")
    public ApiResponse<List<Map<String, Object>>> groupInvites(HttpSession session) {
        return ApiResponse.ok(socialService.listGroupInvites(currentUserId(session)));
    }

    @PostMapping("/groups/invites/{memberId}/respond")
    public ApiResponse<SocialGroupMember> respondToGroupInvite(@PathVariable Long memberId, @RequestBody Map<String, String> body, HttpSession session) {
        return ApiResponse.ok(socialService.respondToGroupInvite(currentUserId(session), memberId, body.get("decision")));
    }

    @PostMapping("/groups/{id}/leave")
    public ApiResponse<Void> leaveGroup(@PathVariable Long id, HttpSession session) {
        socialService.leaveGroup(currentUserId(session), id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/groups/{id}/members")
    public ApiResponse<List<Map<String, Object>>> groupMembers(@PathVariable Long id, HttpSession session) {
        return ApiResponse.ok(socialService.listGroupMembers(currentUserId(session), id));
    }
}
