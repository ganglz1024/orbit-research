CREATE TABLE orbit_user (
  id VARCHAR(36) PRIMARY KEY, username VARCHAR(64) NOT NULL UNIQUE,
  password_hash VARCHAR(100) NOT NULL, created_at VARCHAR(40) NOT NULL
);
CREATE TABLE conversation (
  id VARCHAR(36) PRIMARY KEY, owner_id VARCHAR(36) NOT NULL, title VARCHAR(180) NOT NULL,
  created_at VARCHAR(40) NOT NULL, updated_at VARCHAR(40) NOT NULL,
  FOREIGN KEY(owner_id) REFERENCES orbit_user(id)
);
CREATE INDEX conversation_owner ON conversation(owner_id, updated_at);
CREATE TABLE run_record (
  id VARCHAR(36) PRIMARY KEY, owner_id VARCHAR(36) NOT NULL, conversation_id VARCHAR(36) NOT NULL,
  idempotency_key VARCHAR(100) NOT NULL, request_hash VARCHAR(64) NOT NULL,
  prompt TEXT NOT NULL, document_ids TEXT NOT NULL, status VARCHAR(24) NOT NULL,
  answer TEXT NOT NULL, error_code VARCHAR(64), created_at VARCHAR(40) NOT NULL, updated_at VARCHAR(40) NOT NULL,
  prompt_tokens BIGINT, completion_tokens BIGINT,
  UNIQUE(owner_id, idempotency_key), FOREIGN KEY(conversation_id) REFERENCES conversation(id)
);
CREATE INDEX run_conversation ON run_record(conversation_id, created_at);
CREATE TABLE chat_message (
  id VARCHAR(36) PRIMARY KEY, conversation_id VARCHAR(36) NOT NULL, run_id VARCHAR(36),
  role VARCHAR(16) NOT NULL, content TEXT NOT NULL, created_at VARCHAR(40) NOT NULL,
  FOREIGN KEY(conversation_id) REFERENCES conversation(id)
);
CREATE INDEX message_conversation ON chat_message(conversation_id, created_at);
CREATE TABLE run_event (
  run_id VARCHAR(36) NOT NULL, event_id BIGINT NOT NULL, event_type VARCHAR(40) NOT NULL,
  payload TEXT NOT NULL, created_at VARCHAR(40) NOT NULL,
  PRIMARY KEY(run_id, event_id), FOREIGN KEY(run_id) REFERENCES run_record(id)
);
CREATE TABLE document (
  id VARCHAR(36) PRIMARY KEY, owner_id VARCHAR(36) NOT NULL, name VARCHAR(255) NOT NULL,
  content_hash VARCHAR(64) NOT NULL, byte_size BIGINT NOT NULL, page_count INT NOT NULL,
  parse_status VARCHAR(24) NOT NULL, extracted_text TEXT NOT NULL, created_at VARCHAR(40) NOT NULL,
  UNIQUE(owner_id, content_hash), FOREIGN KEY(owner_id) REFERENCES orbit_user(id)
);
CREATE TABLE conversation_document (
  conversation_id VARCHAR(36) NOT NULL, document_id VARCHAR(36) NOT NULL,
  PRIMARY KEY(conversation_id, document_id), FOREIGN KEY(conversation_id) REFERENCES conversation(id),
  FOREIGN KEY(document_id) REFERENCES document(id)
);
CREATE TABLE annotation (
  id VARCHAR(36) PRIMARY KEY, document_id VARCHAR(36) NOT NULL, page_number INT NOT NULL,
  kind VARCHAR(16) NOT NULL, quote_text TEXT NOT NULL, comment_text TEXT NOT NULL,
  rectangles TEXT NOT NULL, color VARCHAR(24) NOT NULL, origin VARCHAR(8) NOT NULL,
  revision INT NOT NULL, created_at VARCHAR(40) NOT NULL, updated_at VARCHAR(40) NOT NULL,
  FOREIGN KEY(document_id) REFERENCES document(id)
);
CREATE TABLE reading_position (
  document_id VARCHAR(36) PRIMARY KEY, page_number INT NOT NULL, zoom DOUBLE NOT NULL,
  rotation INT NOT NULL, updated_at VARCHAR(40) NOT NULL, FOREIGN KEY(document_id) REFERENCES document(id)
);
CREATE TABLE skill_preference (
  owner_id VARCHAR(36) NOT NULL, skill_id VARCHAR(40) NOT NULL, enabled BOOLEAN NOT NULL,
  PRIMARY KEY(owner_id, skill_id), FOREIGN KEY(owner_id) REFERENCES orbit_user(id)
);
