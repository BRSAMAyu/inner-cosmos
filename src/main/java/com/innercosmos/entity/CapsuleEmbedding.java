package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.TableName;

/**
 * A3-capsule-matching: versioned embedding of ONLY a capsule's consent-scoped public-safe text
 * (pseudonym + intro + publicTags — see CapsuleServiceImpl#capsulePublicSafeText). Never built
 * from personaPrompt, ownerContextNote, styleProfileJson or contextPreviewJson, which are private
 * runtime/authoring fields the owner never consented to expose to matching or to a third-party
 * embedding provider. contentHash lets a re-embed be skipped when the public-safe text has not
 * changed, and forces a re-embed the moment it does (e.g. after a correction/withdrawal touches
 * the source memories a capsule's intro was derived from).
 */
@TableName("tb_capsule_embedding")
public class CapsuleEmbedding extends BaseEntity {
    public Long capsuleId;
    public String modelName;
    public String modelVersion;
    public String contentHash;
    public Integer dimensions;
    public String embeddingJson;
    public String status;
}
