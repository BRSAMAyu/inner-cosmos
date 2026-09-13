-- CP-26 quiet hours: a wake intent withheld inside a do-not-disturb window must stay
-- visible on its row (status DEFERRED + deferred_until = the UTC instant the window ends)
-- instead of silently re-polling or dropping. Extend the durable intent state machine and
-- keep the claim scan able to find deferred rows again once their window closes.
ALTER TABLE tb_wake_intent ADD COLUMN IF NOT EXISTS deferred_until TIMESTAMP;

ALTER TABLE tb_wake_intent DROP CONSTRAINT IF EXISTS ck_wake_intent_status;
ALTER TABLE tb_wake_intent ADD CONSTRAINT ck_wake_intent_status
    CHECK (status IN ('PLANNED','CLAIMED','DEFERRED','FIRED','CANCELLED','EXPIRED','SUPERSEDED'));

CREATE INDEX IF NOT EXISTS idx_wake_intent_deferred ON tb_wake_intent (status, deferred_until);
