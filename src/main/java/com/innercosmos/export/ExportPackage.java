package com.innercosmos.export;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * CP-62 versioned data-portability package (导出格式 v2). The envelope is the migration
 * contract: schemaVersion whitelist, source identity (ownerGuard basis), export time,
 * per-section SHA-256 over canonical JSON, and a whole-package integrity digest. Every
 * record carries its natural import key, its owning user, sourceRef back to the origin
 * row, occurredAt, and (for memories) the correction chain — so a clean-account import
 * can verify 格式版本/来源/时间/纠正/媒体完整度 (blueprint L893) before anything lands.
 */
public record ExportPackage(
        int schemaVersion,
        Long sourceUserId,
        LocalDateTime exportedAt,
        Map<String, Section> sections,
        List<MediaRef> media,
        String integrity) {

    public static final int CURRENT_SCHEMA = 2;

    public record Section(String sha256, List<Map<String, Object>> records) {
    }

    /** Media referenced by records; the package references media, never inlines blobs. */
    public record MediaRef(String ref, String mediaType, String referencedBy) {
    }
}
