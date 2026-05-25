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
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
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
  INDEX idx_message_session (session_id)
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
  INDEX idx_memory_user (user_id)
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
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
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
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
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
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
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
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_capsule_public (is_public, visibility_status)
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
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS tb_persona_chat_session (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  visitor_user_id BIGINT,
  capsule_id BIGINT NOT NULL,
  status VARCHAR(32),
  turn_count INT DEFAULT 0,
  daily_limit INT DEFAULT 5,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS tb_persona_chat_message (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  session_id BIGINT NOT NULL,
  sender_type VARCHAR(32),
  text_content TEXT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS tb_slow_letter (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  sender_user_id BIGINT,
  receiver_user_id BIGINT,
  receiver_capsule_id BIGINT,
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
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS tb_letter_status_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  letter_id BIGINT NOT NULL,
  from_status VARCHAR(32),
  to_status VARCHAR(32),
  operator_user_id BIGINT,
  reason TEXT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
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
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS tb_report_record (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  reporter_user_id BIGINT,
  target_type VARCHAR(32),
  target_id BIGINT,
  reason TEXT,
  status VARCHAR(32),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
