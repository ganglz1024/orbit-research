CREATE TABLE model_configuration (
  owner_id VARCHAR(36) PRIMARY KEY, base_url VARCHAR(1000) NOT NULL, model VARCHAR(200) NOT NULL,
  api_key_cipher LONGTEXT NOT NULL, timeout_seconds INT NOT NULL, max_output_tokens INT NOT NULL,
  include_usage BOOLEAN NOT NULL, token_parameter VARCHAR(32) NOT NULL, revision INT NOT NULL,
  updated_at VARCHAR(40) NOT NULL, FOREIGN KEY(owner_id) REFERENCES orbit_user(id)
);
CREATE TABLE research_preference (
  owner_id VARCHAR(36) PRIMARY KEY, research_context LONGTEXT NOT NULL,
  default_skill_id VARCHAR(64) NOT NULL, allow_skill_tools BOOLEAN NOT NULL, assistant_mode VARCHAR(16) NOT NULL,
  updated_at VARCHAR(40) NOT NULL, FOREIGN KEY(owner_id) REFERENCES orbit_user(id)
);
CREATE TABLE custom_skill (
  id VARCHAR(40) PRIMARY KEY, owner_id VARCHAR(36) NOT NULL, name VARCHAR(80) NOT NULL,
  description VARCHAR(600) NOT NULL, instructions LONGTEXT NOT NULL, revision INT NOT NULL,
  enabled BOOLEAN NOT NULL, deleted BOOLEAN NOT NULL, created_at VARCHAR(40) NOT NULL,
  updated_at VARCHAR(40) NOT NULL, FOREIGN KEY(owner_id) REFERENCES orbit_user(id)
);
CREATE INDEX custom_skill_owner ON custom_skill(owner_id, deleted);
ALTER TABLE run_record ADD skill_snapshot LONGTEXT;
ALTER TABLE run_record ADD model_snapshot LONGTEXT;
