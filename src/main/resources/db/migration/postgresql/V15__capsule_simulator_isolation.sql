-- Isolated Simulator capability contract (对齐文档/16 Campaign C): a capsule compiled
-- from explicitly SIMULATOR_AUTHORIZED memories for testing/research only. Permanently
-- excluded from publish, plaza listing, matching and real visitor persona chat — enforced
-- in application code (CapsuleServiceImpl.updateVisibility/plazaCapsules, PersonaChatService).
ALTER TABLE tb_echo_capsule ADD COLUMN IF NOT EXISTS simulator_only BOOLEAN DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_capsule_simulator_only ON tb_echo_capsule(simulator_only);
