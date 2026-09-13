package com.innercosmos.export;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.common.ErrorCode;
import com.innercosmos.entity.DataImportReceipt;
import com.innercosmos.entity.EchoCapsule;
import com.innercosmos.entity.MemoryCard;
import com.innercosmos.entity.SlowLetter;
import com.innercosmos.entity.VoiceTranscription;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.export.ExportPackage.MediaRef;
import com.innercosmos.export.ExportPackage.Section;
import com.innercosmos.mapper.DataImportReceiptMapper;
import com.innercosmos.mapper.EchoCapsuleMapper;
import com.innercosmos.mapper.MemoryCardMapper;
import com.innercosmos.mapper.SlowLetterMapper;
import com.innercosmos.mapper.VoiceTranscriptionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * CP-62 data-portability import: validate-then-import, the six blueprint L893 rules
 * made mechanical —
 * (1) schemaVersion whitelist + per-section SHA-256 + package integrity (损坏包拒绝);
 * (2) ownerGuard: a record whose ownerUserId is not the package source user is rejected
 *     (越权资料阻断) — packages cannot smuggle someone else's rows;
 * (3) idempotent re-import via tb_data_import_receipt natural keys (重复导入零新增);
 * (4) capsules are imported PRIVATE regardless of source visibility (不自动公开共鸣体);
 * (5) letters are imported as DRAFT and never sent (不寄信);
 * (6) imports never create or revive data-use grants or third-party permissions
 *     (不重新激活已撤回用途).
 */
@Service
public class UserDataImportService {

    private static final Set<Integer> SUPPORTED_SCHEMAS = Set.of(ExportPackage.CURRENT_SCHEMA);

    private final UserDataExportService exporter;
    private final MemoryCardMapper memoryCardMapper;
    private final EchoCapsuleMapper echoCapsuleMapper;
    private final SlowLetterMapper slowLetterMapper;
    private final VoiceTranscriptionMapper voiceTranscriptionMapper;
    private final DataImportReceiptMapper receiptMapper;

    public UserDataImportService(UserDataExportService exporter,
                                 MemoryCardMapper memoryCardMapper,
                                 EchoCapsuleMapper echoCapsuleMapper,
                                 SlowLetterMapper slowLetterMapper,
                                 VoiceTranscriptionMapper voiceTranscriptionMapper,
                                 DataImportReceiptMapper receiptMapper) {
        this.exporter = exporter;
        this.memoryCardMapper = memoryCardMapper;
        this.echoCapsuleMapper = echoCapsuleMapper;
        this.slowLetterMapper = slowLetterMapper;
        this.voiceTranscriptionMapper = voiceTranscriptionMapper;
        this.receiptMapper = receiptMapper;
    }

    /** Per-section tallies for the roundtrip report. */
    public record ImportReport(Map<String, Integer> imported, Map<String, Integer> skipped) {
    }

    @Transactional(rollbackFor = Exception.class)
    public ImportReport validateThenImport(Long targetUserId, ExportPackage pkg) {
        if (pkg == null || pkg.sourceUserId() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "malformed package");
        }
        if (!SUPPORTED_SCHEMAS.contains(pkg.schemaVersion())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "unsupported schema version " + pkg.schemaVersion());
        }
        // (1) integrity: every section digest and the package digest must recompute.
        for (Map.Entry<String, Section> entry : pkg.sections().entrySet()) {
            String actual = UserDataExportService.sha256(exporter.toJson(entry.getValue().records()));
            if (!actual.equals(entry.getValue().sha256())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST,
                        "corrupt package: section " + entry.getKey() + " digest mismatch");
            }
            // (2) ownerGuard: every record belongs to the package source user.
            for (Map<String, Object> record : entry.getValue().records()) {
                Object owner = record.get("ownerUserId");
                if (owner == null || !String.valueOf(owner).equals(String.valueOf(pkg.sourceUserId()))) {
                    throw new BusinessException(ErrorCode.FORBIDDEN,
                            "package contains records the source user does not own (越权资料阻断)");
                }
            }
        }
        Map<String, Object> integrityInput = new LinkedHashMap<>();
        pkg.sections().forEach((name, section) -> integrityInput.put(name, section.records()));
        integrityInput.put("media", pkg.media() == null ? List.of() : pkg.media());
        String integrity = UserDataExportService.sha256(exporter.toJson(integrityInput));
        if (!integrity.equals(pkg.integrity())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "corrupt package: integrity mismatch");
        }

        Map<String, Integer> imported = new LinkedHashMap<>();
        Map<String, Integer> skipped = new LinkedHashMap<>();
        importSection(pkg, targetUserId, "memoryCards", imported, skipped, this::importMemoryCard);
        importSection(pkg, targetUserId, "echoCapsules", imported, skipped, this::importCapsule);
        importSection(pkg, targetUserId, "slowLettersSent", imported, skipped, this::importLetter);
        importSection(pkg, targetUserId, "voiceTranscriptions", imported, skipped, this::importTranscription);
        return new ImportReport(imported, skipped);
    }

    private interface RecordImporter {
        void importRecord(Long targetUserId, Map<String, Object> record);
    }

    private void importSection(ExportPackage pkg, Long targetUserId, String section,
                               Map<String, Integer> imported, Map<String, Integer> skipped,
                               RecordImporter importer) {
        Section records = pkg.sections().get(section);
        if (records == null) {
            return;
        }
        int inserted = 0;
        int skippedExisting = 0;
        for (Map<String, Object> record : records.records()) {
            String importKey = String.valueOf(record.get("importKey"));
            if (alreadyImported(section, importKey, targetUserId)) {
                skippedExisting++;
                continue;
            }
            importer.importRecord(targetUserId, record);
            receipt(section, importKey, targetUserId, pkg.sourceUserId());
            inserted++;
        }
        imported.put(section, inserted);
        skipped.put(section, skippedExisting);
    }

    private void importMemoryCard(Long targetUserId, Map<String, Object> record) {
        MemoryCard card = new MemoryCard();
        card.userId = targetUserId;
        card.title = text(record, "title");
        card.summary = text(record, "summary");
        card.memoryType = text(record, "memoryType");
        card.emotionTags = text(record, "emotionTags");
        card.keywordTags = text(record, "keywordTags");
        card.peopleTags = text(record, "peopleTags");
        card.memoryLayer = text(record, "memoryLayer");
        card.consentScope = text(record, "consentScope");
        card.provenanceRefs = text(record, "provenanceRefs");
        card.versionNo = intOrNull(record.get("versionNo"));
        card.visibilityLevel = "PRIVATE";
        card.status = "ACTIVE";
        card.sourceSessionId = null; // old-session references never cross deployments
        card.supersededById = null;  // points at a row in the SOURCE deployment
        memoryCardMapper.insert(card);
    }

    private void importCapsule(Long targetUserId, Map<String, Object> record) {
        // (4) 不自动公开共鸣体：visibility is the importer's decision, never the export's.
        EchoCapsule capsule = new EchoCapsule();
        capsule.ownerUserId = targetUserId;
        capsule.capsuleType = text(record, "capsuleType");
        capsule.pseudonym = text(record, "pseudonym");
        capsule.intro = text(record, "intro");
        capsule.personaPrompt = text(record, "personaPrompt");
        capsule.publicTags = text(record, "publicTags");
        capsule.authorizedMemoryIds = record.get("authorizedMemoryIds") == null ? null
                : record.get("authorizedMemoryIds").toString();
        capsule.visibilityStatus = "PRIVATE";
        capsule.isPublic = false;
        echoCapsuleMapper.insert(capsule);
    }

    private void importLetter(Long targetUserId, Map<String, Object> record) {
        // (5) 不寄信：imported letters are DRAFTs in the target account; nothing is sent.
        SlowLetter letter = new SlowLetter();
        letter.senderUserId = targetUserId;
        letter.receiverUserId = longOrNull(record.get("receiverUserId"));
        letter.title = text(record, "title");
        letter.letterBody = text(record, "letterBody");
        letter.status = "DRAFT";
        letter.parallaxDistance = intOrNull(record.get("parallaxDistance"));
        slowLetterMapper.insert(letter);
    }

    private void importTranscription(Long targetUserId, Map<String, Object> record) {
        VoiceTranscription transcription = new VoiceTranscription();
        transcription.userId = targetUserId;
        transcription.originalText = text(record, "originalText");
        transcription.editedText = text(record, "editedText");
        transcription.audioDurationSec = intOrNull(record.get("audioDurationSec"));
        transcription.status = "IMPORTED";
        voiceTranscriptionMapper.insert(transcription);
    }

    private boolean alreadyImported(String section, String importKey, Long targetUserId) {
        return receiptMapper.selectCount(new QueryWrapper<DataImportReceipt>()
                .eq("section", section)
                .eq("source_record_key", importKey)
                .eq("target_user_id", targetUserId)) > 0;
    }

    private void receipt(String section, String importKey, Long targetUserId, Long sourceUserId) {
        DataImportReceipt receipt = new DataImportReceipt();
        receipt.section = section;
        receipt.sourceRecordKey = importKey;
        receipt.targetUserId = targetUserId;
        receipt.sourceUserId = sourceUserId;
        receipt.importedAt = LocalDateTime.now(ZoneOffset.UTC);
        receiptMapper.insert(receipt);
    }

    private static String text(Map<String, Object> record, String key) {
        Object value = record.get(key);
        return value == null ? null : value.toString();
    }

    private static Integer intOrNull(Object value) {
        return value == null ? null : Integer.parseInt(String.valueOf(value));
    }

    private static Long longOrNull(Object value) {
        return value == null ? null : Long.parseLong(String.valueOf(value));
    }
}
