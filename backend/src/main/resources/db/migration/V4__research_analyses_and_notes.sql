CREATE TABLE research_analysis (
 id VARCHAR(36) PRIMARY KEY, owner_id VARCHAR(36) NOT NULL, conversation_id VARCHAR(36) NOT NULL,
 request_hash VARCHAR(64) NOT NULL, task VARCHAR(24) NOT NULL, sources LONGTEXT NOT NULL,
 run_id VARCHAR(36), result LONGTEXT, created_at VARCHAR(40) NOT NULL,
 FOREIGN KEY(owner_id) REFERENCES orbit_user(id), FOREIGN KEY(conversation_id) REFERENCES conversation(id)
);
CREATE INDEX analysis_workspace ON research_analysis(owner_id, conversation_id, created_at);
CREATE TABLE research_note (
 id VARCHAR(36) PRIMARY KEY, owner_id VARCHAR(36) NOT NULL, conversation_id VARCHAR(36) NOT NULL,
 save_key VARCHAR(100) NOT NULL, request_hash VARCHAR(64) NOT NULL, analysis_id VARCHAR(36),
 title VARCHAR(180) NOT NULL, body LONGTEXT NOT NULL, snapshot LONGTEXT NOT NULL,
 revision INT NOT NULL, created_at VARCHAR(40) NOT NULL, updated_at VARCHAR(40) NOT NULL,
 UNIQUE(owner_id,save_key), FOREIGN KEY(conversation_id) REFERENCES conversation(id)
);
