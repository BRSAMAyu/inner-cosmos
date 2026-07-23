-- W1: per-user TTS voice preference plus Aurora "inner voice" (心声) delivery preferences.
-- Ships enabled by default (inner_voice_enabled DEFAULT TRUE) in AMBIENT mode; the user can
-- switch to ON_DEMAND (tap-to-reveal-and-play) or mute it entirely from settings. Mirrors the
-- H2 dev/test schema (schema.sql) column-for-column, per this table's existing convention.
ALTER TABLE tb_user_profile
    ADD COLUMN preferred_tts_voice_id VARCHAR(64) NULL,
    ADD COLUMN inner_voice_enabled BOOLEAN DEFAULT TRUE,
    ADD COLUMN inner_voice_mode VARCHAR(16) DEFAULT 'AMBIENT';
