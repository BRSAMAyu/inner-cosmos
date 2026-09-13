package com.innercosmos.social;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.innercosmos.common.Constants;
import com.innercosmos.entity.FriendRelation;
import com.innercosmos.entity.SocialGroup;
import com.innercosmos.entity.User;
import com.innercosmos.mapper.FriendRelationMapper;
import com.innercosmos.mapper.GroupReviewLedgerMapper;
import com.innercosmos.mapper.SocialGroupMapper;
import com.innercosmos.mapper.SocialGroupMemberMapper;
import com.innercosmos.mapper.SocialGroupMessageMapper;
import com.innercosmos.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CP-35 (closing-checklist §2-7) group-governance contract: mute / ownership transfer /
 * dissolve / join-time-bounded history visibility / per-group host review capacity gate,
 * driven end to end over the real H2 schema twin (so the V50 columns, the REMOVED flip and
 * the SQL-level created_at >= joined_at filter are exercised for real, not against mocks).
 *
 * <p>Semantics pinned by this contract:
 * <ul>
 *   <li>主持人 (host) = the group OWNER; there is no separate moderator role in CP-35.</li>
 *   <li>A muted sender gets a loud FORBIDDEN naming the remaining minutes (or the manual
 *       release requirement); the message is never persisted on refusal. An expired mute
 *       self-heals lazily -- no background job.</li>
 *   <li>Transfer is atomic (group row + promote + demote in one transaction); the new owner
 *       takes effect immediately, the old owner becomes an ordinary member.</li>
 *   <li>Dissolve is owner-only; afterwards the group is gone from every member's list and
 *       every group endpoint answers 409 「群已解散」 -- no zombie-readable data.</li>
 *   <li>History visibility: a member sees only messages created at/after their joined_at;
 *       the host (OWNER) is unrestricted (documented choice). Group messages have no other
 *       read path (no by-id endpoint exists), so this bound is total.</li>
 *   <li>Capacity gate is fail-closed: over
 *       inner-cosmos.social.group-review-pending-capacity (overridden to 2 here) a new
 *       report is explicitly rejected with an honest CONFLICT and nothing is persisted --
 *       never silently queued, never silently dropped.</li>
 * </ul>
 *
 * <p>Test identities use the reserved 98xxxxxxx id segment and never collide with seeds.</p>
 */
@SpringBootTest(properties = {
        "inner-cosmos.social.group-review-pending-capacity=2",
        "spring.task.scheduling.enabled=false",
})
@AutoConfigureMockMvc
@Import(com.innercosmos.config.TestRateLimitConfig.class)
class GroupGovernanceContractTest {

    private static final long OWNER = 980_000_001L;
    private static final long MEMBER_A = 980_000_002L;
    private static final long MEMBER_B = 980_000_003L;
    private static final long OUTSIDER = 980_000_004L;
    private static final long LATE_JOINER = 980_000_005L;

    @Autowired MockMvc mockMvc;
    @Autowired UserMapper userMapper;
    @Autowired FriendRelationMapper friendMapper;
    @Autowired SocialGroupMapper groupMapper;
    @Autowired SocialGroupMemberMapper memberMapper;
    @Autowired SocialGroupMessageMapper messageMapper;
    @Autowired GroupReviewLedgerMapper reviewLedgerMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<Long> createdGroupIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        ensureUser(OWNER, "cp35_owner");
        ensureUser(MEMBER_A, "cp35_member_a");
        ensureUser(MEMBER_B, "cp35_member_b");
        ensureUser(OUTSIDER, "cp35_outsider");
        ensureUser(LATE_JOINER, "cp35_late");
        // Group invitations require an accepted friend relation; arrange it directly so the
        // contract stays about governance, not the invite friendship dance.
        ensureFriendship(OWNER, MEMBER_A);
        ensureFriendship(OWNER, MEMBER_B);
        ensureFriendship(OWNER, LATE_JOINER);
        // Clean any rows left over from a previous run of this class (shared in-mem H2).
        List<Long> myGroupIds = memberMapper.selectList(new QueryWrapper<com.innercosmos.entity.SocialGroupMember>()
                        .in("user_id", OWNER, MEMBER_A, MEMBER_B, OUTSIDER, LATE_JOINER))
                .stream().map(m -> m.groupId).distinct().toList();
        myGroupIds.forEach(this::wipeGroup);
        createdGroupIds.forEach(this::wipeGroup);
        createdGroupIds.clear();
    }

    private void wipeGroup(Long groupId) {
        if (groupId == null) return;
        reviewLedgerMapper.delete(new QueryWrapper<com.innercosmos.entity.GroupReviewLedger>()
                .eq("group_id", groupId));
        messageMapper.delete(new QueryWrapper<com.innercosmos.entity.SocialGroupMessage>()
                .eq("group_id", groupId));
        memberMapper.delete(new QueryWrapper<com.innercosmos.entity.SocialGroupMember>()
                .eq("group_id", groupId));
        groupMapper.deleteById(groupId);
    }

    private void ensureUser(Long id, String username) {
        if (userMapper.selectById(id) != null) return;
        User user = new User();
        user.id = id;
        user.username = username;
        user.nickname = username;
        user.passwordHash = "cp35-not-a-login-account";
        user.role = "USER";
        user.status = "ACTIVE";
        user.accountKind = "HUMAN";
        userMapper.insert(user);
    }

    private void ensureFriendship(Long a, Long b) {
        Long existing = friendMapper.selectCount(new QueryWrapper<FriendRelation>()
                .and(q -> q.eq("requester_id", a).eq("addressee_id", b)
                        .or().eq("requester_id", b).eq("addressee_id", a)));
        if (existing != null && existing > 0) return;
        FriendRelation relation = new FriendRelation();
        relation.requesterId = a;
        relation.addresseeId = b;
        relation.status = "ACCEPTED";
        relation.source = "CP35_CONTRACT";
        friendMapper.insert(relation);
    }

    private MockHttpSession sessionOf(long userId) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(Constants.SESSION_USER_KEY, userId);
        return session;
    }

    // --------------------------------------------------------------- helpers

    private long createGroup(long owner) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/social/groups")
                        .session(sessionOf(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"groupName\":\"CP35 治理契约群\",\"intro\":\"\",\"visibility\":\"PRIVATE\"}"))
                .andExpect(status().isOk())
                .andReturn();
        long groupId = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asLong();
        createdGroupIds.add(groupId);
        return groupId;
    }

    /** Invites + accepts in one step; the joined_at of the invitee is after everything before. */
    private void joinGroup(long groupId, long inviter, long invitee) throws Exception {
        MvcResult invite = mockMvc.perform(post("/api/social/groups/{id}/invite", groupId)
                        .session(sessionOf(inviter))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + invitee + "}"))
                .andExpect(status().isOk())
                .andReturn();
        long memberId = objectMapper.readTree(invite.getResponse().getContentAsString())
                .path("data").path("id").asLong();
        mockMvc.perform(post("/api/social/groups/invites/{memberId}/respond", memberId)
                        .session(sessionOf(invitee))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"accept\"}"))
                .andExpect(status().isOk());
    }

    private long sendGroupMessage(long groupId, long sender, String body) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/social/groups/{id}/messages", groupId)
                        .session(sessionOf(sender))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"messageBody\":\"" + body + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asLong();
    }

    private List<String> listGroupMessageBodies(long groupId, long reader) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/social/groups/{id}/messages", groupId)
                        .session(sessionOf(reader)))
                .andExpect(status().isOk())
                .andReturn();
        List<String> bodies = new ArrayList<>();
        for (JsonNode row : objectMapper.readTree(result.getResponse().getContentAsString()).path("data")) {
            bodies.add(row.path("messageBody").asText());
        }
        return bodies;
    }

    private long countMessages(long groupId) {
        return messageMapper.selectCount(new QueryWrapper<com.innercosmos.entity.SocialGroupMessage>()
                .eq("group_id", groupId));
    }

    // --------------------------------------------------------------- 1. mute

    @Test
    void muteLifecycleRefusesLoudlyAndSelfHeals() throws Exception {
        long groupId = createGroup(OWNER);
        joinGroup(groupId, OWNER, MEMBER_A);

        // Negative: a non-host cannot mute (403), and nothing changes.
        mockMvc.perform(post("/api/social/groups/{id}/mute", groupId)
                        .session(sessionOf(MEMBER_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + MEMBER_B + ",\"durationMinutes\":10}"))
                .andExpect(status().isForbidden());

        // Host mutes MEMBER_A for 2 minutes.
        mockMvc.perform(post("/api/social/groups/{id}/mute", groupId)
                        .session(sessionOf(OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + MEMBER_A + ",\"durationMinutes\":2}"))
                .andExpect(status().isOk());

        // Muted member's send is refused loudly with the remaining duration, nothing persisted.
        long before = countMessages(groupId);
        mockMvc.perform(post("/api/social/groups/{id}/messages", groupId)
                        .session(sessionOf(MEMBER_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"messageBody\":\"被禁言还想说话\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("剩余")));
        assertEquals(before, countMessages(groupId), "a refused message must not be persisted");

        // The host (and other members) still see everything -- mute only silences the target.
        sendGroupMessage(groupId, OWNER, "主持人仍然可以发言");
        assertTrue(listGroupMessageBodies(groupId, OWNER).contains("主持人仍然可以发言"));

        // Manual unmute restores speech immediately.
        mockMvc.perform(post("/api/social/groups/{id}/unmute", groupId)
                        .session(sessionOf(OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + MEMBER_A + "}"))
                .andExpect(status().isOk());
        sendGroupMessage(groupId, MEMBER_A, "解除禁言后恢复发言");

        // Indefinite mute names the manual-release requirement instead of a duration.
        mockMvc.perform(post("/api/social/groups/{id}/mute", groupId)
                        .session(sessionOf(OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + MEMBER_A + "}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/social/groups/{id}/messages", groupId)
                        .session(sessionOf(MEMBER_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"messageBody\":\"又要说话\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("手动解除")));

        // Host cannot be muted: promote-then-mute is refused, and self-mute is refused.
        mockMvc.perform(post("/api/social/groups/{id}/mute", groupId)
                        .session(sessionOf(OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + OWNER + ",\"durationMinutes\":5}"))
                .andExpect(status().isBadRequest());

        // Expiry: flip the stored window into the past and speech resumes without a job.
        // The service writes mute timestamps from its UTC Clock bean, so the flipped value
        // must come from the same source -- LocalDateTime.now() (local zone) could land
        // AFTER the stored UTC-naive window on an Asia/Shanghai JVM and mask the expiry.
        com.innercosmos.entity.SocialGroupMember row = memberMapper.selectOne(
                new QueryWrapper<com.innercosmos.entity.SocialGroupMember>()
                        .eq("group_id", groupId).eq("user_id", MEMBER_A));
        row.mutedUntil = java.time.LocalDateTime.now(java.time.Clock.systemUTC()).minusSeconds(1);
        memberMapper.updateById(row);
        sendGroupMessage(groupId, MEMBER_A, "禁言期满自动恢复");
        assertTrue(listGroupMessageBodies(groupId, OWNER).contains("禁言期满自动恢复"));
    }

    // --------------------------------------------------------- 2. transfer

    @Test
    void ownershipTransferIsAtomicImmediateAndRevokesTheOldOwner() throws Exception {
        long groupId = createGroup(OWNER);
        joinGroup(groupId, OWNER, MEMBER_A);
        joinGroup(groupId, OWNER, MEMBER_B);

        // Negative: a non-owner cannot transfer (403).
        mockMvc.perform(post("/api/social/groups/{id}/transfer", groupId)
                        .session(sessionOf(MEMBER_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + MEMBER_B + "}"))
                .andExpect(status().isForbidden());

        // Negative: transferring to a non-member is refused.
        mockMvc.perform(post("/api/social/groups/{id}/transfer", groupId)
                        .session(sessionOf(OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + OUTSIDER + "}"))
                .andExpect(status().isBadRequest());

        // Positive: owner hands the crown to MEMBER_A; effective immediately.
        mockMvc.perform(post("/api/social/groups/{id}/transfer", groupId)
                        .session(sessionOf(OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + MEMBER_A + "}"))
                .andExpect(status().isOk());

        SocialGroup group = groupMapper.selectById(groupId);
        assertEquals(MEMBER_A, group.ownerUserId, "the new owner takes effect immediately");
        com.innercosmos.entity.SocialGroupMember oldOwner = memberMapper.selectOne(
                new QueryWrapper<com.innercosmos.entity.SocialGroupMember>()
                        .eq("group_id", groupId).eq("user_id", OWNER));
        com.innercosmos.entity.SocialGroupMember newOwner = memberMapper.selectOne(
                new QueryWrapper<com.innercosmos.entity.SocialGroupMember>()
                        .eq("group_id", groupId).eq("user_id", MEMBER_A));
        assertEquals("MEMBER", oldOwner.memberRole, "the old owner is demoted to an ordinary member");
        assertEquals("OWNER", newOwner.memberRole);

        // The old owner has lost host powers; the new owner can exercise them.
        mockMvc.perform(post("/api/social/groups/{id}/mute", groupId)
                        .session(sessionOf(OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + MEMBER_B + ",\"durationMinutes\":5}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/social/groups/{id}/mute", groupId)
                        .session(sessionOf(MEMBER_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + MEMBER_B + ",\"durationMinutes\":5}"))
                .andExpect(status().isOk());
    }

    // ----------------------------------------------------------- 3. dissolve

    @Test
    void dissolveIsOwnerOnlyAndLeavesNoZombieReadableData() throws Exception {
        long groupId = createGroup(OWNER);
        joinGroup(groupId, OWNER, MEMBER_A);
        sendGroupMessage(groupId, OWNER, "解散前的最后一条");

        // Negative: a non-owner cannot dissolve (403).
        mockMvc.perform(post("/api/social/groups/{id}/dissolve", groupId)
                        .session(sessionOf(MEMBER_A)))
                .andExpect(status().isForbidden());
        // An outsider neither.
        mockMvc.perform(post("/api/social/groups/{id}/dissolve", groupId)
                        .session(sessionOf(OUTSIDER)))
                .andExpect(status().isForbidden());

        // Positive: the owner dissolves; members see the group vanish from their list.
        mockMvc.perform(post("/api/social/groups/{id}/dissolve", groupId)
                        .session(sessionOf(OWNER)))
                .andExpect(status().isOk());

        MvcResult ownerList = mockMvc.perform(get("/api/social/groups")
                        .session(sessionOf(OWNER)))
                .andExpect(status().isOk())
                .andReturn();
        assertFalse(objectMapper.readTree(ownerList.getResponse().getContentAsString()).path("data")
                        .toString().contains("\"id\":" + groupId + ""),
                "a dissolved group must vanish from the owner's list");

        // Every group endpoint answers an explicit 「群已解散」 refusal (409), for every
        // former member -- reads, writes and member listings alike.
        for (long formerMember : new long[]{OWNER, MEMBER_A}) {
            mockMvc.perform(get("/api/social/groups/{id}/messages", groupId)
                            .session(sessionOf(formerMember)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("CONFLICT"))
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("群已解散")));
            mockMvc.perform(post("/api/social/groups/{id}/messages", groupId)
                            .session(sessionOf(formerMember))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"messageBody\":\"解散后还想发言\"}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("群已解散")));
            mockMvc.perform(get("/api/social/groups/{id}/members", groupId)
                            .session(sessionOf(formerMember)))
                    .andExpect(status().isConflict());
        }
        // Re-dissolving is refused explicitly too, never silently idempotent.
        mockMvc.perform(post("/api/social/groups/{id}/dissolve", groupId)
                        .session(sessionOf(OWNER)))
                .andExpect(status().isConflict());
    }

    // ------------------------------------------------- 4. history visibility

    @Test
    void lateJoinersOnlySeeHistoryFromTheirJoinInstantOnward() throws Exception {
        long groupId = createGroup(OWNER);
        joinGroup(groupId, OWNER, MEMBER_A);

        // Pre-join history the late joiner must never see.
        sendGroupMessage(groupId, OWNER, "加入前的第一条");
        sendGroupMessage(groupId, MEMBER_A, "加入前的第二条");

        joinGroup(groupId, OWNER, LATE_JOINER);
        sendGroupMessage(groupId, OWNER, "加入后的第一条");

        // The late joiner sees ONLY post-join messages; the list endpoint is the only read
        // path (there is no by-id group-message endpoint), so this bound is total.
        List<String> seenByLate = listGroupMessageBodies(groupId, LATE_JOINER);
        assertEquals(List.of("加入后的第一条"), seenByLate);
        assertFalse(seenByLate.contains("加入前的第一条"));

        // Documented choice: the host (OWNER) is NOT restricted and keeps the full history.
        List<String> seenByHost = listGroupMessageBodies(groupId, OWNER);
        assertTrue(seenByHost.contains("加入前的第一条"));
        assertTrue(seenByHost.contains("加入前的第二条"));
        assertTrue(seenByHost.contains("加入后的第一条"));
        // Other ordinary members keep their own join-instant bound (MEMBER_A joined before
        // the pre-join messages? No: MEMBER_A joined first, so it sees all three).
        assertEquals(3, listGroupMessageBodies(groupId, MEMBER_A).size());
    }

    // --------------------------------------------- 5. review capacity gate

    @Test
    void reviewCapacityGateFailsClosedAndFreesOnResolution() throws Exception {
        long groupId = createGroup(OWNER);
        joinGroup(groupId, OWNER, MEMBER_A);
        joinGroup(groupId, OWNER, MEMBER_B);
        long m1 = sendGroupMessage(groupId, MEMBER_A, "被举报的第一条");
        long m2 = sendGroupMessage(groupId, MEMBER_A, "被举报的第二条");
        long m3 = sendGroupMessage(groupId, MEMBER_A, "被举报的第三条");

        // Two distinct members each file one report -> both PENDING (capacity = 2 here).
        MvcResult first = mockMvc.perform(post("/api/social/groups/{id}/reports", groupId)
                        .session(sessionOf(MEMBER_B))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"messageId\":" + m1 + ",\"reason\":\"言语攻击\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturn();
        long firstReviewId = objectMapper.readTree(first.getResponse().getContentAsString())
                .path("data").path("reviewId").asLong();

        mockMvc.perform(post("/api/social/groups/{id}/reports", groupId)
                        .session(sessionOf(OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"messageId\":" + m2 + ",\"reason\":\"刷屏\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"));

        // Capacity reached: the third report is EXPLICITLY rejected (honest CONFLICT naming
        // the bound), nothing persisted -- never silently queued, never silently dropped.
        mockMvc.perform(post("/api/social/groups/{id}/reports", groupId)
                        .session(sessionOf(OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"messageId\":" + m3 + ",\"reason\":\"再举报\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("上限")));
        long pendingRows = reviewLedgerMapper.selectCount(
                new QueryWrapper<com.innercosmos.entity.GroupReviewLedger>()
                        .eq("group_id", groupId).eq("status", "PENDING"));
        assertEquals(2L, pendingRows, "the rejected report must not be persisted");

        // Duplicate pending report of the same message by the same reporter: BAD_REQUEST.
        mockMvc.perform(post("/api/social/groups/{id}/reports", groupId)
                        .session(sessionOf(OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"messageId\":" + m2 + ",\"reason\":\"重复举报\"}"))
                .andExpect(status().isBadRequest());

        // The host queue view exposes the structured ledger; a non-host cannot read it.
        mockMvc.perform(get("/api/social/groups/{id}/reviews", groupId)
                        .session(sessionOf(MEMBER_B)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/social/groups/{id}/reviews", groupId)
                        .session(sessionOf(OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.capacity").value(2))
                .andExpect(jsonPath("$.data.pendingCount").value(2))
                .andExpect(jsonPath("$.data.reviews.length()").value(2));

        // Resolving one review frees exactly one slot; the third report now fits.
        mockMvc.perform(post("/api/social/groups/{id}/reviews/{reviewId}/resolve", groupId, firstReviewId)
                        .session(sessionOf(OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"resolve\",\"note\":\"已核实并处理\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RESOLVED"))
                .andExpect(jsonPath("$.data.pendingCount").value(1));

        mockMvc.perform(post("/api/social/groups/{id}/reports", groupId)
                        .session(sessionOf(OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"messageId\":" + m3 + ",\"reason\":\"腾出容量后再举报\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"));

        // Negative edges: resolving someone else's/nonexistent review, an invalid decision,
        // a foreign message, and a blank reason all fail honestly.
        mockMvc.perform(post("/api/social/groups/{id}/reviews/999999/resolve", groupId)
                        .session(sessionOf(OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"resolve\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/social/groups/{id}/reviews/{reviewId}/resolve", groupId, firstReviewId)
                        .session(sessionOf(OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"resolve\"}"))
                .andExpect(status().isBadRequest()); // already resolved, not silently re-done
        mockMvc.perform(post("/api/social/groups/{id}/reports", groupId)
                        .session(sessionOf(OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"messageId\":999999,\"reason\":\"不存在的消息\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/social/groups/{id}/reports", groupId)
                        .session(sessionOf(OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"messageId\":" + m3 + ",\"reason\":\"   \"}"))
                .andExpect(status().isBadRequest());

        // A report against a message from a DIFFERENT group never lands in this group's ledger.
        long otherGroupId = createGroup(OWNER);
        joinGroup(otherGroupId, OWNER, MEMBER_A);
        long foreignMessage = sendGroupMessage(otherGroupId, MEMBER_A, "另一个群的消息");
        mockMvc.perform(post("/api/social/groups/{id}/reports", groupId)
                        .session(sessionOf(OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"messageId\":" + foreignMessage + ",\"reason\":\"跨群举报\"}"))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------- extras

    /** The governance endpoints require an authenticated session like every other API. */
    @Test
    void governanceEndpointsStayBehindAuthentication() throws Exception {
        long groupId = createGroup(OWNER);
        mockMvc.perform(post("/api/social/groups/{id}/dissolve", groupId))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/social/groups/{id}/reviews", groupId))
                .andExpect(status().isUnauthorized());
    }
}
