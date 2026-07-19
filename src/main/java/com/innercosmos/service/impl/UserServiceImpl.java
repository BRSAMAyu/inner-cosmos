package com.innercosmos.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.common.Constants;
import com.innercosmos.common.ErrorCode;
import com.innercosmos.dto.LoginRequest;
import com.innercosmos.dto.RegisterRequest;
import com.innercosmos.entity.*;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.mapper.*;
import com.innercosmos.service.UserService;
import com.innercosmos.vo.UserProfileVO;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class UserServiceImpl implements UserService {
    private final UserMapper userMapper;
    private final UserProfileMapper userProfileMapper;
    private final MemoryCardMapper memoryCardMapper;
    private final DailyRecordMapper dailyRecordMapper;
    private final TodoItemMapper todoItemMapper;
    private final SlowLetterMapper slowLetterMapper;
    private final EchoCapsuleMapper echoCapsuleMapper;
    private final DialogSessionMapper dialogSessionMapper;
    private final DialogMessageMapper dialogMessageMapper;
    private final EmotionTraceMapper emotionTraceMapper;
    private final EmotionTimelineMapper emotionTimelineMapper;
    private final ThoughtFragmentMapper thoughtFragmentMapper;
    private final PersonaChatSessionMapper personaChatSessionMapper;
    private final PersonaChatMessageMapper personaChatMessageMapper;
    private final BeliefPatternMapper beliefPatternMapper;
    private final RelationMentionMapper relationMentionMapper;
    private final EventCardMapper eventCardMapper;
    private final MemoryThemeMapper memoryThemeMapper;
    private final VoiceTranscriptionMapper voiceTranscriptionMapper;
    private final WeeklyReviewMapper weeklyReviewMapper;
    private final FriendRelationMapper friendRelationMapper;
    private final LetterThreadMapper letterThreadMapper;
    private final SocialGroupMapper socialGroupMapper;
    private final SocialGroupMemberMapper socialGroupMemberMapper;
    private final BlockRelationMapper blockRelationMapper;
    private final SafetyEventMapper safetyEventMapper;
    private final AiInteractionLogMapper aiInteractionLogMapper;
    private final UserCorrectionMapper userCorrectionMapper;
    private final DialogSummaryMapper dialogSummaryMapper;
    private final ABTestMetricsMapper abTestMetricsMapper;
    private final ReportRecordMapper reportRecordMapper;
    // A5 right-to-erasure: the compiled derivatives + audit trail must be purged on account deletion.
    private final MemoryEmbeddingMapper memoryEmbeddingMapper;
    private final CapsuleEmbeddingMapper capsuleEmbeddingMapper;
    private final DataRetractionReceiptMapper dataRetractionReceiptMapper;
    private final PasswordEncoder passwordEncoder;

    public UserServiceImpl(UserMapper userMapper,
                           UserProfileMapper userProfileMapper,
                           MemoryCardMapper memoryCardMapper,
                           DailyRecordMapper dailyRecordMapper,
                           TodoItemMapper todoItemMapper,
                           SlowLetterMapper slowLetterMapper,
                           EchoCapsuleMapper echoCapsuleMapper,
                           DialogSessionMapper dialogSessionMapper,
                           DialogMessageMapper dialogMessageMapper,
                           EmotionTraceMapper emotionTraceMapper,
                           EmotionTimelineMapper emotionTimelineMapper,
                           ThoughtFragmentMapper thoughtFragmentMapper,
                           PersonaChatSessionMapper personaChatSessionMapper,
                           PersonaChatMessageMapper personaChatMessageMapper,
                           BeliefPatternMapper beliefPatternMapper,
                           RelationMentionMapper relationMentionMapper,
                           EventCardMapper eventCardMapper,
                           MemoryThemeMapper memoryThemeMapper,
                           VoiceTranscriptionMapper voiceTranscriptionMapper,
                           WeeklyReviewMapper weeklyReviewMapper,
                           FriendRelationMapper friendRelationMapper,
                           LetterThreadMapper letterThreadMapper,
                           SocialGroupMapper socialGroupMapper,
                           SocialGroupMemberMapper socialGroupMemberMapper,
                           BlockRelationMapper blockRelationMapper,
                           SafetyEventMapper safetyEventMapper,
                           AiInteractionLogMapper aiInteractionLogMapper,
                           UserCorrectionMapper userCorrectionMapper,
                           DialogSummaryMapper dialogSummaryMapper,
                           ABTestMetricsMapper abTestMetricsMapper,
                           ReportRecordMapper reportRecordMapper,
                           MemoryEmbeddingMapper memoryEmbeddingMapper,
                           CapsuleEmbeddingMapper capsuleEmbeddingMapper,
                           DataRetractionReceiptMapper dataRetractionReceiptMapper,
                           PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.userProfileMapper = userProfileMapper;
        this.memoryCardMapper = memoryCardMapper;
        this.dailyRecordMapper = dailyRecordMapper;
        this.todoItemMapper = todoItemMapper;
        this.slowLetterMapper = slowLetterMapper;
        this.echoCapsuleMapper = echoCapsuleMapper;
        this.dialogSessionMapper = dialogSessionMapper;
        this.dialogMessageMapper = dialogMessageMapper;
        this.emotionTraceMapper = emotionTraceMapper;
        this.emotionTimelineMapper = emotionTimelineMapper;
        this.thoughtFragmentMapper = thoughtFragmentMapper;
        this.personaChatSessionMapper = personaChatSessionMapper;
        this.personaChatMessageMapper = personaChatMessageMapper;
        this.beliefPatternMapper = beliefPatternMapper;
        this.relationMentionMapper = relationMentionMapper;
        this.eventCardMapper = eventCardMapper;
        this.memoryThemeMapper = memoryThemeMapper;
        this.voiceTranscriptionMapper = voiceTranscriptionMapper;
        this.weeklyReviewMapper = weeklyReviewMapper;
        this.friendRelationMapper = friendRelationMapper;
        this.letterThreadMapper = letterThreadMapper;
        this.socialGroupMapper = socialGroupMapper;
        this.socialGroupMemberMapper = socialGroupMemberMapper;
        this.blockRelationMapper = blockRelationMapper;
        this.safetyEventMapper = safetyEventMapper;
        this.aiInteractionLogMapper = aiInteractionLogMapper;
        this.userCorrectionMapper = userCorrectionMapper;
        this.dialogSummaryMapper = dialogSummaryMapper;
        this.abTestMetricsMapper = abTestMetricsMapper;
        this.reportRecordMapper = reportRecordMapper;
        this.memoryEmbeddingMapper = memoryEmbeddingMapper;
        this.capsuleEmbeddingMapper = capsuleEmbeddingMapper;
        this.dataRetractionReceiptMapper = dataRetractionReceiptMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public User register(RegisterRequest request) {
        QueryWrapper<User> query = new QueryWrapper<>();
        query.eq("username", request.username);
        if (userMapper.selectCount(query) > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该用户名已被注册");
        }
        User user = new User();
        user.username = request.username;
        user.passwordHash = passwordEncoder.encode(request.password);
        user.nickname = request.nickname == null || request.nickname.isBlank() ? request.username : request.nickname;
        user.email = request.email;
        user.role = Constants.ROLE_USER;
        user.status = Constants.STATUS_ACTIVE;
        userMapper.insert(user);
        return user;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public User login(LoginRequest request) {
        QueryWrapper<User> query = new QueryWrapper<>();
        query.eq("username", request.username);
        User user = userMapper.selectOne(query);
        if (user == null || !passwordEncoder.matches(request.password, user.passwordHash)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户名或密码不正确");
        }
        if (!Constants.STATUS_ACTIVE.equals(user.status)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "该账户已被停用");
        }
        user.lastLoginAt = LocalDateTime.now();
        userMapper.updateById(user);
        if (request.timezone != null && !request.timezone.isBlank()) {
            UserProfileVO timezoneOnly = new UserProfileVO();
            timezoneOnly.timezone = request.timezone;
            updateProfile(user.id, timezoneOnly);
        }
        return user;
    }

    @Override
    public User current(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "尚未登录");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "尚未登录");
        }
        return user;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        // M-032: change the current user's password, requiring the old one.
        User user = userMapper.selectById(userId);
        if (user == null || oldPassword == null || oldPassword.isBlank()
                || !passwordEncoder.matches(oldPassword, user.passwordHash)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "原密码不正确");
        }
        if (newPassword == null || newPassword.length() < 8) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "新密码至少 8 位");
        }
        user.passwordHash = passwordEncoder.encode(newPassword);
        userMapper.updateById(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProfile(Long userId, UserProfileVO profile) {
        QueryWrapper<UserProfile> query = new QueryWrapper<>();
        query.eq("user_id", userId);
        UserProfile existing = userProfileMapper.selectOne(query);

        if (existing == null) {
            existing = new UserProfile();
            existing.userId = userId;
        }

        if (profile.auroraName != null) existing.auroraName = profile.auroraName;
        if (profile.auroraTone != null) existing.auroraTone = profile.auroraTone;
        if (profile.preferredInputType != null) existing.preferredInputType = profile.preferredInputType;
        if (profile.socialReachabilityStatus != null) existing.socialReachabilityStatus = profile.socialReachabilityStatus;
        if (profile.bio != null) existing.bio = profile.bio;
        if (profile.reflectionDepth != null) existing.reflectionDepth = profile.reflectionDepth;
        if (profile.allowMemoryRecall != null) existing.allowMemoryRecall = profile.allowMemoryRecall;
        if (profile.quietHoursStart != null) existing.quietHoursStart = profile.quietHoursStart;
        if (profile.quietHoursEnd != null) existing.quietHoursEnd = profile.quietHoursEnd;
        if (profile.proactiveSensitivity != null) {
            int sens = Math.max(1, Math.min(5, profile.proactiveSensitivity));
            existing.proactiveSensitivity = sens;
            // Make the 1-5 slider actually drive the proactive engine: it was a dead signal
            // (stored but never read). Map it onto the intensity level the engine consumes.
            existing.proactiveIntensity = switch (sens) {
                case 1 -> "WHISPER";
                case 2 -> "LIGHT";
                case 3 -> "ACTIVE";
                case 4 -> "COMPANION";
                default -> "ALIVE"; // 5
            };
        }
        if (profile.allowMultiMessage != null) existing.allowMultiMessage = profile.allowMultiMessage;
        if (profile.focusModeEnabled != null) existing.focusModeEnabled = profile.focusModeEnabled;
        if (profile.focusWindowsJson != null) existing.focusWindowsJson = profile.focusWindowsJson;
        if (profile.currentEnvironmentLabel != null) existing.currentEnvironmentLabel = profile.currentEnvironmentLabel;
        if (profile.weatherAwarenessEnabled != null) existing.weatherAwarenessEnabled = profile.weatherAwarenessEnabled;
        if (profile.timeAwarenessEnabled != null) existing.timeAwarenessEnabled = profile.timeAwarenessEnabled;
        if (profile.timezone != null) {
            try {
                existing.timezone = java.time.ZoneId.of(profile.timezone).getId();
            } catch (java.time.DateTimeException invalid) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "timezone 必须是有效的 IANA 时区");
            }
        }

        if (existing.id == null) {
            userProfileMapper.insert(existing);
        } else {
            userProfileMapper.updateById(existing);
        }
    }

    @Override
    public Map<String, Object> exportData(Long userId) {
        Map<String, Object> data = new HashMap<>();

        // User profile
        QueryWrapper<UserProfile> upQuery = new QueryWrapper<>();
        upQuery.eq("user_id", userId);
        UserProfile userProfile = userProfileMapper.selectOne(upQuery);
        data.put("userProfile", userProfile);

        // Dialog sessions and messages
        QueryWrapper<DialogSession> dsQuery = new QueryWrapper<>();
        dsQuery.eq("user_id", userId);
        List<DialogSession> dialogSessions = dialogSessionMapper.selectList(dsQuery);
        data.put("dialogSessions", dialogSessions);

        // Get all dialog message IDs from sessions
        List<Long> sessionIds = dialogSessions.stream().map(session -> session.id).toList();
        if (!sessionIds.isEmpty()) {
            QueryWrapper<DialogMessage> dmQuery = new QueryWrapper<>();
            dmQuery.in("session_id", sessionIds);
            List<DialogMessage> dialogMessages = dialogMessageMapper.selectList(dmQuery);
            data.put("dialogMessages", dialogMessages);
        } else {
            data.put("dialogMessages", List.of());
        }

        // Memory cards
        QueryWrapper<MemoryCard> mcQuery = new QueryWrapper<>();
        mcQuery.eq("user_id", userId);
        List<MemoryCard> memoryCards = memoryCardMapper.selectList(mcQuery);
        data.put("memoryCards", memoryCards);

        // Daily records
        QueryWrapper<DailyRecord> drQuery = new QueryWrapper<>();
        drQuery.eq("user_id", userId);
        List<DailyRecord> dailyRecords = dailyRecordMapper.selectList(drQuery);
        data.put("dailyRecords", dailyRecords);

        // Todo items
        QueryWrapper<TodoItem> tiQuery = new QueryWrapper<>();
        tiQuery.eq("user_id", userId);
        List<TodoItem> todos = todoItemMapper.selectList(tiQuery);
        data.put("todos", todos);

        // Slow letters
        QueryWrapper<SlowLetter> slQuery = new QueryWrapper<>();
        slQuery.eq("sender_user_id", userId).or().eq("receiver_user_id", userId);
        List<SlowLetter> letters = slowLetterMapper.selectList(slQuery);
        data.put("letters", letters);

        // Echo capsules
        QueryWrapper<EchoCapsule> ecQuery = new QueryWrapper<>();
        ecQuery.eq("owner_user_id", userId);
        List<EchoCapsule> capsules = echoCapsuleMapper.selectList(ecQuery);
        data.put("capsules", capsules);

        // Emotion traces
        QueryWrapper<EmotionTrace> etQuery = new QueryWrapper<>();
        etQuery.eq("user_id", userId);
        List<EmotionTrace> emotionTraces = emotionTraceMapper.selectList(etQuery);
        data.put("emotionTraces", emotionTraces);

        // Emotion timeline
        QueryWrapper<EmotionTimeline> etlQuery = new QueryWrapper<>();
        etlQuery.eq("user_id", userId);
        List<EmotionTimeline> emotionTimeline = emotionTimelineMapper.selectList(etlQuery);
        data.put("emotionTimeline", emotionTimeline);

        // Thought fragments
        QueryWrapper<ThoughtFragment> tfQuery = new QueryWrapper<>();
        tfQuery.eq("user_id", userId);
        List<ThoughtFragment> thoughtFragments = thoughtFragmentMapper.selectList(tfQuery);
        data.put("thoughtFragments", thoughtFragments);

        return data;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteAccount(Long userId) {
        // Persona chat messages and sessions
        List<PersonaChatSession> personaSessions = personaChatSessionMapper.selectList(
                new QueryWrapper<PersonaChatSession>().eq("visitor_user_id", userId));
        for (PersonaChatSession ps : personaSessions) {
            personaChatMessageMapper.delete(new QueryWrapper<PersonaChatMessage>().eq("session_id", ps.id));
        }
        personaChatSessionMapper.delete(new QueryWrapper<PersonaChatSession>().eq("visitor_user_id", userId));

        // Belief patterns
        beliefPatternMapper.delete(new QueryWrapper<BeliefPattern>().eq("user_id", userId));

        // Relation mentions
        relationMentionMapper.delete(new QueryWrapper<RelationMention>().eq("user_id", userId));

        // Event cards
        eventCardMapper.delete(new QueryWrapper<EventCard>().eq("user_id", userId));

        // Memory themes
        memoryThemeMapper.delete(new QueryWrapper<MemoryTheme>().eq("user_id", userId));

        // Voice transcriptions
        voiceTranscriptionMapper.delete(new QueryWrapper<VoiceTranscription>().eq("user_id", userId));

        // Weekly reviews
        weeklyReviewMapper.delete(new QueryWrapper<WeeklyReview>().eq("user_id", userId));

        // User corrections
        userCorrectionMapper.delete(new QueryWrapper<UserCorrection>().eq("user_id", userId));

        // Dialog summaries (by session)
        List<DialogSession> sessions = dialogSessionMapper.selectList(
                new QueryWrapper<DialogSession>().eq("user_id", userId));
        for (DialogSession session : sessions) {
            dialogSummaryMapper.delete(new QueryWrapper<DialogSummary>().eq("session_id", session.id));
        }

        // Thought fragments, emotion timeline, emotion traces
        thoughtFragmentMapper.delete(new QueryWrapper<ThoughtFragment>().eq("user_id", userId));
        emotionTimelineMapper.delete(new QueryWrapper<EmotionTimeline>().eq("user_id", userId));
        emotionTraceMapper.delete(new QueryWrapper<EmotionTrace>().eq("user_id", userId));

        // Dialog messages then sessions (FK cascade also covers messages)
        for (DialogSession session : sessions) {
            dialogMessageMapper.delete(new QueryWrapper<DialogMessage>().eq("session_id", session.id));
        }
        dialogSessionMapper.delete(new QueryWrapper<DialogSession>().eq("user_id", userId));

        // Memory cards (FK cascade deletes thought fragments, but already cleaned above)
        memoryCardMapper.delete(new QueryWrapper<MemoryCard>().eq("user_id", userId));

        // Daily records, todos
        dailyRecordMapper.delete(new QueryWrapper<DailyRecord>().eq("user_id", userId));
        todoItemMapper.delete(new QueryWrapper<TodoItem>().eq("user_id", userId));

        // Slow letters (FK cascade deletes letter_status_log)
        slowLetterMapper.delete(new QueryWrapper<SlowLetter>()
                .eq("sender_user_id", userId).or().eq("receiver_user_id", userId));

        // Letter threads
        letterThreadMapper.delete(new QueryWrapper<LetterThread>()
                .eq("participant_a", userId).or().eq("participant_b", userId));

        // A5 right-to-erasure — compiled derivatives that have no FK cascade must be purged explicitly,
        // or account deletion would orphan the user's matching/retrieval vectors and audit trail.
        // Capsule matching vectors are keyed by capsule_id, so collect ids BEFORE deleting the capsules.
        List<Long> ownedCapsuleIds = echoCapsuleMapper
                .selectList(new QueryWrapper<EchoCapsule>().select("id").eq("owner_user_id", userId))
                .stream().map(c -> c.id).toList();
        if (!ownedCapsuleIds.isEmpty()) {
            capsuleEmbeddingMapper.delete(new QueryWrapper<CapsuleEmbedding>().in("capsule_id", ownedCapsuleIds));
        }
        memoryEmbeddingMapper.delete(new QueryWrapper<MemoryEmbedding>().eq("user_id", userId));
        dataRetractionReceiptMapper.delete(new QueryWrapper<DataRetractionReceipt>().eq("user_id", userId));

        // Echo capsules (FK cascade deletes capsule_boundary, authorized_memory_ref)
        echoCapsuleMapper.delete(new QueryWrapper<EchoCapsule>().eq("owner_user_id", userId));

        // Friend relations
        friendRelationMapper.delete(new QueryWrapper<FriendRelation>()
                .eq("requester_id", userId).or().eq("addressee_id", userId));

        // Block relations
        blockRelationMapper.delete(new QueryWrapper<BlockRelation>()
                .eq("blocker_user_id", userId).or().eq("blocked_user_id", userId));

        // Social groups and memberships
        socialGroupMemberMapper.delete(new QueryWrapper<SocialGroupMember>().eq("user_id", userId));
        socialGroupMapper.delete(new QueryWrapper<SocialGroup>().eq("owner_user_id", userId));

        // Safety events, AI logs, A/B test metrics
        safetyEventMapper.delete(new QueryWrapper<SafetyEvent>().eq("user_id", userId));
        aiInteractionLogMapper.delete(new QueryWrapper<AiInteractionLog>().eq("user_id", userId));
        abTestMetricsMapper.delete(new QueryWrapper<ABTestMetrics>().eq("user_id", userId));

        // Report records (filed by this user)
        reportRecordMapper.delete(new QueryWrapper<ReportRecord>().eq("reporter_user_id", userId));

        // User profile
        userProfileMapper.delete(new QueryWrapper<UserProfile>().eq("user_id", userId));

        // Finally delete user account
        userMapper.deleteById(userId);
    }

}
