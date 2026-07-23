package com.innercosmos.dto;

/**
 * Gemini audit 1.8 (CONFIRMED/P1): owner-scoped, version/ETag-checked edit of a DRAFT letter.
 * {@code expectedVersion} must match the draft's current {@code versionNo} or the edit is
 * rejected as a lost concurrent-edit race rather than silently overwriting.
 */
public class LetterDraftPatchRequest {
    public String title;
    public String letterBody;
    public Integer expectedVersion;
}
