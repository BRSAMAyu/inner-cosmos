CREATE TABLE tb_capsule_landing (
    id BIGSERIAL PRIMARY KEY,
    capsule_id BIGINT NOT NULL REFERENCES tb_echo_capsule(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES tb_user(id) ON DELETE CASCADE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_capsule_landing_user UNIQUE (capsule_id, user_id)
);

CREATE INDEX idx_capsule_landing_user ON tb_capsule_landing(user_id, created_at);
