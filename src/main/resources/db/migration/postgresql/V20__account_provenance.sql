ALTER TABLE tb_user
  ADD COLUMN IF NOT EXISTS account_kind VARCHAR(16) NOT NULL DEFAULT 'HUMAN';

-- One-time classification of accounts produced by historical QA journeys. Runtime
-- discovery relies on account_kind, never on these legacy naming conventions.
UPDATE tb_user SET account_kind = 'SYNTHETIC'
WHERE account_kind = 'HUMAN' AND (
  LOWER(username) LIKE 'csrf%' OR LOWER(username) LIKE 'smoke%'
  OR LOWER(username) LIKE 'header%' OR LOWER(username) LIKE 'qa-%'
  OR LOWER(username) LIKE 'test-%' OR LOWER(username) LIKE 'b0observer%'
  OR LOWER(username) LIKE 'b0-observer%' OR LOWER(username) LIKE 'b0diag%'
  OR LOWER(username) LIKE 'b1register%' OR LOWER(username) LIKE 'b1routes%'
  OR LOWER(username) LIKE 'b1loading%' OR LOWER(username) LIKE 'b1pwa%'
  OR LOWER(username) LIKE 'b1decompose%' OR LOWER(username) LIKE 'b1pwainst%'
  OR LOWER(username) LIKE 'mobcheck%' OR LOWER(username) LIKE 'ordercheck%'
  OR LOWER(username) LIKE 'spacecheck%' OR LOWER(username) LIKE 'handoff\_%'
  OR LOWER(username) LIKE 'head\_%'
);

UPDATE tb_user SET account_kind = 'SYSTEM' WHERE username = 'admin';
UPDATE tb_user SET account_kind = 'DEMO' WHERE username = 'demo';
UPDATE tb_user SET account_kind = 'SHOWCASE' WHERE username IN ('river', 'cloud');

CREATE INDEX IF NOT EXISTS idx_user_discovery
  ON tb_user (status, account_kind, last_login_at DESC, id DESC);
