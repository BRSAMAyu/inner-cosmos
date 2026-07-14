ALTER TABLE message
  ADD COLUMN generation_trace JSONB NOT NULL DEFAULT '{}'::jsonb;

ALTER TABLE memory_item
  ADD COLUMN provenance JSONB NOT NULL DEFAULT '{}'::jsonb;

ALTER TABLE memory_item
  ADD COLUMN search_document TSVECTOR GENERATED ALWAYS AS (
    to_tsvector('simple'::regconfig, coalesce(content, ''))
  ) STORED;

CREATE INDEX idx_memory_search_document ON memory_item USING GIN (search_document);
CREATE INDEX idx_consent_active ON consent_grant (user_id, purpose, granted_at DESC)
  WHERE revoked_at IS NULL;
