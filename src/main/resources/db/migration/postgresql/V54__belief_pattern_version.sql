-- CP-21 final residual (belief half): tb_belief_pattern gets the optimistic-lock
-- version column that POST /api/belief/{id}/recalculate pins. Plain ADD COLUMN with
-- DEFAULT 1 backfills every existing belief row to version 1, so legacy callers that
-- send no expectedVersion keep working (no pin = unconditional intent), while callers
-- that rendered a belief row can detect "someone recalculated/updated this before you"
-- as 409 CONFLICT. Same pattern as tb_understanding_claim.version (CP-21 portrait half).
ALTER TABLE tb_belief_pattern ADD COLUMN version INT NOT NULL DEFAULT 1;
