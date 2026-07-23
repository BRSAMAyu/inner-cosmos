package com.innercosmos.service;

import com.innercosmos.entity.FriendRelation;
import com.innercosmos.entity.SocialGroup;
import com.innercosmos.entity.SocialGroupMember;

import java.util.List;
import java.util.Map;

/**
 * G2.ARCH-MODULES: gives {@code SocialController} a service seam instead of injecting six mappers
 * (UserMapper, FriendRelationMapper, SocialGroupMapper, SocialGroupMemberMapper, SlowLetterMapper,
 * BlockRelationMapper) directly. Owns the friend-request, block, and group-membership business
 * rules and owner-scope checks in one place instead of at each controller call site.
 */
public interface SocialService {

    /** Up to 30 discoverable human/showcase accounts (not the caller) with the caller's relation status. */
    List<Map<String, Object>> discoverPeople(Long userId);

    /** The caller's accepted friend connections. */
    List<Map<String, Object>> listFriends(Long userId);

    /** {@code {incoming: [...], outgoing: [...]}} pending friend requests. */
    Map<String, Object> listFriendRequests(Long userId);

    /**
     * Sends (or resumes a previously declined/withdrawn) friend request.
     *
     * @throws com.innercosmos.exception.BusinessException BAD_REQUEST if {@code targetUserId} is the caller.
     */
    FriendRelation requestFriend(Long userId, Long targetUserId, String source);

    /**
     * Requests a connection with the sender of a slow letter the caller received, without the
     * client ever supplying the sender's user id.
     *
     * @throws com.innercosmos.exception.BusinessException UNAUTHORIZED if the caller is not the
     *         letter's receiver, BAD_REQUEST if the letter has not been read yet, FORBIDDEN if
     *         either party has blocked the other.
     */
    FriendRelation requestFriendFromLetter(Long userId, Long letterId);

    /**
     * @throws com.innercosmos.exception.BusinessException UNAUTHORIZED unless the caller is the
     *         pending request's addressee.
     */
    FriendRelation acceptFriendRequest(Long userId, Long relationId);

    /**
     * @throws com.innercosmos.exception.BusinessException UNAUTHORIZED unless the caller is the
     *         pending request's addressee.
     */
    FriendRelation declineFriendRequest(Long userId, Long relationId);

    /**
     * @throws com.innercosmos.exception.BusinessException UNAUTHORIZED unless the caller is a party
     *         to the accepted relation.
     */
    FriendRelation leaveFriendRelation(Long userId, Long relationId);

    /** Groups the caller is an active member of. */
    List<SocialGroup> listGroups(Long userId);

    /**
     * Creates a group and its OWNER membership row atomically.
     *
     * @throws com.innercosmos.exception.BusinessException BAD_REQUEST if {@code name} is blank.
     */
    SocialGroup createGroup(Long userId, String name, String intro, String visibility);

    /**
     * @throws com.innercosmos.exception.BusinessException UNAUTHORIZED if the caller is not an
     *         active member of the group, BAD_REQUEST if inviting self or a non-accepted-friend or
     *         someone already active/pending, FORBIDDEN if either party has blocked the other,
     *         NOT_FOUND if the target user does not exist.
     */
    SocialGroupMember inviteToGroup(Long userId, Long groupId, Long targetUserId);

    /** The caller's own pending group invitations, each with the group's name. */
    List<Map<String, Object>> listGroupInvites(Long userId);

    /**
     * @param decision must be {@code "accept"} or {@code "decline"} (case-insensitive).
     * @throws com.innercosmos.exception.BusinessException UNAUTHORIZED if the invite is not the
     *         caller's own, BAD_REQUEST if it is not (still) pending or {@code decision} is invalid.
     */
    SocialGroupMember respondToGroupInvite(Long userId, Long memberId, String decision);

    /**
     * @throws com.innercosmos.exception.BusinessException NOT_FOUND if the caller is not an active
     *         member, BAD_REQUEST if the caller is the OWNER.
     */
    void leaveGroup(Long userId, Long groupId);

    /**
     * @throws com.innercosmos.exception.BusinessException UNAUTHORIZED unless the caller is an
     *         active member of the group.
     */
    List<Map<String, Object>> listGroupMembers(Long userId, Long groupId);
}
