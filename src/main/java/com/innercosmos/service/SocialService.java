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

    /** A bounded list of active, discoverable human accounts (not the caller). */
    List<Map<String, Object>> discoverPeople(Long userId);

    /**
     * Exact classroom lookup by username/seat code or full nickname. A blank query preserves the
     * bounded discovery list; a non-blank query never widens beyond active HUMAN accounts.
     */
    List<Map<String, Object>> discoverPeople(Long userId, String exactQuery);

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

    /**
     * Returns the latest bounded group conversation in chronological order.
     *
     * @throws com.innercosmos.exception.BusinessException UNAUTHORIZED unless the caller is an
     *         active member of the group.
     */
    List<Map<String, Object>> listGroupMessages(Long userId, Long groupId);

    /**
     * Adds one user-authored message to a group the caller has actively joined.
     *
     * @throws com.innercosmos.exception.BusinessException UNAUTHORIZED unless the caller is an
     *         active member, BAD_REQUEST when the message is blank or exceeds the bounded length,
     *         FORBIDDEN when the caller is currently muted (message names the remaining duration
     *         or the manual-release requirement; the message body is never persisted on refusal),
     *         CONFLICT when the group is dissolved.
     */
    Map<String, Object> sendGroupMessage(Long userId, Long groupId, String messageBody);

    // ------------------------------------------------------------------
    // CP-35 group governance (closing-checklist §2-7). "主持人" (host) is the group OWNER --
    // no separate MODERATOR member role exists in this batch.
    // ------------------------------------------------------------------

    /**
     * Host mutes a member for {@code durationMinutes} minutes, or until manual release when
     * {@code durationMinutes} is null.
     *
     * @throws com.innercosmos.exception.BusinessException NOT_FOUND when the group does not
     *         exist or the target is not an active member, FORBIDDEN unless the caller is the
     *         group owner, BAD_REQUEST when muting self/the owner or the duration is not a
     *         positive number of minutes, CONFLICT when the group is dissolved.
     */
    void muteGroupMember(Long actorUserId, Long groupId, Long targetUserId, Integer durationMinutes);

    /**
     * Host lifts a mute before its expiry.
     *
     * @throws com.innercosmos.exception.BusinessException NOT_FOUND when the group does not
     *         exist or the target is not an active member, FORBIDDEN unless the caller is the
     *         group owner, CONFLICT when the group is dissolved.
     */
    void unmuteGroupMember(Long actorUserId, Long groupId, Long targetUserId);

    /**
     * Atomically hands ownership to an active member: the group row, the new OWNER row and the
     * old owner's demotion succeed or fail together, and the new owner takes effect immediately.
     *
     * @throws com.innercosmos.exception.BusinessException NOT_FOUND when the group does not
     *         exist, FORBIDDEN unless the caller is the current owner, BAD_REQUEST when the
     *         target is the caller or not an active member, CONFLICT when the group is
     *         dissolved or a concurrent transfer already moved the crown.
     */
    void transferGroupOwnership(Long ownerUserId, Long groupId, Long targetUserId);

    /**
     * Marks the group DISSOLVED and flips every membership row (ACTIVE/PENDING) to REMOVED, so
     * the group disappears from every member's list and all group endpoints answer with an
     * explicit 「群已解散」 refusal instead of leaving zombie-readable data.
     *
     * @throws com.innercosmos.exception.BusinessException NOT_FOUND when the group does not
     *         exist, FORBIDDEN unless the caller is the owner, CONFLICT when already dissolved.
     */
    void dissolveGroup(Long ownerUserId, Long groupId);

    /**
     * Records one structured review-ledger row (status PENDING) for a group message. Capacity
     * gate is fail-closed: once the group's PENDING count reaches
     * {@code inner-cosmos.social.group-review-pending-capacity} the new report is explicitly
     * REJECTED (CONFLICT naming the bound) and nothing is persisted -- never silently queued,
     * never silently dropped.
     *
     * @throws com.innercosmos.exception.BusinessException NOT_FOUND when the group or the
     *         reported message does not exist in that group, UNAUTHORIZED unless the caller is
     *         an active member, BAD_REQUEST for a blank/oversized reason or a duplicate pending
     *         report of the same message by the same reporter, CONFLICT when the group is
     *         dissolved or the review capacity is exhausted. A muted member may still report --
     *         muting silences speech, not the safety valve.
     */
    Map<String, Object> reportGroupMessage(Long reporterUserId, Long groupId, Long messageId, String reason);

    /**
     * Host resolves ({@code decision="resolve"}) or dismisses ({@code decision="dismiss"}) one
     * pending review, freeing one capacity slot.
     *
     * @throws com.innercosmos.exception.BusinessException NOT_FOUND when the group or the review
     *         does not exist in that group, FORBIDDEN unless the caller is the group owner,
     *         BAD_REQUEST for an invalid decision, an oversized note, or an already-resolved
     *         review, CONFLICT when the group is dissolved.
     */
    Map<String, Object> resolveGroupReview(Long hostUserId, Long groupId, Long reviewId, String decision, String note);

    /**
     * Host's structured queue view: the bounded pending count, the configured capacity and the
     * recent ledger rows.
     *
     * @throws com.innercosmos.exception.BusinessException NOT_FOUND when the group does not
     *         exist, FORBIDDEN unless the caller is the group owner, CONFLICT when dissolved.
     */
    Map<String, Object> listGroupReviews(Long hostUserId, Long groupId);
}
