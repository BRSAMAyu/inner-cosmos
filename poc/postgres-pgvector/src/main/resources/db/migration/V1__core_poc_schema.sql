CREATE EXTENSION IF NOT EXISTS vector WITH SCHEMA public;

CREATE TABLE conversation_session (
  id UUID PRIMARY KEY,
  user_id BIGINT NOT NULL,
  status VARCHAR(32) NOT NULL CHECK (status IN ('ACTIVE', 'ENDED', 'ARCHIVED')),
  context JSONB NOT NULL DEFAULT '{}'::jsonb,
  version BIGINT NOT NULL DEFAULT 0,
  started_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  ended_at TIMESTAMPTZ
);
ALTER TABLE conversation_session ADD CONSTRAINT uq_conversation_owner UNIQUE (id, user_id);

CREATE TABLE message (
  id UUID PRIMARY KEY,
  session_id UUID NOT NULL,
  user_id BIGINT NOT NULL,
  role VARCHAR(16) NOT NULL CHECK (role IN ('USER', 'AURORA', 'SYSTEM')),
  content TEXT NOT NULL,
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (session_id, user_id) REFERENCES conversation_session(id, user_id) ON DELETE CASCADE
);

CREATE TABLE support_plan (
  id UUID PRIMARY KEY,
  session_id UUID NOT NULL,
  user_id BIGINT NOT NULL,
  intent VARCHAR(64) NOT NULL,
  plan JSONB NOT NULL,
  status VARCHAR(32) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (session_id, user_id) REFERENCES conversation_session(id, user_id) ON DELETE CASCADE
);

CREATE TABLE interaction_feedback (
  id UUID PRIMARY KEY,
  session_id UUID NOT NULL,
  user_id BIGINT NOT NULL,
  feedback_type VARCHAR(64) NOT NULL,
  score SMALLINT,
  detail JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (session_id, user_id) REFERENCES conversation_session(id, user_id) ON DELETE CASCADE
);

CREATE TABLE memory_item (
  id UUID PRIMARY KEY,
  user_id BIGINT NOT NULL,
  memory_type VARCHAR(32) NOT NULL,
  content TEXT NOT NULL,
  attributes JSONB NOT NULL DEFAULT '{}'::jsonb,
  keywords TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[],
  consent_scope VARCHAR(64) NOT NULL,
  retention_until TIMESTAMPTZ,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  version BIGINT NOT NULL DEFAULT 1,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
ALTER TABLE memory_item ADD CONSTRAINT uq_memory_owner UNIQUE (id, user_id);

CREATE TABLE memory_evidence (
  id UUID PRIMARY KEY,
  memory_id UUID NOT NULL,
  user_id BIGINT NOT NULL,
  source_type VARCHAR(32) NOT NULL,
  source_ref VARCHAR(160) NOT NULL,
  excerpt_hash VARCHAR(128),
  confidence DOUBLE PRECISION NOT NULL CHECK (confidence >= 0 AND confidence <= 1),
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (memory_id, user_id) REFERENCES memory_item(id, user_id) ON DELETE CASCADE
);

CREATE TABLE memory_revision (
  id UUID PRIMARY KEY,
  memory_id UUID NOT NULL,
  user_id BIGINT NOT NULL,
  from_version BIGINT NOT NULL,
  to_version BIGINT NOT NULL,
  operation VARCHAR(32) NOT NULL,
  actor_type VARCHAR(32) NOT NULL,
  reason TEXT,
  before_value JSONB,
  after_value JSONB,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (memory_id, user_id) REFERENCES memory_item(id, user_id) ON DELETE CASCADE
);

CREATE TABLE memory_embedding (
  id UUID PRIMARY KEY,
  memory_id UUID NOT NULL,
  user_id BIGINT NOT NULL,
  embedding_type VARCHAR(48) NOT NULL,
  model_id VARCHAR(96) NOT NULL,
  model_version VARCHAR(48) NOT NULL,
  source_version BIGINT NOT NULL,
  embedding public.vector(8) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (memory_id, user_id) REFERENCES memory_item(id, user_id) ON DELETE CASCADE,
  UNIQUE (memory_id, embedding_type, model_id, model_version)
);

CREATE TABLE consent_grant (
  id UUID PRIMARY KEY,
  user_id BIGINT NOT NULL,
  purpose VARCHAR(64) NOT NULL,
  data_categories TEXT[] NOT NULL,
  provider VARCHAR(96),
  region VARCHAR(48),
  consent_version VARCHAR(48) NOT NULL,
  granted_at TIMESTAMPTZ NOT NULL,
  revoked_at TIMESTAMPTZ,
  UNIQUE (user_id, purpose, consent_version)
);

CREATE TABLE retrieval_audit (
  id UUID PRIMARY KEY,
  user_id BIGINT NOT NULL,
  actor_type VARCHAR(32) NOT NULL,
  purpose VARCHAR(64) NOT NULL,
  allowed_scopes TEXT[] NOT NULL,
  query_hash VARCHAR(128) NOT NULL,
  returned_memory_ids UUID[] NOT NULL DEFAULT ARRAY[]::UUID[],
  policy_version VARCHAR(48) NOT NULL,
  occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE retention_policy (
  id UUID PRIMARY KEY,
  user_id BIGINT,
  data_category VARCHAR(64) NOT NULL,
  purpose VARCHAR(64) NOT NULL,
  retain_days INTEGER NOT NULL CHECK (retain_days >= 0),
  policy_version VARCHAR(48) NOT NULL,
  effective_at TIMESTAMPTZ NOT NULL,
  UNIQUE (user_id, data_category, purpose, policy_version)
);

CREATE TABLE outbox_event (
  id UUID PRIMARY KEY,
  aggregate_type VARCHAR(64) NOT NULL,
  aggregate_id UUID NOT NULL,
  event_type VARCHAR(96) NOT NULL,
  event_version INTEGER NOT NULL,
  user_id BIGINT,
  privacy_class VARCHAR(16) NOT NULL,
  payload JSONB NOT NULL,
  trace_id VARCHAR(64),
  occurred_at TIMESTAMPTZ NOT NULL,
  published_at TIMESTAMPTZ,
  attempts INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE ai_job (
  id UUID PRIMARY KEY,
  user_id BIGINT NOT NULL,
  job_type VARCHAR(64) NOT NULL,
  input_ref VARCHAR(160) NOT NULL,
  input_version BIGINT NOT NULL,
  consent_scope VARCHAR(64) NOT NULL,
  status VARCHAR(32) NOT NULL,
  lease_owner VARCHAR(96),
  lease_until TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE job_attempt (
  id UUID PRIMARY KEY,
  job_id UUID NOT NULL REFERENCES ai_job(id) ON DELETE CASCADE,
  attempt_no INTEGER NOT NULL,
  worker_id VARCHAR(96) NOT NULL,
  model_id VARCHAR(96),
  status VARCHAR(32) NOT NULL,
  started_at TIMESTAMPTZ NOT NULL,
  completed_at TIMESTAMPTZ,
  error_code VARCHAR(64),
  result_ref VARCHAR(160),
  UNIQUE (job_id, attempt_no)
);

CREATE INDEX idx_conversation_owner ON conversation_session (user_id, started_at DESC);
CREATE INDEX idx_message_owner_session ON message (user_id, session_id, occurred_at);
CREATE INDEX idx_memory_owner_policy ON memory_item (user_id, consent_scope, status, retention_until);
CREATE INDEX idx_memory_keywords ON memory_item USING GIN (keywords);
CREATE INDEX idx_memory_attributes ON memory_item USING GIN (attributes);
CREATE INDEX idx_embedding_owner_type ON memory_embedding (user_id, embedding_type, model_id, model_version);
CREATE INDEX idx_embedding_hnsw ON memory_embedding USING hnsw (embedding public.vector_cosine_ops);
CREATE INDEX idx_outbox_unpublished ON outbox_event (occurred_at) WHERE published_at IS NULL;
CREATE INDEX idx_ai_job_claim ON ai_job (status, lease_until, created_at);
