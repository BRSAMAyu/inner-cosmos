package com.innercosmos.skill;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class PsychologySkillRegistry {
    private static final List<String> RESOURCES = List.of(
            "skills/emotion-needs-clarifier.v1.json",
            "skills/values-compass.v1.json",
            "skills/decision-conflict-map.v1.json");

    private final Map<String, PsychologySkillManifest> manifests;

    public PsychologySkillRegistry(ObjectMapper objectMapper) {
        Map<String, PsychologySkillManifest> loaded = new LinkedHashMap<>();
        for (String path : RESOURCES) {
            PsychologySkillManifest manifest = read(objectMapper, path);
            validate(manifest, path);
            if (loaded.putIfAbsent(manifest.id, manifest) != null) {
                throw new IllegalStateException("Duplicate Psychology Skill id: " + manifest.id);
            }
        }
        manifests = Map.copyOf(loaded);
    }

    public List<PsychologySkillManifest> list() {
        return RESOURCES.stream().map(path -> manifests.get(idFor(path))).toList();
    }

    public PsychologySkillManifest require(String id) {
        PsychologySkillManifest manifest = manifests.get(id);
        if (manifest == null) throw new IllegalArgumentException("Unknown Psychology Skill: " + id);
        return manifest;
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

    private String idFor(String path) {
        return path.substring(path.lastIndexOf('/') + 1, path.indexOf(".v1.json"));
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }
}
