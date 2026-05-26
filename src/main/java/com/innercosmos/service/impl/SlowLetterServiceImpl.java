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
    public SlowLetter replyWithLetter(Long userId, Long letterId, LetterCreateRequest request) {
        SlowLetter original = letterMapper.selectById(letterId);
        if (original == null) {
            throw new com.innercosmos.exception.BusinessException(com.innercosmos.common.ErrorCode.NOT_FOUND, "原信件不存在");
        }
        if (!guardAgent.allow(request.letterBody)) {
            throw new SafetyBlockedException("letter contains unsafe content");
        }
        SlowLetter reply = new SlowLetter();
        reply.senderUserId = userId;
        reply.receiverUserId = original.senderUserId;
        reply.receiverCapsuleId = original.receiverCapsuleId;
        reply.title = request.title == null ? "回复：" + original.title : request.title;
        reply.letterBody = request.letterBody;
        reply.status = "DRAFT";
        reply.parallaxDistance = 3;
        reply.estimatedArrivalAt = LocalDateTime.now().plusMinutes(3);
        letterMapper.insert(reply);

        LetterThread thread = new LetterThread();
        thread.firstLetterId = original.id;
        thread.participantA = original.senderUserId;
        thread.participantB = userId;
        thread.capsuleId = original.receiverCapsuleId;
        thread.status = "ACTIVE";
        thread.lastLetterAt = LocalDateTime.now();
        threadMapper.insert(thread);
        return reply;
    }

    @Override
    public List<LetterThread> listThreads(Long userId) {
        QueryWrapper<LetterThread> query = new QueryWrapper<>();
        query.eq("participant_a", userId).or().eq("participant_b", userId).orderByDesc("id");
        return threadMapper.selectList(query);
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
            return "信件内容未通过安全检查，建议修改：" + result.reason;
        }
        return null;
    }
}
