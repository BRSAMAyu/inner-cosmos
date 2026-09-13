package com.innercosmos.export;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.innercosmos.entity.EchoCapsule;
import com.innercosmos.entity.MemoryCard;
import com.innercosmos.entity.SlowLetter;
import com.innercosmos.entity.VoiceTranscription;
import com.innercosmos.export.ExportPackage.MediaRef;
import com.innercosmos.export.ExportPackage.Section;
import com.innercosmos.mapper.EchoCapsuleMapper;
import com.innercosmos.mapper.MemoryCardMapper;
import com.innercosmos.mapper.SlowLetterMapper;
import com.innercosmos.mapper.VoiceTranscriptionMapper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CP-62 export v2 builder. Sections carry the user's OWN portable assets: memory cards
 * (with the correction chain snapshot: versionNo/supersededById/provenanceRefs/
 * consentScope), echo capsules (their published visibility recorded as-is — the IMPORT
 * side forces private), slow letters SENT by the user (received letters belong to their
 * senders and are not exported), and voice transcriptions (as media references). Each
 * section digest is SHA-256 over the canonical (key-sorted) JSON of its records; the
 * package integrity digest covers sections + media, so any tampering is detectable
 * without a trusted channel.
 */
@Service
public class UserDataExportService {

    private final MemoryCardMapper memoryCardMapper;
    private final EchoCapsuleMapper echoCapsuleMapper;
    private final SlowLetterMapper slowLetterMapper;
    private final VoiceTranscriptionMapper voiceTranscriptionMapper;
    private final ObjectMapper canonical = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .enable(com.fasterxml.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    public UserDataExportService(MemoryCardMapper memoryCardMapper,
                                 EchoCapsuleMapper echoCapsuleMapper,
                                 SlowLetterMapper slowLetterMapper,
                                 VoiceTranscriptionMapper voiceTranscriptionMapper) {
        this.memoryCardMapper = memoryCardMapper;
        this.echoCapsuleMapper = echoCapsuleMapper;
        this.slowLetterMapper = slowLetterMapper;
        this.voiceTranscriptionMapper = voiceTranscriptionMapper;
    }

    public ExportPackage build(Long userId) {
        List<Map<String, Object>> memories = new ArrayList<>();
        List<MediaRef> media = new ArrayList<>();
        for (MemoryCard card : memoryCardMapper.selectList(
                new QueryWrapper<MemoryCard>().eq("user_id", userId).orderByAsc("id"))) {
            Map<String, Object> record = record("memoryCards", "tb_memory_card", card.id, userId, card);
            // 纠正链快照：版本与被取代关系作为数据随行，导入方据此呈现完整历史。
            record.put("versionNo", card.versionNo);
            record.put("supersededById", card.supersededById);
            record.put("provenanceRefs", card.provenanceRefs);
            record.put("consentScope", card.consentScope);
            memories.add(record);
        }
        List<Map<String, Object>> capsules = new ArrayList<>();
        for (EchoCapsule capsule : echoCapsuleMapper.selectList(
                new QueryWrapper<EchoCapsule>().eq("owner_user_id", userId).orderByAsc("id"))) {
            Map<String, Object> record = record("echoCapsules", "tb_echo_capsule", capsule.id,
                    capsule.ownerUserId, capsule);
            record.put("wasPublic", capsule.isPublic);
            capsules.add(record);
        }
        List<Map<String, Object>> letters = new ArrayList<>();
        for (SlowLetter letter : slowLetterMapper.selectList(
                new QueryWrapper<SlowLetter>().eq("sender_user_id", userId).orderByAsc("id"))) {
            Map<String, Object> record = record("slowLettersSent", "tb_slow_letter", letter.id,
                    letter.senderUserId, letter);
            record.put("originalStatus", letter.status);
            letters.add(record);
        }
        List<Map<String, Object>> transcriptions = new ArrayList<>();
        for (VoiceTranscription transcription : voiceTranscriptionMapper.selectList(
                new QueryWrapper<VoiceTranscription>().eq("user_id", userId).orderByAsc("id"))) {
            Map<String, Object> record = record("voiceTranscriptions", "tb_voice_transcription",
                    transcription.id, transcription.userId, transcription);
            transcriptions.add(record);
            media.add(new MediaRef("voice-transcription-" + transcription.id, "audio/transcript",
                    "voiceTranscriptions:" + transcription.id));
        }

        Map<String, Section> sections = new LinkedHashMap<>();
        putSection(sections, "memoryCards", memories);
        putSection(sections, "echoCapsules", capsules);
        putSection(sections, "slowLettersSent", letters);
        putSection(sections, "voiceTranscriptions", transcriptions);

        Map<String, Object> integrityInput = new LinkedHashMap<>();
        sections.forEach((name, section) -> integrityInput.put(name, section.records()));
        integrityInput.put("media", media);
        return new ExportPackage(ExportPackage.CURRENT_SCHEMA, userId,
                LocalDateTime.now(ZoneOffset.UTC), sections, media,
                sha256(toJson(integrityInput)));
    }

    private void putSection(Map<String, Section> sections, String name,
                            List<Map<String, Object>> records) {
        sections.put(name, new Section(sha256(toJson(records)), records));
    }

    private Map<String, Object> record(String section, String sourceTable, Long sourceId,
                                       Long ownerUserId, Object entity) {
        Map<String, Object> record = canonical.convertValue(entity,
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                });
        record.put("importKey", section + ":" + sourceId);
        record.put("sourceRef", sourceTable + ":" + sourceId);
        record.put("ownerUserId", ownerUserId);
        record.put("occurredAt", record.getOrDefault("createdAt", null));
        record.remove("id");
        return record;
    }

    String toJson(Object value) {
        try {
            return canonical.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("canonical JSON failed", e);
        }
    }

    static String sha256(String canonicalJson) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest
                    .getInstance("SHA-256")
                    .digest(canonicalJson.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
