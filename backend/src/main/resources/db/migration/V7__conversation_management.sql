ALTER TABLE conversation ADD COLUMN workspace_id VARCHAR(100);
ALTER TABLE conversation ADD COLUMN deleted_at VARCHAR(40);
ALTER TABLE conversation ADD COLUMN archived_at VARCHAR(40);
ALTER TABLE conversation ADD COLUMN pinned_at VARCHAR(40);
ALTER TABLE conversation ADD COLUMN revision INT NOT NULL DEFAULT 0;
ALTER TABLE conversation ADD COLUMN title_manual BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX conversation_scope ON conversation(owner_id, workspace_id, deleted_at, archived_at, updated_at);
