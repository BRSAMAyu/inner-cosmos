ALTER TABLE tb_wake_intent ADD COLUMN IF NOT EXISTS context_session_id BIGINT;
ALTER TABLE tb_wake_intent ADD COLUMN IF NOT EXISTS context_message_id BIGINT;
ALTER TABLE tb_wake_intent ADD COLUMN IF NOT EXISTS supersedes_intent_id BIGINT;
ALTER TABLE tb_wake_intent ADD COLUMN IF NOT EXISTS user_feedback VARCHAR(24);
ALTER TABLE tb_wake_intent ADD COLUMN IF NOT EXISTS feedback_at TIMESTAMP;

ALTER TABLE tb_wake_intent DROP CONSTRAINT IF EXISTS ck_wake_intent_status;
ALTER TABLE tb_wake_intent ADD CONSTRAINT ck_wake_intent_status
    CHECK (status IN ('PLANNED','CLAIMED','FIRED','CANCELLED','EXPIRED','SUPERSEDED'));

CREATE INDEX IF NOT EXISTS idx_wake_intent_context ON tb_wake_intent (user_id, context_session_id);
CREATE INDEX IF NOT EXISTS idx_wake_intent_feedback ON tb_wake_intent (user_id, purpose, user_feedback);
