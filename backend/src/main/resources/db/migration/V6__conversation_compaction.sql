CREATE TABLE conversation_checkpoint (
  id VARCHAR(36) PRIMARY KEY, owner_id VARCHAR(36) NOT NULL, conversation_id VARCHAR(36) NOT NULL,
  summary TEXT NOT NULL, covered_ids TEXT NOT NULL, history_hash VARCHAR(64) NOT NULL,
  status VARCHAR(16) NOT NULL, created_at VARCHAR(40) NOT NULL,
  FOREIGN KEY (conversation_id) REFERENCES conversation(id)
);
CREATE INDEX checkpoint_conversation ON conversation_checkpoint(conversation_id, status, created_at);
