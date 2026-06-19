package com.innercosmos.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.ai.agent.LetterGuardAgent;
import com.innercosmos.dto.LetterCreateRequest;
import com.innercosmos.entity.LetterStatusLog;
import com.innercosmos.entity.LetterThread;
import com.innercosmos.entity.ReportRecord;
import com.innercosmos.entity.SlowLetter;
import com.innercosmos.exception.SafetyBlockedException;
import com.innercosmos.letterstate.LetterStateRegistry;
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

    public SlowLetterServiceImpl(SlowLetterMapper letterMapper, LetterStatusLogMapper logMapper, LetterStateRegistry stateRegistry, LetterGuardAgent guardAgent, LetterThreadMapper threadMapper, ReportRecordMapper reportRecordMapper, LetterSafetyFilter letterSafetyFilter) {
        this.letterMapper = letterMapper;
        this.logMapper = logMapper;
        this.stateRegistry = stateRegistry;
        this.guardAgent = guardAgent;
        this.threadMapper = threadMapper;
        this.reportRecordMapper = reportRecordMapper;
        this.letterSafetyFilter = letterSafetyFilter;
    }

    @Override
    public SlowLetter draft(Long userId, LetterCreateRequest request) {
        if (!guardAgent.allow(request.letterBody)) {
            throw new SafetyBlockedException("letter contains unsafe content");
        }
        SlowLetter letter = new SlowLetter();
        letter.senderUserId = userId;
        letter.receiverUserId = request.receiverUserId;
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
        letter.status = targetStatus;
        if ("SENT".equals(targetStatus)) letter.sentAt = LocalDateTime.now();
        if ("DELIVERED".equals(targetStatus)) letter.deliveredAt = LocalDateTime.now();
        if ("READ".equals(targetStatus)) letter.readAt = LocalDateTime.now();
        if ("REPLIED".equals(targetStatus)) letter.repliedAt = LocalDateTime.now();
        letterMapper.updateById(letter);

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
        query.eq("receiver_user_id", userId).orderByDesc("id");
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
