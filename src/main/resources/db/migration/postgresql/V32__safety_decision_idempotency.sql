ALTER TABLE tb_safety_event
    ADD COLUMN IF NOT EXISTS client_message_id VARCHAR(128),
    ADD COLUMN IF NOT EXISTS safety_scope VARCHAR(32);

CREATE UNIQUE INDEX IF NOT EXISTS uq_safety_event_client_scope
    ON tb_safety_event (user_id, client_message_id, safety_scope)
    WHERE client_message_id IS NOT NULL AND safety_scope IS NOT NULL;
