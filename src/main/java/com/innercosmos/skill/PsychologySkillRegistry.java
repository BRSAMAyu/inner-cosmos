package com.innercosmos.skill;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class PsychologySkillRegistry {
    private static final List<String> RESOURCES = List.of(
            "skills/emotion-needs-clarifier.v1.json",
            "skills/values-compass.v1.json",
            "skills/decision-conflict-map.v1.json");

    private final Map<String, PsychologySkillManifest> currentById;
    private final Map<String, PsychologySkillManifest> byVersion;
    private final List<PsychologySkillManifest> current;
    private final List<PsychologySkillManifest> all;
    private final ObjectMapper objectMapper;

    public PsychologySkillRegistry(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        Map<String, PsychologySkillManifest> loadedByVersion = new LinkedHashMap<>();
        Map<String, PsychologySkillManifest> loadedCurrent = new LinkedHashMap<>();
        for (String path : RESOURCES) {
            PsychologySkillManifest manifest = read(objectMapper, path);
            validate(manifest, path);
            if (loadedByVersion.putIfAbsent(key(manifest.id, manifest.version), manifest) != null) {
                throw new IllegalStateException("Duplicate Psychology Skill version: " + manifest.id + "@" + manifest.version);
            }
            // RESOURCES is an append-only version ledger; the last version for an id is current.
            loadedCurrent.put(manifest.id, manifest);
        }
        byVersion = Map.copyOf(loadedByVersion);
        currentById = Map.copyOf(loadedCurrent);
        current = List.copyOf(loadedCurrent.values());
        all = List.copyOf(loadedByVersion.values());
    }

    public List<PsychologySkillManifest> list() {
        return current;
    }

    public List<PsychologySkillManifest> all() {
        return all;
    }

    public PsychologySkillManifest require(String id) {
        PsychologySkillManifest manifest = currentById.get(id);
        if (manifest == null) throw new IllegalArgumentException("Unknown Psychology Skill: " + id);
        return manifest;
    }

    public PsychologySkillManifest require(String id, String version) {
        PsychologySkillManifest manifest = byVersion.get(key(id, version));
        if (manifest == null) throw new IllegalArgumentException("Unknown Psychology Skill version: " + id + "@" + version);
        return manifest;
    }

    public String hash(PsychologySkillManifest manifest) {
        try {
            byte[] canonical = objectMapper.writer().with(com.fasterxml.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                    .writeValueAsBytes(manifest);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical));
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot hash Psychology Skill manifest", exception);
        }
    }

    private PsychologySkillManifest read(ObjectMapper mapper, String path) {
        try (var input = new ClassPathResource(path).getInputStream()) {
            return mapper.readValue(input, PsychologySkillManifest.class);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot load Psychology Skill manifest " + path, exception);
        }
    }

    private void validate(PsychologySkillManifest manifest, String path) {
        if (blank(manifest.id) || blank(manifest.version) || blank(manifest.owner)
                || manifest.title == null || manifest.description == null
                || !manifest.title.containsKey("zh-CN") || !manifest.title.containsKey("en-SG")
                || manifest.requiredInputs == null || manifest.requiredInputs.isEmpty()
                || manifest.evidence == null || manifest.evidence.isEmpty()
                || !"EXPLICIT_CONSENT".equals(manifest.userInvocation)
                || !"L1".equals(manifest.riskTier)) {
            throw new IllegalStateException("Invalid Psychology Skill manifest: " + path);
        }
    }

    private String key(String id, String version) { return id + "@" + version; }

    private boolean blank(String value) { return value == null || value.isBlank(); }
}
