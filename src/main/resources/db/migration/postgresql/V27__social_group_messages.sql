CREATE TABLE IF NOT EXISTS tb_social_group_message (
    id BIGSERIAL PRIMARY KEY,
    group_id BIGINT NOT NULL,
    sender_user_id BIGINT NOT NULL,
    message_body VARCHAR(2000) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_group_message_group_id
    ON tb_social_group_message (group_id, id);
