-- Gemini audit 1.8 (CONFIRMED/P1): a reply letter now records which letter it replies to, so its
-- own SENT transition can atomically flip the ORIGINAL to REPLIED -- a reliable side effect of
-- actually sending, never an optimistic write at draft-creation time. version_no backs an
-- owner-scoped optimistic-concurrency draft PATCH; idempotency_key lets a retried compose call
-- (draft / reply-with-letter) return the original row instead of inserting a duplicate letter.
-- No FK constraint on reply_to_letter_id, matching this table's existing convention: none of
-- its other cross-referencing columns (sender_user_id, receiver_user_id, receiver_capsule_id)
-- are FK-enforced either -- integrity here is owned by the service layer (SlowLetterServiceImpl),
-- consistent across both the H2 dev/test schema (schema.sql) and this PostgreSQL migration.
ALTER TABLE tb_slow_letter
    ADD COLUMN reply_to_letter_id BIGINT NULL,
    ADD COLUMN version_no INT NOT NULL DEFAULT 0,
    ADD COLUMN idempotency_key VARCHAR(128) NULL;

CREATE INDEX idx_letter_reply_to ON tb_slow_letter(reply_to_letter_id);

-- One idempotency key can only ever mean one letter for a given sender -- a retried compose
-- call must resolve to the SAME row, not a different one.
CREATE UNIQUE INDEX uq_slow_letter_sender_idempotency
    ON tb_slow_letter(sender_user_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;
