-- Owner-authored sandbox feedback remains private raw data. Only allow-listed structured
-- calibration signals are copied into a later immutable Genome version.
ALTER TABLE tb_capsule_sandbox_feedback
  ADD COLUMN IF NOT EXISTS calibration_signals_json TEXT,
  ADD COLUMN IF NOT EXISTS applied_genome_version_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_capsule_sandbox_feedback_status
  ON tb_capsule_sandbox_feedback(capsule_id, status, created_at);

ALTER TABLE tb_capsule_sandbox_feedback
  ADD CONSTRAINT fk_capsule_sandbox_feedback_applied_genome
  FOREIGN KEY (applied_genome_version_id)
  REFERENCES tb_capsule_genome_version(id)
  ON DELETE SET NULL;
