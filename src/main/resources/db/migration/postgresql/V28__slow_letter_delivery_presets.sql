-- Slow letters keep their ritual while supporting demo-speed and calendar-based arrival.
-- DEMO presets accelerate only elapsed time; the scheduler still performs real state transitions.
ALTER TABLE tb_slow_letter
    ADD COLUMN IF NOT EXISTS delivery_preset VARCHAR(32) NOT NULL DEFAULT 'DEMO_3M',
    ADD COLUMN IF NOT EXISTS delivery_time_zone VARCHAR(64) NULL,
    ADD COLUMN IF NOT EXISTS scheduled_arrival_at TIMESTAMP NULL;

-- Preserve the known due time for letters created before this migration.
UPDATE tb_slow_letter
SET scheduled_arrival_at = estimated_arrival_at
WHERE scheduled_arrival_at IS NULL
  AND estimated_arrival_at IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_letter_delivery_due
    ON tb_slow_letter (status, estimated_arrival_at, id);
