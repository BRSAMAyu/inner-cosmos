-- Public-demo verification and real-provider/browser evaluation accounts are persisted test actors.
-- Older runs predate write-time classification, so reconcile them once and keep social discovery
-- based on account_kind rather than browser-side username heuristics.
UPDATE tb_user
SET account_kind = 'SYNTHETIC'
WHERE account_kind IN ('HUMAN', 'SHOWCASE')
  AND LOWER(username) ~ '^(demoproof[ab]|bench(deepseek|glm)|journey|final|guard|semantic|eval|strong)[0-9]+$';
