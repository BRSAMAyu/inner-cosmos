package com.innercosmos.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.innercosmos.ai.agent.LetterGuardAgent;
import com.innercosmos.ai.tts.TtsClient;
import com.innercosmos.ai.tts.TtsVoicePresets;
import com.innercosmos.common.ErrorCode;
import com.innercosmos.dto.LetterCreateRequest;
import com.innercosmos.entity.BlockRelation;
import com.innercosmos.entity.EchoCapsule;
import com.innercosmos.entity.LetterStatusLog;
import com.innercosmos.entity.LetterThread;
import com.innercosmos.entity.ReportRecord;
import com.innercosmos.entity.SlowLetter;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.exception.SafetyBlockedException;
import com.innercosmos.letterstate.LetterStateRegistry;
import com.innercosmos.mapper.BlockRelationMapper;
import com.innercosmos.mapper.EchoCapsuleMapper;
import com.innercosmos.mapper.LetterStatusLogMapper;
import com.innercosmos.mapper.LetterThreadMapper;
import com.innercosmos.mapper.ReportRecordMapper;
import com.innercosmos.mapper.SlowLetterMapper;
import com.innercosmos.safety.PiiCredentialDetector;
import com.innercosmos.service.LetterSafetyFilter;
import com.innercosmos.service.SlowLetterService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class SlowLetterServiceImpl implements SlowLetterService {
    /**
     * Gemini audit 1.7 (PARTIAL/P1): the "3 minutes" flight duration used to be a bare magic
     * number inlined at every estimatedArrivalAt call site. It is a deliberate, explainable
     * parallax-flight policy (matching LetterDeliveryJob's own comment: "gives the letter a
     * flight duration"), not an arbitrary constant, so it is named and defined once here.
     */
    static final Duration PARALLAX_FLIGHT_DURATION = Duration.ofMinutes(3);

    private final SlowLetterMapper letterMapper;
    private final LetterStatusLogMapper logMapper;
    private final LetterStateRegistry stateRegistry;
    private final LetterGuardAgent guardAgent;
    private final LetterThreadMapper threadMapper;
    private final ReportRecordMapper reportRecordMapper;
    private final LetterSafetyFilter letterSafetyFilter;
    private final EchoCapsuleMapper capsuleMapper;
    private final BlockRelationMapper blockRelationMapper;
    // Gemini audit 3.3 (CONFIRMED/P1): detect-and-gate for credentials/PII at SEND time. Never
    // rewrites/redacts the letter body -- see the SENT branch of transition() for the hard-block
    // vs. soft-confirm handling.
    private final PiiCredentialDetector piiCredentialDetector;
    // Gemini audit 1.7 (PARTIAL/P1): the only clock this class advances on. Constructor-injected
    // (Spring wires the Clock.systemUTC() @Bean from ClockConfig in production); tests inject a
    // fixed/adjustable Clock directly instead of racing the wall clock. No method in this class
    // calls LocalDateTime.now() with an implicit zone anymore.
    private final Clock clock;
    /**
     * W1 slow-letter voice reuse: the same {@link TtsClient} Aurora's inner-voice and the capsule
     * persona-voice paths use, field-injected (optional) exactly like
     * {@code PersonaChatServiceImpl#ttsClient} -- Spring always wires a real bean
     * ({@code QwenAudioTtsClient} or {@code DisabledTtsClient}), so this is non-null in production
     * and only null in direct-construction unit tests. Field injection (not constructor) is
     * deliberate: it keeps this class's heavily-audited 11-arg constructor signature stable.
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private TtsClient ttsClient;

    /**
     * The statuses that mean "this letter has been delivered to the recipient and is readable by
     * them" -- exactly the set {@link #inbox(Long)} selects for the receiver. Reused as the voice
     * delivery-state gate so "hearable" is defined by the SAME existing delivered-to-recipient
     * boundary the inbox already enforces, not a new one. Pre-delivery states (DRAFT/SENT/FLYING)
     * are absent on purpose: a recipient must never hear a letter before it arrives.
     */
    static final java.util.Set<String> DELIVERED_TO_RECEIVER_STATUSES = java.util.Set.of(
            "DELIVERED", "READ", "REPLIED", "DECLINED", "BLOCKED", "ARCHIVED");


    public SlowLetterServiceImpl(SlowLetterMapper letterMapper, LetterStatusLogMapper logMapper, LetterStateRegistry stateRegistry, LetterGuardAgent guardAgent, LetterThreadMapper threadMapper, ReportRecordMapper reportRecordMapper, LetterSafetyFilter letterSafetyFilter, EchoCapsuleMapper capsuleMapper, BlockRelationMapper blockRelationMapper, PiiCredentialDetector piiCredentialDetector, Clock clock) {
        this.letterMapper = letterMapper;
        this.logMapper = logMapper;
        this.stateRegistry = stateRegistry;
        this.guardAgent = guardAgent;
        this.threadMapper = threadMapper;
        this.reportRecordMapper = reportRecordMapper;
        this.letterSafetyFilter = letterSafetyFilter;
        this.capsuleMapper = capsuleMapper;
        this.blockRelationMapper = blockRelationMapper;
        this.piiCredentialDetector = piiCredentialDetector;
        this.clock = clock;
    }

    @Override
    public SlowLetter draft(Long userId, LetterCreateRequest request) {
        // Gemini audit 1.8 (CONFIRMED/P1): a retried compose call (e.g. the client never received
        // the response and resubmits) must not silently insert a second letter. If the caller
        // supplied an idempotency key and a letter with that (sender, key) pair already exists,
        // return it unchanged instead of re-running validation/insert.
        SlowLetter existing = findByIdempotencyKey(userId, request.idempotencyKey);
        if (existing != null) {
            return existing;
        }
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
        letter.estimatedArrivalAt = LocalDateTime.now(clock).plus(PARALLAX_FLIGHT_DURATION);
        letter.versionNo = 0;
        letter.idempotencyKey = blankToNull(request.idempotencyKey);
        insertIdempotently(letter, userId);
        return letter;
    }

    /** Returns the caller's own existing letter for this idempotency key, or null if none/blank. */
    private SlowLetter findByIdempotencyKey(Long userId, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return null;
        }
        return letterMapper.selectOne(new QueryWrapper<SlowLetter>()
                .eq("sender_user_id", userId).eq("idempotency_key", idempotencyKey).last("LIMIT 1"));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /**
     * Inserts a freshly-built letter, tolerating a concurrent retry of the SAME idempotency key
     * racing this insert (the unique (sender_user_id, idempotency_key) index catches it) by
     * returning the winner's row instead of failing the request.
     */
    private void insertIdempotently(SlowLetter letter, Long senderUserId) {
        if (letter.idempotencyKey == null) {
            letterMapper.insert(letter);
            return;
        }
        try {
            letterMapper.insert(letter);
        } catch (DuplicateKeyException raced) {
            SlowLetter winner = findByIdempotencyKey(senderUserId, letter.idempotencyKey);
            if (winner == null) {
                throw raced;
            }
            copyPersistedFields(winner, letter);
        }
    }

    /** After losing an idempotency-key insert race, present the WINNER's persisted state. */
    private static void copyPersistedFields(SlowLetter winner, SlowLetter target) {
        target.id = winner.id;
        target.status = winner.status;
        target.threadId = winner.threadId;
        target.estimatedArrivalAt = winner.estimatedArrivalAt;
        target.versionNo = winner.versionNo;
        target.title = winner.title;
        target.letterBody = winner.letterBody;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SlowLetter patchDraft(Long userId, Long id, String title, String letterBody, Integer expectedVersion) {
        SlowLetter letter = letterMapper.selectById(id);
        if (letter == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "信件不存在");
        }
        // Owner-scoped: only the sender may edit their own draft (IDOR guard).
        if (!userId.equals(letter.senderUserId)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "只有写信人可以编辑这封信");
        }
        if (!"DRAFT".equals(letter.status)) {
            throw new BusinessException(ErrorCode.LETTER_STATE_INVALID, "只能编辑尚未寄出的草稿");
        }
        if (title == null || title.isBlank() || letterBody == null || letterBody.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "标题和正文不能为空");
        }
        int expected = expectedVersion == null ? 0 : expectedVersion;
        // Gemini audit 1.8 (CONFIRMED/P1): the atomic conditional UPDATE gates on id + owner +
        // DRAFT status + the client's expectedVersion all at once. A concurrent edit (this
        // client's belief about versionNo is stale) OR a concurrent send (status is no longer
        // DRAFT) both surface as rowsAffected==0 -- a real lost-update guard, not a TOCTOU gap
        // between the select above and this update.
        UpdateWrapper<SlowLetter> w = new UpdateWrapper<SlowLetter>()
                .eq("id", id).eq("sender_user_id", userId).eq("status", "DRAFT").eq("version_no", expected)
                .set("title", title).set("letter_body", letterBody).set("version_no", expected + 1)
                .set("updated_at", LocalDateTime.now(clock));
        int updated = letterMapper.update(null, w);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "这封信已经被修改过，请刷新后再试");
        }
        letter.title = title;
        letter.letterBody = letterBody;
        letter.versionNo = expected + 1;
        return letter;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SlowLetter transition(Long userId, Long id, String targetStatus, Boolean piiConfirmed) {
        SlowLetter letter = letterMapper.selectById(id);
        if (letter == null) {
            throw new com.innercosmos.exception.BusinessException(com.innercosmos.common.ErrorCode.NOT_FOUND, "信件不存在");
        }
        boolean isSender = userId.equals(letter.senderUserId);
        boolean isReceiver = userId.equals(letter.receiverUserId);
        if (!isSender && !isReceiver) {
            throw new com.innercosmos.exception.BusinessException(com.innercosmos.common.ErrorCode.UNAUTHORIZED, "无权操作此信件");
        }
        if (List.of("FLYING", "DELIVERED").contains(targetStatus)) {
            throw new com.innercosmos.exception.BusinessException(
                    com.innercosmos.common.ErrorCode.UNAUTHORIZED, "信件抵达只能由调度系统推进");
        }
        // Gemini audit 1.8 (CONFIRMED/P1): REPLIED must never be a client-chosen target -- it
        // used to depend on the frontend making a second, disconnected /reply call any time
        // after the reply draft was created, with no guarantee the reply had actually SENT (or
        // even existed). It is now ONLY an automatic, atomic side effect of a linked reply's own
        // SENT transition (see the replyToLetterId branch below) -- never externally settable.
        if ("REPLIED".equals(targetStatus)) {
            throw new com.innercosmos.exception.BusinessException(com.innercosmos.common.ErrorCode.BAD_REQUEST,
                    "REPLIED 只能在回信真正寄出后自动触发，不能手动设置");
        }
        if ("SENT".equals(targetStatus) && !isSender) {
            throw new com.innercosmos.exception.BusinessException(
                    com.innercosmos.common.ErrorCode.UNAUTHORIZED, "只有写信人可以让信启程");
        }
        if (List.of("READ", "DECLINED", "BLOCKED").contains(targetStatus) && !isReceiver) {
            throw new com.innercosmos.exception.BusinessException(
                    com.innercosmos.common.ErrorCode.UNAUTHORIZED, "只有收件人可以处理这封信");
        }
        // Gemini audit 1.8: a retried send (client never received the first response) must be
        // idempotent -- it already succeeded, so return the existing letter rather than
        // re-running safety checks, erroring, or attempting to re-send.
        if ("SENT".equals(targetStatus) && "SENT".equals(letter.status)) {
            return letter;
        }
        // Gemini audit 3.3 (CONFIRMED/P1): detect-and-gate for credentials/PII, never a keyword
        // -deletion sanitize -- the letter body itself is never rewritten or redacted.
        java.util.List<String> piiConsentCategories = java.util.List.of();
        if ("SENT".equals(targetStatus)) {
            ensureDeliveryAllowed(letter.letterBody, letter.senderUserId, letter.receiverUserId);
            PiiCredentialDetector.ScanResult pii = piiCredentialDetector.scan(letter.letterBody);
            if (pii.hasHardBlock()) {
                // Credentials/secrets: hard-blocked outright, no confirmation override exists.
                throw new SafetyBlockedException("这封信似乎包含密码、密钥或证件号等凭据信息("
                        + String.join(",", pii.hardBlockCategories) + ")，为了你的账号和信息安全，无法发送。"
                        + "请删除这些内容后再试一次。");
            }
            if (pii.hasSoftConfirm()) {
                if (!Boolean.TRUE.equals(piiConfirmed)) {
                    throw new com.innercosmos.exception.BusinessException(
                            com.innercosmos.common.ErrorCode.PII_CONFIRMATION_REQUIRED,
                            "这封信包含个人联系方式或住址信息(" + String.join(",", pii.softConfirmCategories)
                                    + ")，确认要发送吗？");
                }
                // Explicit confirmation given -- record a minimal consent RECEIPT (category names
                // only) in the transition log below. Never the raw matched PII text itself.
                piiConsentCategories = pii.softConfirmCategories;
            }
        }
        stateRegistry.validate(letter.status, targetStatus);
        String from = letter.status;
        // M-022: atomic conditional UPDATE — only applies if status is still `from`, so a concurrent
        // transition (e.g. user /read racing the scheduler SENT->DELIVERED) cannot both pass
        // validate() and clobber each other. rowsAffected==0 means we lost the race.
        java.time.LocalDateTime now = java.time.LocalDateTime.now(clock);
        com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<SlowLetter> w =
                new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<SlowLetter>()
                        .eq("id", id).eq("status", from)
                        .set("status", targetStatus);
        if ("SENT".equals(targetStatus)) w.set("sent_at", now);
        if ("DELIVERED".equals(targetStatus)) w.set("delivered_at", now);
        if ("READ".equals(targetStatus)) w.set("read_at", now);
        int updated = letterMapper.update(null, w);
        if (updated == 0) {
            throw new com.innercosmos.exception.BusinessException(
                    com.innercosmos.common.ErrorCode.LETTER_STATE_INVALID, "信件状态刚刚变化，请刷新后重试");
        }
        letter.status = targetStatus;
        if ("SENT".equals(targetStatus)) letter.sentAt = now;
        if ("DELIVERED".equals(targetStatus)) letter.deliveredAt = now;
        if ("READ".equals(targetStatus)) letter.readAt = now;

        // Gemini audit 1.8 (CONFIRMED/P1): the ONLY path that ever sets REPLIED. This runs in the
        // SAME transaction as the reply's own SENT advance above -- either both commit (the reply
        // sent AND the original is marked replied) or neither does, never an optimistic write at
        // draft-creation time. The atomic conditional UPDATE only flips an original that is still
        // READ (the one state the letter-state machine allows to advance to REPLIED); if the
        // original is no longer READ (already REPLIED by an earlier reply, or BLOCKED/DECLINED/
        // ARCHIVED in the meantime), this is a harmless no-op -- the reply still sends
        // successfully either way.
        if ("SENT".equals(targetStatus) && letter.replyToLetterId != null) {
            LocalDateTime repliedAt = now;
            int flippedOriginal = letterMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<SlowLetter>()
                    .eq("id", letter.replyToLetterId).eq("status", "READ")
                    .set("status", "REPLIED").set("replied_at", repliedAt));
            if (flippedOriginal == 1) {
                LetterStatusLog originalLog = new LetterStatusLog();
                originalLog.letterId = letter.replyToLetterId;
                originalLog.fromStatus = "READ";
                originalLog.toStatus = "REPLIED";
                originalLog.operatorUserId = userId;
                originalLog.reason = "auto: linked reply sent (id=" + id + ")";
                logMapper.insert(originalLog);
            }
        }

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
        // Gemini audit 3.3: minimal consent RECEIPT -- category names only (e.g. "PHONE,EMAIL"),
        // never the raw PII text that was matched -- appended when the sender confirmed sending a
        // letter flagged with soft-confirm PII.
        log.reason = piiConsentCategories.isEmpty() ? "API transition"
                : "API transition; PII_CONSENT_CONFIRMED:" + String.join(",", piiConsentCategories);
        logMapper.insert(log);
        return letter;
    }

    @Override
    public List<SlowLetter> inbox(Long userId) {
        QueryWrapper<SlowLetter> query = new QueryWrapper<>();
        query.eq("receiver_user_id", userId)
                // Reuses DELIVERED_TO_RECEIVER_STATUSES -- the single source of truth for "delivered
                // to the recipient", shared with the voice gate (synthesizeVoice) so the two cannot
                // drift apart on which states count as "arrived".
                .in("status", DELIVERED_TO_RECEIVER_STATUSES)
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
    public byte[] synthesizeVoice(Long userId, Long letterId) {
        SlowLetter letter = letterMapper.selectById(letterId);
        if (letter == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "信件不存在");
        }
        // Authorization gate #1 -- reuse the SAME recipient-scoped fetch + party-membership check
        // getLetter()/reportLetter() use (only a party to the letter may act on it), narrowed here to
        // RECIPIENT-only: voice exists so the recipient can hear a delivered letter read aloud. The
        // sender already knows what they wrote, and a third party must never reach this path (IDOR).
        // This is the existing recipient-scoped letter-read gate reapplied, NOT a new surface.
        if (!userId.equals(letter.receiverUserId)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "只有收件人可以听这封信");
        }
        // Authorization gate #2 -- reuse the SAME delivered-to-recipient delivery-state gate the
        // inbox() query uses (DELIVERED_TO_RECEIVER_STATUSES): a letter is hearable only once it has
        // actually arrived. In-flight SENT/FLYING letters (and unreadable DRAFTs) are rejected -- a
        // recipient must never hear a letter before it is delivered, exactly as they can never see
        // its body in their inbox before then.
        if (!DELIVERED_TO_RECEIVER_STATUSES.contains(letter.status)) {
            throw new BusinessException(ErrorCode.LETTER_STATE_INVALID, "信件抵达后才能朗读");
        }
        if (letter.letterBody == null || letter.letterBody.isBlank()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "这封信没有可以朗读的正文");
        }
        if (ttsClient == null || !ttsClient.available()) {
            throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR, "慢信朗读暂未启用");
        }
        try {
            // Warm, calm default voice reused from Aurora's own TtsVoicePresets (NO new catalog) -- a
            // slow letter deserves the same gentle warmth as Aurora's inner voice, deliberately NOT a
            // distinct persona voice (that distinction belongs to the capsule path only).
            return ttsClient.synthesize(letter.letterBody, TtsVoicePresets.defaultVoice().id());
        } catch (Exception failure) {
            // Mirrors the capsule-voice / Aurora inner-voice resilience: a synthesis failure or a
            // bounded timeout (tts.timeout-ms, default 8s) never breaks the letter -- this on-demand
            // endpoint surfaces a clean business error and the recipient's letter is untouched. The
            // text body was already delivered via the separate GET /api/letters/{id} path.
            throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR, "慢信朗读暂时不可用，请稍后再试");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SlowLetter replyWithLetter(Long userId, Long letterId, LetterCreateRequest request) {
        // Gemini audit 1.8 (CONFIRMED/P1): a retried compose-reply call must not insert a second
        // reply letter (and a second thread bump). Checked before any other validation so a
        // replay is a true no-op, not a partial re-run.
        SlowLetter alreadyComposed = findByIdempotencyKey(userId, request.idempotencyKey);
        if (alreadyComposed != null) {
            return alreadyComposed;
        }
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
        ensureDeliveryAllowed(request.letterBody, userId, original.senderUserId);

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
        reply.estimatedArrivalAt = LocalDateTime.now(clock).plus(PARALLAX_FLIGHT_DURATION);
        reply.versionNo = 0;
        // Gemini audit 1.8: links this reply to the letter it replies to, so its OWN later SENT
        // transition can atomically flip `original` to REPLIED -- never optimistically here, at
        // draft-creation time, before the reply has even been reviewed or sent.
        reply.replyToLetterId = original.id;
        reply.idempotencyKey = blankToNull(request.idempotencyKey);
        insertIdempotently(reply, userId);

        // Back-fill the original so the whole conversation is walkable by thread id.
        if (original.threadId == null) {
            original.threadId = thread.id;
            letterMapper.updateById(original);
        }

        thread.lastLetterAt = LocalDateTime.now(clock);
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
        thread.lastLetterAt = LocalDateTime.now(clock);
        threadMapper.insert(thread);
        return thread;
    }

    private void ensureDeliveryAllowed(String body, Long senderId, Long receiverId) {
        LetterSafetyFilter.FilterResult result = letterSafetyFilter.filter(body, senderId, receiverId);
        if (result == null || !result.passed) {
            String reason = result == null ? "letter safety check unavailable" : result.reason;
            throw new SafetyBlockedException(reason == null ? "letter cannot be delivered" : reason);
        }
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
