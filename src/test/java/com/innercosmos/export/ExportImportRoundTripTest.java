package com.innercosmos.export;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CP-62 migration roundtrip contract (blueprint L893, all six items): export→import
 * preserves format version/source/time/correction/media references; re-import is
 * idempotent (zero new rows, receipts skip); a tampered package is rejected; a package
 * smuggling another user's rows is rejected (越权资料阻断); imported capsules are
 * private and letters are drafts (不自动公开/不寄信); imports never create or revive
 * data-use grants (不复活已撤回用途). No payment state is consulted — 退出/迁移不绑购买.
 */
@SpringBootTest(properties = "spring.task.scheduling.enabled=false")
class ExportImportRoundTripTest {

    private static final AtomicLong USERS = new AtomicLong(980_000_000L);

    @Autowired UserDataExportService exports;
    @Autowired UserDataImportService imports;
    @Autowired MemoryCardMapper memoryCardMapper;
    @Autowired EchoCapsuleMapper echoCapsuleMapper;
    @Autowired SlowLetterMapper slowLetterMapper;
    @Autowired VoiceTranscriptionMapper voiceTranscriptionMapper;
    @Autowired DataImportReceiptMapper receiptMapper;

    private static long uniqueUser() {
        return USERS.incrementAndGet();
    }

    private Long seedSourceUser() {
        long user = uniqueUser();
        MemoryCard card = new MemoryCard();
        card.userId = user;
        card.title = "工作节奏";
        card.summary = "周二例会前夜焦虑，调整为周一上午准备";
        card.memoryType = "FACT";
        card.memoryLayer = "CORE";
        card.consentScope = "PRIVATE";
        card.provenanceRefs = "session:12;correction:3";
        card.versionNo = 2;
        card.visibilityLevel = "PRIVATE";
        card.status = "ACTIVE";
        memoryCardMapper.insert(card);

        EchoCapsule capsule = new EchoCapsule();
        capsule.ownerUserId = user;
        capsule.capsuleType = "PERSONA";
        capsule.pseudonym = "夜航船";
        capsule.intro = "intro";
        capsule.personaPrompt = "prompt";
        capsule.visibilityStatus = "PUBLIC";
        capsule.isPublic = true;
        echoCapsuleMapper.insert(capsule);

        SlowLetter letter = new SlowLetter();
        letter.senderUserId = user;
        letter.receiverUserId = user + 1;
        letter.title = "写给下周的我";
        letter.letterBody = "body";
        letter.status = "READ";
        slowLetterMapper.insert(letter);

        VoiceTranscription transcription = new VoiceTranscription();
        transcription.userId = user;
        transcription.originalText = "原文转写";
        transcription.editedText = "编辑后转写";
        transcription.status = "CONFIRMED";
        voiceTranscriptionMapper.insert(transcription);
        return user;
    }

    @Test
    void roundTripPreservesEverythingAndLandsPrivateByDefault() {
        Long source = seedSourceUser();
        Long target = uniqueUser();
        ExportPackage pkg = exports.build(source);

        // (1) Envelope: version / source / time / hashes / correction chain / media refs.
        assertEquals(ExportPackage.CURRENT_SCHEMA, pkg.schemaVersion());
        assertEquals(source, pkg.sourceUserId());
        assertNotNull(pkg.exportedAt());
        Section memories = pkg.sections().get("memoryCards");
        assertEquals(1, memories.records().size());
        Map<String, Object> memoryRecord = memories.records().get(0);
        assertEquals(2, ((Number) memoryRecord.get("versionNo")).intValue(),
                "correction chain (version) rides along");
        assertNotNull(memoryRecord.get("provenanceRefs"));
        assertEquals("PRIVATE", memoryRecord.get("consentScope"));
        assertTrue(pkg.media().stream().anyMatch(m -> "audio/transcript".equals(m.mediaType())),
                "media references present");
        assertNull(memoryRecord.get("id"), "no source row ids leak into the package");

        UserDataImportService.ImportReport report = imports.validateThenImport(target, pkg);
        assertEquals(1, report.imported().get("memoryCards"));
        assertEquals(1, report.imported().get("echoCapsules"));
        assertEquals(1, report.imported().get("slowLettersSent"));
        assertEquals(1, report.imported().get("voiceTranscriptions"));

        MemoryCard importedCard = memoryCardMapper.selectList(new QueryWrapper<MemoryCard>()
                .eq("user_id", target)).get(0);
        assertEquals(target, importedCard.userId);
        assertEquals("工作节奏", importedCard.title);
        assertEquals(2, importedCard.versionNo);
        assertEquals("session:12;correction:3", importedCard.provenanceRefs);
        assertNull(importedCard.supersededById, "source-deployment pointers never cross");

        // (4) 不自动公开共鸣体 + (5) 不寄信。
        EchoCapsule importedCapsule = echoCapsuleMapper.selectList(new QueryWrapper<EchoCapsule>()
                .eq("owner_user_id", target)).get(0);
        assertFalse(Boolean.TRUE.equals(importedCapsule.isPublic),
                "capsules land PRIVATE regardless of source visibility");
        assertEquals("PRIVATE", importedCapsule.visibilityStatus);
        SlowLetter importedLetter = slowLetterMapper.selectList(new QueryWrapper<SlowLetter>()
                .eq("sender_user_id", target)).get(0);
        assertEquals("DRAFT", importedLetter.status, "imported letters are never sent");

        // (2) Idempotent re-import: receipts skip everything, zero new rows.
        UserDataImportService.ImportReport again = imports.validateThenImport(target, pkg);
        assertEquals(0, again.imported().get("memoryCards"));
        assertEquals(1, again.skipped().get("memoryCards"));
        assertEquals(1, memoryCardMapper.selectCount(
                new QueryWrapper<MemoryCard>().eq("user_id", target)));
        assertEquals(4, receiptMapper.selectCount(
                new QueryWrapper<DataImportReceipt>().eq("target_user_id", target)));

        // (6) Imports never create grants/permissions — only the four sections exist.
        List<String> receiptSections = receiptMapper.selectList(new QueryWrapper<DataImportReceipt>()
                        .eq("target_user_id", target)).stream().map(r -> r.section).toList();
        assertTrue(receiptSections.stream().noneMatch(s -> s.contains("grant") || s.contains("permission")),
                "no grant or permission rows may ever come from an import");
    }

    @Test
    void tamperedPackagesAndForeignRecordsAreRejectedFailClosed() {
        Long source = seedSourceUser();
        Long honestTarget = uniqueUser();

        // (3) Tamper: mutate a record after signing — section digest must catch it.
        ExportPackage tampered = mutateFirstRecord(exports.build(source), "summary", "伪造摘要");
        BusinessException corrupt = assertThrows(BusinessException.class,
                () -> imports.validateThenImport(honestTarget, tampered));
        assertEquals("BAD_REQUEST", corrupt.code);
        assertTrue(corrupt.getMessage().contains("corrupt"));

        // Tamper the integrity field only — whole-package digest must catch it.
        ExportPackage forged = exports.build(source);
        ExportPackage forgedIntegrity = new ExportPackage(forged.schemaVersion(),
                forged.sourceUserId(), forged.exportedAt(), forged.sections(), forged.media(),
                "deadbeef" + forged.integrity().substring(8));
        assertThrows(BusinessException.class,
                () -> imports.validateThenImport(honestTarget, forgedIntegrity));

        // Nothing landed from any rejected package.
        assertEquals(0, memoryCardMapper.selectCount(
                new QueryWrapper<MemoryCard>().eq("user_id", honestTarget)));

        // (3b) 越权资料阻断: a RE-SIGNED package (digests recomputed) smuggling a
        // foreign-owned record is still rejected — ownerGuard is not a digest artifact.
        ExportPackage smuggled = mutateAndResign(exports.build(source), "ownerUserId",
                String.valueOf(source + 999));
        BusinessException foreign = assertThrows(BusinessException.class,
                () -> imports.validateThenImport(honestTarget, smuggled));
        assertEquals("FORBIDDEN", foreign.code);

        // Unsupported schema version rejected.
        ExportPackage badVersion = exports.build(source);
        assertThrows(BusinessException.class, () -> imports.validateThenImport(honestTarget,
                new ExportPackage(99, badVersion.sourceUserId(), badVersion.exportedAt(),
                        badVersion.sections(), badVersion.media(), badVersion.integrity())));
    }

    private static ExportPackage mutateFirstRecord(ExportPackage pkg, String key, String value) {
        Map<String, Section> sections = new java.util.LinkedHashMap<>(pkg.sections());
        Section memories = sections.get("memoryCards");
        Map<String, Object> mutated = new java.util.LinkedHashMap<>(memories.records().get(0));
        mutated.put(key, value);
        sections.put("memoryCards", new Section(memories.sha256(),
                new java.util.ArrayList<>(memories.records())));
        sections.get("memoryCards").records().set(0, mutated);
        return new ExportPackage(pkg.schemaVersion(), pkg.sourceUserId(), pkg.exportedAt(),
                sections, pkg.media(), pkg.integrity());
    }

    /** Mutates a record and RE-SIGNS the package — the honest-shape smuggling attack. */
    private ExportPackage mutateAndResign(ExportPackage pkg, String key, String value) {
        Map<String, Section> sections = new java.util.LinkedHashMap<>(pkg.sections());
        Section memories = sections.get("memoryCards");
        Map<String, Object> mutated = new java.util.LinkedHashMap<>(memories.records().get(0));
        mutated.put(key, value);
        List<Map<String, Object>> records = new java.util.ArrayList<>(memories.records());
        records.set(0, mutated);
        sections.put("memoryCards", new Section(
                UserDataExportService.sha256(exports.toJson(records)), records));
        Map<String, Object> integrityInput = new java.util.LinkedHashMap<>();
        sections.forEach((name, section) -> integrityInput.put(name, section.records()));
        integrityInput.put("media", pkg.media());
        return new ExportPackage(pkg.schemaVersion(), pkg.sourceUserId(), pkg.exportedAt(),
                sections, pkg.media(),
                UserDataExportService.sha256(exports.toJson(integrityInput)));
    }
}
