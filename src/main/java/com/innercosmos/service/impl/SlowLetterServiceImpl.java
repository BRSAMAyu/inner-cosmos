package com.innercosmos.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.ai.agent.LetterGuardAgent;
import com.innercosmos.dto.LetterCreateRequest;
import com.innercosmos.entity.BlockRelation;
import com.innercosmos.entity.EchoCapsule;
import com.innercosmos.entity.LetterStatusLog;
import com.innercosmos.entity.LetterThread;
import com.innercosmos.entity.ReportRecord;
import com.innercosmos.entity.SlowLetter;
import com.innercosmos.exception.SafetyBlockedException;
import com.innercosmos.letterstate.LetterStateRegistry;
import com.innercosmos.mapper.BlockRelationMapper;
import com.innercosmos.mapper.EchoCapsuleMapper;
import com.innercosmos.mapper.LetterStatusLogMapper;
import com.innercosmos.mapper.LetterThreadMapper;
import com.innercosmos.mapper.ReportRecordMapper;
import com.innercosmos.mapper.SlowLetterMapper;
import com.innercosmos.service.LetterSafetyFilter;
import com.innercosmos.service.SlowLetterService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class SlowLetterServiceImpl implements SlowLetterService {
    private final SlowLetterMapper letterMapper;
    private final LetterStatusLogMapper logMapper;
    private final LetterStateRegistry stateRegistry;
    private final LetterGuardAgent guardAgent;
    private final LetterThreadMapper threadMapper;
    private final ReportRecordMapper reportRecordMapper;
    private final LetterSafetyFilter letterSafetyFilter;
    private final EchoCapsuleMapper capsuleMapper;
    private final BlockRelationMapper blockRelationMapper;

    public SlowLetterServiceImpl(SlowLetterMapper letterMapper, LetterStatusLogMapper logMapper, LetterStateRegistry stateRegistry, LetterGuardAgent guardAgent, LetterThreadMapper threadMapper, ReportRecordMapper reportRecordMapper, LetterSafetyFilter letterSafetyFilter, EchoCapsuleMapper capsuleMapper, BlockRelationMapper blockRelationMapper) {
        this.letterMapper = letterMapper;
        this.logMapper = logMapper;
        this.stateRegistry = stateRegistry;
        this.guardAgent = guardAgent;
        this.threadMapper = threadMapper;
        this.reportRecordMapper = reportRecordMapper;
        this.letterSafetyFilter = letterSafetyFilter;
        this.capsuleMapper = capsuleMapper;
        this.blockRelationMapper = blockRelationMapper;
    }

    @Override
    public SlowLetter draft(Long userId, LetterCreateRequest request) {
        if (!guardAgent.allow(request.letterBody)) {
            throw new SafetyBlockedException("letter contains unsafe content");
        }
        Long receiverUserId = request.receiverUserId;
        if (request.receiverCapsuleId != null) {
            EchoCapsule capsule = capsuleMapper.selectById(request.receiverCapsuleId);
            if (capsule == null || !Boolean.TRUE.equals(capsule.isPublic)
                    || !"PUBLIC".equals(capsule.visibilityStatus)) {
                throw new com.innercosmos.exception.BusinessException(
                        com.innercosmos.common.ErrorCode.NOT_FOUND, "这个共鸣体当前不能接收慢信");
            }
            if (capsule.ownerUserId == null) {
                throw new com.innercosmos.exception.BusinessException(
                        com.innercosmos.common.ErrorCode.NOT_FOUND, "官方种子共鸣体没有真人收件人");
            }
            if (receiverUserId != null && !capsule.ownerUserId.equals(receiverUserId)) {
                throw new com.innercosmos.exception.BusinessException(
                        com.innercosmos.common.ErrorCode.BAD_REQUEST, "慢信收件人与共鸣体授权者不一致");
            }
            receiverUserId = capsule.ownerUserId;
        }
        if (receiverUserId == null || userId.equals(receiverUserId)) {
            throw new com.innercosmos.exception.BusinessException(
                    com.innercosmos.common.ErrorCode.BAD_REQUEST, "请选择可以接收慢信的共鸣者");
        }
        SlowLetter letter = new SlowLetter();
        letter.senderUserId = userId;
        letter.receiverUserId = receiverUserId;
        letter.receiverCapsuleId = request.receiverCapsuleId;
        letter.title = request.title;
        letter.letterBody = request.letterBody;
        letter.status = "DRAFT";
        letter.parallaxDistance = 3;
        letter.estimatedArrivalAt = LocalDateTime.now().plusMinutes(3);
        letterMapper.insert(letter);
        return letter;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SlowLetter transition(Long userId, Long id, String targetStatus) {
        SlowLetter letter = letterMapper.selectById(id);
        if (letter == null) {
            throw new com.innercosmos.exception.BusinessException(com.innercosmos.common.ErrorCode.NOT_FOUND, "信件不存在");
        }
        boolean isSender = userId.equals(letter.senderUserId);
        boolean isReceiver = userId.equals(letter.receiverUserId);
        if (!isSender && !isReceiver) {
            throw new com.innercosmos.exception.BusinessException(com.innercosmos.common.ErrorCode.UNAUTHORIZED, "无权操作此信件");
        }
        stateRegistry.validate(letter.status, targetStatus);
        String from = letter.status;
        // M-022: atomic conditional UPDATE — only applies if status is still `from`, so a concurrent
        // transition (e.g. user /read racing the scheduler SENT->DELIVERED) cannot both pass
        // validate() and clobber each other. rowsAffected==0 means we lost the race.
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<SlowLetter> w =
                new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<SlowLetter>()
                        .eq("id", id).eq("status", from)
                        .set("status", targetStatus);
        if ("SENT".equals(targetStatus)) w.set("sent_at", now);
        if ("DELIVERED".equals(targetStatus)) w.set("delivered_at", now);
        if ("READ".equals(targetStatus)) w.set("read_at", now);
        if ("REPLIED".equals(targetStatus)) w.set("replied_at", now);
        int updated = letterMapper.update(null, w);
        if (updated == 0) {
            throw new com.innercosmos.exception.BusinessException(
                    com.innercosmos.common.ErrorCode.LETTER_STATE_INVALID, "信件状态刚刚变化，请刷新后重试");
        }
        letter.status = targetStatus;
        if ("SENT".equals(targetStatus)) letter.sentAt = now;
        if ("DELIVERED".equals(targetStatus)) letter.deliveredAt = now;
        if ("READ".equals(targetStatus)) letter.readAt = now;
        if ("REPLIED".equals(targetStatus)) letter.repliedAt = now;

        if ("BLOCKED".equals(targetStatus) && isReceiver) {
            Long existing = blockRelationMapper.selectCount(new QueryWrapper<BlockRelation>()
                    .eq("blocker_user_id", userId).eq("blocked_user_id", letter.senderUserId));
            if (existing == null || existing == 0L) {
                BlockRelation relation = new BlockRelation();
                relation.blockerUserId = userId;
                relation.blockedUserId = letter.senderUserId;
                relation.reason = "SLOW_LETTER_BLOCK";
                blockRelationMapper.insert(relation);
            }
        }

        LetterStatusLog log = new LetterStatusLog();
        log.letterId = id;
        log.fromStatus = from;
        log.toStatus = targetStatus;
        log.operatorUserId = userId;
        log.reason = "API transition";
        logMapper.insert(log);
        return letter;
    }

    @Override
    public List<SlowLetter> inbox(Long userId) {
        QueryWrapper<SlowLetter> query = new QueryWrapper<>();
        query.eq("receiver_user_id", userId)
                .in("status", "DELIVERED", "READ", "REPLIED", "DECLINED", "BLOCKED", "ARCHIVED")
                .orderByDesc("id");
        return letterMapper.selectList(query);
    }

    @Override
    public List<SlowLetter> outbox(Long userId) {
        QueryWrapper<SlowLetter> query = new QueryWrapper<>();
        query.eq("sender_user_id", userId).orderByDesc("id");
        return letterMapper.selectList(query);
    }

    @Override
    public SlowLetter getLetter(Long userId, Long letterId) {
        SlowLetter letter = letterMapper.selectById(letterId);
        if (letter == null) {
            throw new com.innercosmos.exception.BusinessException(com.innercosmos.common.ErrorCode.NOT_FOUND, "信件不存在");
        }
        boolean isSender = userId.equals(letter.senderUserId);
        boolean isReceiver = userId.equals(letter.receiverUserId);
        if (!isSender && !isReceiver) {
            throw new com.innercosmos.exception.BusinessException(com.innercosmos.common.ErrorCode.UNAUTHORIZED, "无权查看此信件");
        }
        return letter;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SlowLetter replyWithLetter(Long userId, Long letterId, LetterCreateRequest request) {
        SlowLetter original = letterMapper.selectById(letterId);
        if (original == null) {
            throw new com.innercosmos.exception.BusinessException(com.innercosmos.common.ErrorCode.NOT_FOUND, "原信件不存在");
        }
        if (!userId.equals(original.receiverUserId)) {
            throw new com.innercosmos.exception.BusinessException(com.innercosmos.common.ErrorCode.UNAUTHORIZED, "只有收件人可以回复此信件");
        }
        if (!guardAgent.allow(request.letterBody)) {
            throw new SafetyBlockedException("letter contains unsafe content");
        }

        // Resolve the conversation BEFORE inserting so the reply (and, on first reply,
        // the original) can be stamped with the shared thread id. Replies join an
        // existing thread instead of orphaning a fresh one each round.
        LetterThread thread = resolveThread(original, userId);

        SlowLetter reply = new SlowLetter();
        reply.senderUserId = userId;
        reply.receiverUserId = original.senderUserId;
        reply.receiverCapsuleId = original.receiverCapsuleId;
        reply.threadId = thread.id;
        reply.title = request.title == null ? "回复:" + original.title : request.title;
        reply.letterBody = request.letterBody;
        reply.status = "DRAFT";
        reply.parallaxDistance = 3;
        reply.estimatedArrivalAt = LocalDateTime.now().plusMinutes(3);
        letterMapper.insert(reply);

        // Back-fill the original so the whole conversation is walkable by thread id.
        if (original.threadId == null) {
            original.threadId = thread.id;
            letterMapper.updateById(original);
        }

        thread.lastLetterAt = LocalDateTime.now();
        threadMapper.updateById(thread);
        return reply;
    }

    /**
     * Find-or-create the {@link LetterThread} a reply belongs to. Reuse precedence:
     *  1. the original letter's own thread id, if already set;
     *  2. an existing thread for the SAME unordered participant pair + capsule;
     *  3. otherwise a brand-new ACTIVE thread.
     */
    private LetterThread resolveThread(SlowLetter original, Long replier) {
        if (original.threadId != null) {
            LetterThread existing = threadMapper.selectById(original.threadId);
            if (existing != null) {
                return existing;
            }
        }
        Long a = original.senderUserId;
        Long b = replier;
        QueryWrapper<LetterThread> q = new QueryWrapper<>();
        q.and(w -> w.eq("participant_a", a).eq("participant_b", b)
                .or(o -> o.eq("participant_a", b).eq("participant_b", a)));
        if (original.receiverCapsuleId != null) {
            q.eq("capsule_id", original.receiverCapsuleId);
        } else {
            q.isNull("capsule_id");
        }
        q.last("LIMIT 1");
        List<LetterThread> found = threadMapper.selectList(q);
        if (found != null && !found.isEmpty()) {
            return found.get(0);
        }
        LetterThread thread = new LetterThread();
        thread.firstLetterId = original.id;
        thread.participantA = a;
        thread.participantB = b;
        thread.capsuleId = original.receiverCapsuleId;
        thread.status = "ACTIVE";
        thread.lastLetterAt = LocalDateTime.now();
        threadMapper.insert(thread);
        return thread;
    }

    @Override
    public List<LetterThread> listThreads(Long userId) {
        QueryWrapper<LetterThread> query = new QueryWrapper<>();
        query.eq("participant_a", userId).or().eq("participant_b", userId).orderByDesc("id");
        return threadMapper.selectList(query);
    }

    @Override
    public List<SlowLetter> getThreadLetters(Long userId, Long threadId) {
        LetterThread thread = threadMapper.selectById(threadId);
        if (thread == null) {
            throw new com.innercosmos.exception.BusinessException(com.innercosmos.common.ErrorCode.NOT_FOUND, "对话不存在");
        }
        if (!userId.equals(thread.participantA) && !userId.equals(thread.participantB)) {
            throw new com.innercosmos.exception.BusinessException(com.innercosmos.common.ErrorCode.UNAUTHORIZED, "无权查看此对话");
        }
        QueryWrapper<SlowLetter> query = new QueryWrapper<>();
        // thread_id matches every reply; the anchor (first) letter may predate the back-fill.
        query.and(w -> w.eq("thread_id", threadId).or().eq("id", thread.firstLetterId));
        query.orderByAsc("id");
        return letterMapper.selectList(query);
    }

    @Override
    public void reportLetter(Long userId, Long letterId, String reason) {
        SlowLetter letter = letterMapper.selectById(letterId);
        if (letter == null) {
            throw new com.innercosmos.exception.BusinessException(com.innercosmos.common.ErrorCode.NOT_FOUND, "信件不存在");
        }
        // M-078: only a party to the letter (sender or receiver) may report it.
        if (!userId.equals(letter.senderUserId) && !userId.equals(letter.receiverUserId)) {
            throw new com.innercosmos.exception.BusinessException(com.innercosmos.common.ErrorCode.UNAUTHORIZED, "无权举报此信件");
        }
        ReportRecord report = new ReportRecord();
        report.reporterUserId = userId;
        report.targetType = "LETTER";
        report.targetId = letterId;
        report.reason = reason;
        report.status = "PENDING";
        reportRecordMapper.insert(report);
    }

    @Override
    public String requestRewrite(Long userId, Long letterId) {
        SlowLetter letter = letterMapper.selectById(letterId);
        if (letter == null) {
            throw new com.innercosmos.exception.BusinessException(com.innercosmos.common.ErrorCode.NOT_FOUND, "信件不存在");
        }
        boolean isSender = userId.equals(letter.senderUserId);
        if (!isSender) {
            throw new com.innercosmos.exception.BusinessException(com.innercosmos.common.ErrorCode.UNAUTHORIZED, "无权操作此信件");
        }
        LetterSafetyFilter.FilterResult result = letterSafetyFilter.filter(letter.letterBody, userId, letter.receiverUserId);
        if (!result.passed) {
            return "信件内容未通过安全检查,建议修改:" + result.reason;
        }
        // The letter is safe — still offer gentle coaching rather than a bare null,
        // so the "请帮我润色" affordance always returns something usable to the writer.
        if (result.suggestion != null && !result.suggestion.isBlank()) {
            return result.suggestion;
        }
        return "这封信读起来已经足够真诚了。如果想再打磨,可以把最在意的那句话放到结尾,让它慢慢落进对方心里。";
    }
}
