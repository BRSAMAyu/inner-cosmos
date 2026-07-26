CREATE TABLE IF NOT EXISTS tb_live_chat_invite (
    id BIGSERIAL PRIMARY KEY,
    inviter_user_id BIGINT NOT NULL,
    invitee_user_id BIGINT NOT NULL,
    duration_minutes INTEGER NOT NULL,
    status VARCHAR(24) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    responded_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_live_chat_invite_duration CHECK (duration_minutes IN (10, 15)),
    CONSTRAINT chk_live_chat_invite_status CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED', 'EXPIRED'))
);

CREATE INDEX IF NOT EXISTS idx_live_chat_invite_invitee_status
    ON tb_live_chat_invite (invitee_user_id, status, id DESC);

CREATE INDEX IF NOT EXISTS idx_live_chat_invite_inviter_status
    ON tb_live_chat_invite (inviter_user_id, status, id DESC);

CREATE TABLE IF NOT EXISTS tb_live_chat_session (
    id BIGSERIAL PRIMARY KEY,
    invite_id BIGINT NOT NULL UNIQUE,
    participant_one_id BIGINT NOT NULL,
    participant_two_id BIGINT NOT NULL,
    duration_minutes INTEGER NOT NULL,
    status VARCHAR(24) NOT NULL,
    started_at TIMESTAMP NOT NULL,
    ends_at TIMESTAMP NOT NULL,
    ended_at TIMESTAMP NULL,
    ended_by_user_id BIGINT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_live_chat_session_duration CHECK (duration_minutes IN (10, 15)),
    CONSTRAINT chk_live_chat_session_status CHECK (status IN ('ACTIVE', 'ENDED', 'EXPIRED'))
);

CREATE INDEX IF NOT EXISTS idx_live_chat_session_participant_one
    ON tb_live_chat_session (participant_one_id, status, id DESC);

CREATE INDEX IF NOT EXISTS idx_live_chat_session_participant_two
    ON tb_live_chat_session (participant_two_id, status, id DESC);

CREATE TABLE IF NOT EXISTS tb_live_chat_message (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL,
    sender_user_id BIGINT NOT NULL,
    message_body VARCHAR(2000) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_live_chat_message_session_id
    ON tb_live_chat_message (session_id, id);
