CREATE TABLE github_skill_import (
 owner_id VARCHAR(36) NOT NULL, source_key VARCHAR(64) NOT NULL, source_url VARCHAR(1000) NOT NULL,
 commit_sha VARCHAR(64) NOT NULL, content_hash VARCHAR(64) NOT NULL, skill_id VARCHAR(40) NOT NULL,
 created_at VARCHAR(40) NOT NULL, PRIMARY KEY(owner_id,source_key),
 FOREIGN KEY(owner_id) REFERENCES orbit_user(id), FOREIGN KEY(skill_id) REFERENCES custom_skill(id)
);
