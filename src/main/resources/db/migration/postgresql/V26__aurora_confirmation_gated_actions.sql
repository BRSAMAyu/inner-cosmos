ALTER TABLE tb_turn_plan ADD COLUMN IF NOT EXISTS proposed_action_type VARCHAR(48);
ALTER TABLE tb_turn_plan ADD COLUMN IF NOT EXISTS proposed_action_payload TEXT;
ALTER TABLE tb_turn_plan ADD COLUMN IF NOT EXISTS proposed_action_summary VARCHAR(500);
ALTER TABLE tb_turn_plan ADD COLUMN IF NOT EXISTS action_status VARCHAR(32);
ALTER TABLE tb_turn_plan ADD COLUMN IF NOT EXISTS action_confirmed_at TIMESTAMP;
ALTER TABLE tb_turn_plan ADD COLUMN IF NOT EXISTS action_result_ref VARCHAR(160);

CREATE INDEX IF NOT EXISTS idx_turn_plan_pending_action
    ON tb_turn_plan (user_id, action_status, id);
