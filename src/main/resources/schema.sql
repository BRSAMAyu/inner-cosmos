CREATE TABLE IF NOT EXISTS tb_user (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(64) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  nickname VARCHAR(64),
  avatar_url VARCHAR(255),
  email VARCHAR(128),
  role VARCHAR(32) NOT NULL,
  status VARCHAR(32) NOT NULL,
  last_login_at TIMESTAMP NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS tb_user_profile (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  aurora_name VARCHAR(64),
  aurora_tone VARCHAR(64),
  preferred_input_type VARCHAR(32),
  social_reachability_status VARCHAR(32),
  bio TEXT,
  reflection_depth INT DEFAULT 3,
  allow_memory_recall BOOLEAN DEFAULT TRUE,
  quiet_hours_start VARCHAR(8),
  quiet_hours_end VARCHAR(8),
  proactive_sensitivity INT DEFAULT 3,
  allow_multi_message BOOLEAN DEFAULT TRUE,
  focus_mode_enabled BOOLEAN DEFAULT FALSE,
  focus_windows_json TEXT,
  current_environment_label VARCHAR(160),
  weather_awareness_enabled BOOLEAN DEFAULT TRUE,
  time_awareness_enabled BOOLEAN DEFAULT TRUE,
  preferred_model VARCHAR(64),
  proactive_intensity VARCHAR(32) DEFAULT 'COMPANION',
  sleep_window_start VARCHAR(8),
  sleep_window_end VARCHAR(8),
  boost_until TIMESTAMP NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE INDEX uk_user_profile_user (user_id)
);

CREATE TABLE IF NOT EXISTS tb_friend_relation (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  requester_id BIGINT NOT NULL,
  addressee_id BIGINT NOT NULL,
  status VARCHAR(32) NOT NULL,
  source VARCHAR(64),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_friend_pair (requester_id, addressee_id),
  INDEX idx_friend_requester (requester_id),
  INDEX idx_friend_addressee (addressee_id)
);

CREATE TABLE IF NOT EXISTS tb_social_group (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  owner_user_id BIGINT NOT NULL,
  group_name VARCHAR(120) NOT NULL,
  intro TEXT,
  visibility VARCHAR(32) DEFAULT 'PRIVATE',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_social_group_owner (owner_user_id)
);

CREATE TABLE IF NOT EXISTS tb_social_group_member (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  group_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  member_role VARCHAR(32) DEFAULT 'MEMBER',
  status VARCHAR(32) DEFAULT 'ACTIVE',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_group_member (group_id, user_id),
  INDEX idx_group_member_user (user_id)
);

CREATE TABLE IF NOT EXISTS tb_dialog_session (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  title VARCHAR(160),
  session_type VARCHAR(32),
  status VARCHAR(32),
  summary_anchor TEXT,
  message_count INT DEFAULT 0,
  token_estimate INT DEFAULT 0,
  started_at TIMESTAMP NULL,
  ended_at TIMESTAMP NULL,
  goodbye_trigger VARCHAR(32),
  current_mode VARCHAR(32) DEFAULT 'DAILY_TALK',
  preferred_model VARCHAR(64),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_dialog_user (user_id)
);

CREATE TABLE IF NOT EXISTS tb_dialog_message (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  session_id BIGINT NOT NULL,
  user_id BIGINT,
  speaker VARCHAR(32),
  text_content TEXT,
  input_type VARCHAR(32),
  audio_duration_sec INT,
  speech_rate DOUBLE,
  pause_count INT,
  long_pause_count INT,
  emotion_hint VARCHAR(64),
  safety_level VARCHAR(32),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_message_session (session_id),
  INDEX idx_message_user (user_id),
  CONSTRAINT fk_message_session FOREIGN KEY (session_id) REFERENCES tb_dialog_session(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS tb_user_identity (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  issuer VARCHAR(255) NOT NULL,
  subject VARCHAR(255) NOT NULL,
  email_snapshot VARCHAR(128),
  last_authenticated_at TIMESTAMP NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_user_identity_issuer_subject UNIQUE (issuer, subject),
  CONSTRAINT fk_user_identity_user FOREIGN KEY (user_id) REFERENCES tb_user(id) ON DELETE CASCADE,
  INDEX idx_user_identity_user (user_id)
);

-- INNO-CONV-001: durable, replayable Aurora turn choreography.  The unique
-- user-message and turn/version keys are the database authority across replicas.
CREATE TABLE IF NOT EXISTS tb_conversation_turn (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  session_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  user_message_id BIGINT NOT NULL,
  active_plan_id BIGINT,
  status VARCHAR(32) NOT NULL,
  next_event_sequence INT NOT NULL DEFAULT 1,
  started_at TIMESTAMP NULL,
  completed_at TIMESTAMP NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_conversation_turn_user_message (user_message_id),
  INDEX idx_conversation_turn_owner (user_id, id),
  INDEX idx_conversation_turn_session (session_id),
  CONSTRAINT fk_conversation_turn_session FOREIGN KEY (session_id) REFERENCES tb_dialog_session(id) ON DELETE CASCADE,
  CONSTRAINT fk_conversation_turn_user_message FOREIGN KEY (user_message_id) REFERENCES tb_dialog_message(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS tb_turn_plan (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  turn_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  plan_version INT NOT NULL,
  commit_slot INT,
  status VARCHAR(32) NOT NULL,
  intent VARCHAR(160),
  posture VARCHAR(160),
  stop_condition VARCHAR(64),
  committed_at TIMESTAMP NULL,
  cancelled_at TIMESTAMP NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_turn_plan_version (turn_id, plan_version),
  UNIQUE KEY uk_turn_plan_single_commit (turn_id, commit_slot),
  INDEX idx_turn_plan_owner (user_id, turn_id),
  CONSTRAINT ck_turn_plan_commit_slot CHECK ((status = 'COMMITTED' AND commit_slot = 1) OR (status <> 'COMMITTED' AND commit_slot IS NULL)),
  CONSTRAINT fk_turn_plan_turn FOREIGN KEY (turn_id) REFERENCES tb_conversation_turn(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS tb_message_bubble (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  turn_id BIGINT NOT NULL,
  plan_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  dialog_message_id BIGINT,
  bubble_order INT NOT NULL,
  purpose VARCHAR(64) NOT NULL,
  content TEXT NOT NULL,
  status VARCHAR(32) NOT NULL,
  send_after_ms INT NOT NULL DEFAULT 0,
  delivered_chars INT NOT NULL DEFAULT 0,
  requires_no_interruption BOOLEAN NOT NULL DEFAULT FALSE,
  planned_at TIMESTAMP NULL,
  sent_at TIMESTAMP NULL,
  cancelled_at TIMESTAMP NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_message_bubble_order (plan_id, bubble_order),
  UNIQUE KEY uk_message_bubble_dialog (dialog_message_id),
  INDEX idx_message_bubble_owner (user_id, turn_id),
  CONSTRAINT fk_message_bubble_turn FOREIGN KEY (turn_id) REFERENCES tb_conversation_turn(id) ON DELETE CASCADE,
  CONSTRAINT fk_message_bubble_plan FOREIGN KEY (plan_id) REFERENCES tb_turn_plan(id) ON DELETE CASCADE,
  CONSTRAINT fk_message_bubble_dialog FOREIGN KEY (dialog_message_id) REFERENCES tb_dialog_message(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS tb_conversation_event (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  turn_id BIGINT NOT NULL,
  plan_id BIGINT,
  bubble_id BIGINT,
  user_id BIGINT NOT NULL,
  event_sequence INT NOT NULL,
  event_type VARCHAR(64) NOT NULL,
  causation_id VARCHAR(96),
  payload_json TEXT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_conversation_event_sequence (turn_id, event_sequence),
  INDEX idx_conversation_event_owner (user_id, turn_id),
  CONSTRAINT fk_conversation_event_turn FOREIGN KEY (turn_id) REFERENCES tb_conversation_turn(id) ON DELETE CASCADE,
  CONSTRAINT fk_conversation_event_plan FOREIGN KEY (plan_id) REFERENCES tb_turn_plan(id) ON DELETE CASCADE,
  CONSTRAINT fk_conversation_event_bubble FOREIGN KEY (bubble_id) REFERENCES tb_message_bubble(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS tb_generation_attempt (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  turn_id BIGINT NOT NULL,
  plan_id BIGINT,
  user_id BIGINT NOT NULL,
  attempt_number INT NOT NULL,
  status VARCHAR(32) NOT NULL,
  provider VARCHAR(64),
  model_name VARCHAR(128),
  started_at TIMESTAMP NULL,
  completed_at TIMESTAMP NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_generation_attempt (turn_id, attempt_number),
  INDEX idx_generation_attempt_owner (user_id, turn_id),
  CONSTRAINT fk_generation_attempt_turn FOREIGN KEY (turn_id) REFERENCES tb_conversation_turn(id) ON DELETE CASCADE,
  CONSTRAINT fk_generation_attempt_plan FOREIGN KEY (plan_id) REFERENCES tb_turn_plan(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS tb_memory_card (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  source_session_id BIGINT,
  title VARCHAR(160),
  summary TEXT,
  memory_type VARCHAR(32),
  emotion_tags TEXT,
  keyword_tags TEXT,
  people_tags TEXT,
  intensity_score DOUBLE DEFAULT 0,
  recurrence_count INT DEFAULT 0,
  user_importance DOUBLE DEFAULT 0,
  trigger_count INT DEFAULT 0,
  emotional_gravity DOUBLE DEFAULT 0,
  last_touched_at TIMESTAMP NULL,
  visibility_level VARCHAR(32),
  status VARCHAR(32),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_memory_user (user_id),
  CONSTRAINT fk_memory_session FOREIGN KEY (source_session_id) REFERENCES tb_dialog_session(id) ON DELETE SET NULL,
  -- M-008: one settlement MemoryCard per (user, session) — defense-in-depth against the
  -- duplicate-card race (M-007 prevents the double-fire at the source). NULL source_session_id
  -- (e.g. shredder cards) stays multi-allowed (H2/MySQL treat NULLs as distinct under UNIQUE).
  CONSTRAINT uk_memory_card_user_session UNIQUE (user_id, source_session_id)
);

CREATE TABLE IF NOT EXISTS tb_thought_fragment (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  memory_card_id BIGINT,
  fragment_type VARCHAR(32),
  raw_excerpt TEXT,
  ai_analysis TEXT,
  reframe_text TEXT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_fragment_user (user_id),
  CONSTRAINT fk_fragment_memory FOREIGN KEY (memory_card_id) REFERENCES tb_memory_card(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS tb_emotion_trace (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  source_session_id BIGINT,
  emotion_name VARCHAR(64),
  emotion_score DOUBLE,
  weather_type VARCHAR(32),
  trigger_scene TEXT,
  record_date DATE,
  emotion_spectrum TEXT,
  analysis_source VARCHAR(32),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_emotion_trace_user (user_id),
  -- IC-EMO-001 de-dup: one trace per (user, session). Both DialogFinished
  -- listeners upsert on this key. Diary rows have source_session_id = NULL;
  -- MySQL and H2(MODE=MySQL) treat multiple NULLs as distinct under a UNIQUE
  -- index, so multiple null-session diary rows remain allowed. This is the
  -- belt-and-suspenders guard behind the app-level upsert + race fallback.
  CONSTRAINT uk_emotion_trace_user_session UNIQUE (user_id, source_session_id)
);

CREATE TABLE IF NOT EXISTS tb_todo_item (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  source_memory_card_id BIGINT,
  task_name VARCHAR(160),
  description TEXT,
  priority VARCHAR(32),
  status VARCHAR(32),
  deadline TIMESTAMP NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_todo_memory FOREIGN KEY (source_memory_card_id) REFERENCES tb_memory_card(id) ON DELETE SET NULL,
  INDEX idx_todo_user_status (user_id, status)
);

CREATE TABLE IF NOT EXISTS tb_echo_capsule (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  owner_user_id BIGINT,
  capsule_type VARCHAR(32),
  pseudonym VARCHAR(100),
  intro TEXT,
  persona_prompt TEXT,
  public_tags TEXT,
  authorized_memory_ids TEXT,
  echo_energy DOUBLE DEFAULT 0,
  freshness_score DOUBLE DEFAULT 0,
  conversation_limit_per_day INT DEFAULT 5,
  visibility_status VARCHAR(32),
  is_public BOOLEAN DEFAULT TRUE,
  last_memory_update_at TIMESTAMP NULL,
  owner_context_note TEXT,
  style_profile_json TEXT,
  context_preview_json TEXT,
  stand_in_enabled BOOLEAN DEFAULT FALSE,
  real_contact_policy VARCHAR(32),
  last_activity_at TIMESTAMP NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_capsule_public (is_public, visibility_status),
  INDEX idx_capsule_owner (owner_user_id)
);

CREATE TABLE IF NOT EXISTS tb_capsule_boundary (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  capsule_id BIGINT NOT NULL,
  allow_topics TEXT,
  blocked_topics TEXT,
  max_conversation_turns INT DEFAULT 5,
  allow_letter_request BOOLEAN DEFAULT TRUE,
  privacy_level VARCHAR(32),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_boundary_capsule FOREIGN KEY (capsule_id) REFERENCES tb_echo_capsule(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS tb_capsule_usage_quota (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  visitor_user_id BIGINT NOT NULL,
  capsule_id BIGINT NOT NULL,
  quota_date DATE NOT NULL,
  turn_count INT DEFAULT 0,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_quota (visitor_user_id, capsule_id, quota_date)
);

CREATE TABLE IF NOT EXISTS tb_persona_chat_session (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  visitor_user_id BIGINT,
  capsule_id BIGINT NOT NULL,
  status VARCHAR(32),
  turn_count INT DEFAULT 0,
  daily_limit INT DEFAULT 5,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_persona_session_visitor (visitor_user_id),
  CONSTRAINT fk_persona_session_capsule FOREIGN KEY (capsule_id) REFERENCES tb_echo_capsule(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS tb_persona_chat_message (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  session_id BIGINT NOT NULL,
  sender_type VARCHAR(32),
  text_content TEXT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_persona_message_session FOREIGN KEY (session_id) REFERENCES tb_persona_chat_session(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS tb_slow_letter (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  sender_user_id BIGINT,
  receiver_user_id BIGINT,
  receiver_capsule_id BIGINT,
  thread_id BIGINT NULL,
  title VARCHAR(160),
  letter_body TEXT,
  status VARCHAR(32),
  parallax_distance INT DEFAULT 0,
  estimated_arrival_at TIMESTAMP NULL,
  sent_at TIMESTAMP NULL,
  delivered_at TIMESTAMP NULL,
  read_at TIMESTAMP NULL,
  replied_at TIMESTAMP NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_letter_sender (sender_user_id),
  INDEX idx_letter_receiver (receiver_user_id),
  INDEX idx_letter_status (status)
);

CREATE TABLE IF NOT EXISTS tb_letter_status_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  letter_id BIGINT NOT NULL,
  from_status VARCHAR(32),
  to_status VARCHAR(32),
  operator_user_id BIGINT,
  reason TEXT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_status_log_letter FOREIGN KEY (letter_id) REFERENCES tb_slow_letter(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS tb_ai_interaction_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT,
  module_name VARCHAR(64),
  provider VARCHAR(64),
  model_name VARCHAR(128),
  request_prompt TEXT,
  response_text TEXT,
  request_json TEXT,
  response_json TEXT,
  success BOOLEAN,
  fallback_used BOOLEAN DEFAULT FALSE,
  error_message TEXT,
  latency_ms BIGINT,
  token_input_estimate INT,
  token_output_estimate INT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_ai_log_user (user_id)
);

CREATE TABLE IF NOT EXISTS tb_safety_event (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT,
  session_id BIGINT,
  message_id BIGINT,
  risk_type VARCHAR(64),
  risk_level VARCHAR(32),
  matched_rule VARCHAR(160),
  handled_action VARCHAR(64),
  trigger_scene TEXT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_safety_event_user (user_id)
);

CREATE TABLE IF NOT EXISTS tb_report_record (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  reporter_user_id BIGINT,
  target_type VARCHAR(32),
  target_id BIGINT,
  reason TEXT,
  status VARCHAR(32),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_report_status (status)
);

-- Phase 1: Memory System Deepening

CREATE TABLE IF NOT EXISTS tb_daily_record (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  record_date DATE NOT NULL,
  source_session_id BIGINT,
  theme VARCHAR(200),
  event_summary TEXT,
  emotion_weather VARCHAR(32),
  cognitive_summary TEXT,
  todo_summary TEXT,
  aurora_summary TEXT,
  capsule_suggested BOOLEAN DEFAULT FALSE,
  user_accepted BOOLEAN DEFAULT FALSE,
  status VARCHAR(32) DEFAULT 'DRAFT',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_daily_record_user_date (user_id, record_date),
  UNIQUE KEY uk_daily_record_user_date (user_id, record_date)
);

CREATE TABLE IF NOT EXISTS tb_event_card (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  source_session_id BIGINT,
  memory_card_id BIGINT,
  event_title VARCHAR(200),
  event_summary TEXT,
  event_time_label VARCHAR(100),
  scene TEXT,
  people_tags TEXT,
  emotion_tags TEXT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_event_card_user (user_id)
);

CREATE TABLE IF NOT EXISTS tb_relation_mention (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  source_session_id BIGINT,
  memory_card_id BIGINT,
  relation_label VARCHAR(100),
  relation_type VARCHAR(32),
  emotion_tags TEXT,
  trigger_summary TEXT,
  boundary_hint TEXT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_relation_mention_user (user_id)
);

CREATE TABLE IF NOT EXISTS tb_memory_theme (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  theme_name VARCHAR(160) NOT NULL,
  theme_summary TEXT,
  theme_type VARCHAR(32),
  keywords TEXT,
  memory_count INT DEFAULT 0,
  average_gravity DOUBLE DEFAULT 0,
  last_touched_at TIMESTAMP NULL,
  status VARCHAR(32) DEFAULT 'ACTIVE',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_memory_theme_user (user_id)
);

CREATE TABLE IF NOT EXISTS tb_user_correction (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  target_type VARCHAR(32) NOT NULL,
  target_id BIGINT NOT NULL,
  field_name VARCHAR(64) NOT NULL,
  old_value TEXT,
  new_value TEXT,
  reason TEXT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_correction_user (user_id)
);

CREATE TABLE IF NOT EXISTS tb_weekly_review (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  week_start_date DATE NOT NULL,
  week_end_date DATE NOT NULL,
  dominant_theme VARCHAR(200),
  theme_summary TEXT,
  emotion_trend VARCHAR(500),
  completed_todos INT DEFAULT 0,
  total_todos INT DEFAULT 0,
  gravity_change_summary TEXT,
  aurora_observation TEXT,
  status VARCHAR(32) DEFAULT 'DRAFT',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_weekly_review_user (user_id)
);

-- Phase 2: Aurora Agent Upgrade

CREATE TABLE IF NOT EXISTS tb_dialog_summary (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  session_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  summary_text TEXT,
  key_topics TEXT,
  emotion_tone VARCHAR(64),
  message_count_at_summary INT DEFAULT 0,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_dialog_summary_session (session_id),
  INDEX idx_dialog_summary_user (user_id)
);

CREATE TABLE IF NOT EXISTS tb_voice_transcription (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  session_id BIGINT,
  message_id BIGINT,
  original_text TEXT,
  edited_text TEXT,
  audio_duration_sec INT,
  speech_rate DOUBLE,
  pause_count INT,
  emotion_hint VARCHAR(64),
  status VARCHAR(32) DEFAULT 'RAW',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_voice_transcription_user (user_id),
  INDEX idx_voice_transcription_session (session_id)
);

-- Phase 4: Capsule Authorization

CREATE TABLE IF NOT EXISTS tb_authorized_memory_ref (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  capsule_id BIGINT NOT NULL,
  memory_card_id BIGINT NOT NULL,
  abstract_excerpt TEXT,
  authorization_status VARCHAR(32) DEFAULT 'AUTHORIZED',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_auth_mem_capsule (capsule_id),
  CONSTRAINT fk_auth_mem_capsule FOREIGN KEY (capsule_id) REFERENCES tb_echo_capsule(id) ON DELETE CASCADE,
  CONSTRAINT fk_auth_mem_memory FOREIGN KEY (memory_card_id) REFERENCES tb_memory_card(id) ON DELETE CASCADE
);

-- Phase 5: Slow Letter Maturation

CREATE TABLE IF NOT EXISTS tb_letter_thread (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  first_letter_id BIGINT,
  participant_a BIGINT NOT NULL,
  participant_b BIGINT NOT NULL,
  capsule_id BIGINT,
  status VARCHAR(32) DEFAULT 'ACTIVE',
  last_letter_at TIMESTAMP NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_thread_participants (participant_a, participant_b)
);

CREATE TABLE IF NOT EXISTS tb_block_relation (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  blocker_user_id BIGINT NOT NULL,
  blocked_user_id BIGINT NOT NULL,
  reason TEXT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_block_blocker (blocker_user_id)
);

CREATE TABLE IF NOT EXISTS tb_belief_pattern (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  belief_content TEXT NOT NULL,
  belief_type VARCHAR(32),
  belief_category VARCHAR(64),
  strength_score DOUBLE DEFAULT 0.5,
  supporting_memory_ids TEXT,
  contradicting_memory_ids TEXT,
  first_detected_at TIMESTAMP NULL,
  last_confirmed_at TIMESTAMP NULL,
  confirmation_count INT DEFAULT 1,
  status VARCHAR(32) DEFAULT 'ACTIVE',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_belief_user (user_id),
  INDEX idx_belief_type (belief_type),
  INDEX idx_belief_strength (strength_score)
);

CREATE TABLE IF NOT EXISTS tb_emotion_timeline (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  record_date DATE NOT NULL,
  dominant_emotion VARCHAR(64),
  emotion_spectrum TEXT,
  intensity_average DOUBLE DEFAULT 0,
  trigger_summary TEXT,
  memory_count INT DEFAULT 0,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_emotion_timeline_user_date (user_id, record_date)
);

CREATE TABLE IF NOT EXISTS tb_ab_test_config (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  test_name VARCHAR(128) NOT NULL UNIQUE,
  description TEXT,
  enabled BOOLEAN DEFAULT TRUE,
  mock_percentage INT DEFAULT 50,
  control_group VARCHAR(32),
  total_participants BIGINT DEFAULT 0,
  start_time TIMESTAMP NULL,
  end_time TIMESTAMP NULL,
  status VARCHAR(32) DEFAULT 'ACTIVE',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_ab_test_enabled (enabled),
  INDEX idx_ab_test_status (status)
);

CREATE TABLE IF NOT EXISTS tb_ab_test_metrics (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  test_name VARCHAR(128) NOT NULL,
  assigned_group VARCHAR(32),
  module_name VARCHAR(64),
  request_count INT DEFAULT 1,
  avg_latency DOUBLE DEFAULT 0,
  success_count INT DEFAULT 0,
  fallback_count INT DEFAULT 0,
  success_rate DOUBLE DEFAULT 0,
  last_request_at TIMESTAMP NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_ab_metrics_user (user_id),
  INDEX idx_ab_metrics_test (test_name),
  INDEX idx_ab_metrics_group (assigned_group)
);

CREATE TABLE IF NOT EXISTS tb_user_portrait (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  dim VARCHAR(64) NOT NULL,
  value_json TEXT NOT NULL,
  score DOUBLE DEFAULT 0.5,
  confidence DOUBLE DEFAULT 0.0,
  evidence_refs TEXT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_dim (user_id, dim)
);

CREATE TABLE IF NOT EXISTS tb_user_portrait_history (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  dim VARCHAR(64) NOT NULL,
  value_json TEXT,
  score DOUBLE,
  confidence DOUBLE,
  evidence_refs TEXT,
  recorded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS tb_aurora_self_profile (
  id INT PRIMARY KEY,
  identity_json TEXT NOT NULL,
  mission_json TEXT,
  voice_style_json TEXT,
  stable_boundaries_json TEXT,
  continuity_rules_json TEXT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS tb_agent_user_relationship (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  relationship_stage VARCHAR(32) DEFAULT 'new_user',
  intimacy_level INT DEFAULT 0,
  trust_level INT DEFAULT 0,
  familiarity_level INT DEFAULT 0,
  user_disclosure_level INT DEFAULT 0,
  aurora_role_in_user_life TEXT,
  shared_history_refs TEXT,
  interaction_rituals TEXT,
  preferred_addressing VARCHAR(32),
  relationship_boundaries TEXT,
  continuity_anchors TEXT,
  last_stage_change_at TIMESTAMP NULL,
  last_updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE INDEX uk_agent_relationship_user (user_id)
);

CREATE TABLE IF NOT EXISTS tb_relationship_event (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  event_type VARCHAR(64) NOT NULL,
  evidence_turn_ids TEXT,
  delta_proposed TEXT,
  applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS tb_rupture_repair_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  event TEXT,
  user_feedback TEXT,
  repair_action TEXT,
  status VARCHAR(16) DEFAULT 'open',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS tb_user_long_term_memory (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  fact_type VARCHAR(32) NOT NULL,
  fact_value TEXT NOT NULL,
  source_session_id BIGINT,
  confidence DOUBLE DEFAULT 0.7,
  privacy_level VARCHAR(16) DEFAULT 'INNER',
  user_approved BOOLEAN DEFAULT FALSE,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS tb_session_summary (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  session_id BIGINT,
  summary_2_sentences TEXT,
  key_topics TEXT,
  emotional_arc VARCHAR(32),
  started_at TIMESTAMP,
  closed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Phase 6: Admin

CREATE TABLE IF NOT EXISTS tb_model_config (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  config_key VARCHAR(128) NOT NULL UNIQUE,
  config_value TEXT,
  description VARCHAR(255),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS tb_prompt_template (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  prompt_key VARCHAR(128) NOT NULL,
  version INT DEFAULT 1,
  content TEXT NOT NULL,
  description VARCHAR(255),
  enabled BOOLEAN DEFAULT TRUE,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_prompt_key_version (prompt_key, version)
);

CREATE TABLE IF NOT EXISTS tb_admin_action_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  admin_user_id BIGINT NOT NULL,
  action_type VARCHAR(64) NOT NULL,
  target_type VARCHAR(32),
  target_id BIGINT,
  detail TEXT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_admin_action_admin (admin_user_id)
);

-- Phase 7: Capsule Sync + PII Filter

CREATE TABLE IF NOT EXISTS tb_capsule_sync_queue (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  capsule_id BIGINT NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  proposed_context_diff TEXT,
  attempt_count INT DEFAULT 0,
  last_error TEXT NULL,
  failed_at TIMESTAMP NULL,
  next_retry_at TIMESTAMP NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  decided_at TIMESTAMP NULL,
  INDEX idx_sync_user (user_id),
  INDEX idx_sync_status (status)
);

-- IC-CAP-002 B-3: system notifications (distinct from slow letters)
CREATE TABLE IF NOT EXISTS tb_notification (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  type VARCHAR(64),
  title VARCHAR(255),
  body TEXT,
  ref_id BIGINT NULL,
  ref_type VARCHAR(64) NULL,
  is_read BOOLEAN DEFAULT FALSE,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_notification_user (user_id)
);

-- Aurora Subjectivity + Continuity System (M0)

CREATE TABLE IF NOT EXISTS tb_aurora_constitution (
  id INT PRIMARY KEY,
  identity_json TEXT NOT NULL,
  core_values_json TEXT NOT NULL,
  product_rights_json TEXT NOT NULL,
  hard_boundaries_json TEXT NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS tb_aurora_self_model (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  dimension VARCHAR(64) NOT NULL,
  belief TEXT NOT NULL,
  confidence DOUBLE NOT NULL DEFAULT 0.5,
  evidence_refs TEXT,
  status VARCHAR(32) NOT NULL DEFAULT 'active',
  committed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  revision_count INT NOT NULL DEFAULT 1,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_self_model_user (user_id),
  INDEX idx_self_model_status (status),
  INDEX idx_self_model_user_status (user_id, status),
  UNIQUE KEY uk_self_model_user_dim (user_id, dimension, status)
);

CREATE TABLE IF NOT EXISTS tb_aurora_self_statement (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  session_id BIGINT,
  message_id BIGINT,
  statement_text TEXT NOT NULL,
  trigger_type VARCHAR(32) NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_statement_user (user_id),
  INDEX idx_statement_created (created_at),
  INDEX idx_statement_user_created (user_id, created_at)
);

CREATE TABLE IF NOT EXISTS tb_aurora_self_reflection (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  trigger_type VARCHAR(32) NOT NULL,
  depth VARCHAR(16) NOT NULL,
  summary TEXT NOT NULL,
  related_statement_id BIGINT,
  dimension VARCHAR(64),
  proposed_belief TEXT,
  confidence DOUBLE DEFAULT 0.5,
  status VARCHAR(32) NOT NULL DEFAULT 'light',
  risk_flags TEXT,
  evidence_refs TEXT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_reflection_user (user_id),
  INDEX idx_reflection_status (status),
  INDEX idx_reflection_user_status_created (user_id, status, created_at),
  INDEX idx_reflection_user_dim (user_id, dimension, status)
);

-- Proactive Engine

CREATE TABLE IF NOT EXISTS tb_proactive_event_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  event_type VARCHAR(64),
  trigger_meta TEXT,
  content TEXT,
  sent_at TIMESTAMP NULL,
  user_responded_at TIMESTAMP NULL,
  accepted BOOLEAN,
  decision_source VARCHAR(64),
  reason_internal TEXT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_proactive_event_user (user_id)
);

-- Private Timer

CREATE TABLE IF NOT EXISTS tb_private_timer (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  fire_at TIMESTAMP NULL,
  kind VARCHAR(64),
  content TEXT,
  fired_at TIMESTAMP NULL,
  cancelled_at TIMESTAMP NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_private_timer_user (user_id)
);

-- Reliable cross-process event delivery (H2 development/test representation).
CREATE TABLE IF NOT EXISTS tb_outbox_event (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  event_id UUID NOT NULL,
  dedup_key VARCHAR(200) NOT NULL UNIQUE,
  aggregate_type VARCHAR(80) NOT NULL,
  aggregate_id VARCHAR(120) NOT NULL,
  event_type VARCHAR(120) NOT NULL,
  schema_version INT NOT NULL DEFAULT 1,
  payload TEXT NOT NULL,
  trace_id VARCHAR(128),
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  attempts INT NOT NULL DEFAULT 0,
  available_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  locked_until TIMESTAMP NULL,
  locked_by VARCHAR(128),
  last_error VARCHAR(500),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  published_at TIMESTAMP NULL,
  UNIQUE (event_id)
);
CREATE INDEX IF NOT EXISTS idx_outbox_claim ON tb_outbox_event (status, available_at, locked_until, id);

CREATE TABLE IF NOT EXISTS tb_inbox_receipt (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  consumer_name VARCHAR(120) NOT NULL,
  event_id UUID NOT NULL,
  event_type VARCHAR(120) NOT NULL,
  processed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (consumer_name, event_id)
);
CREATE INDEX IF NOT EXISTS idx_inbox_processed_at ON tb_inbox_receipt (processed_at);
