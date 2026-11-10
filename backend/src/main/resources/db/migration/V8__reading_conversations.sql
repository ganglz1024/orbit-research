CREATE TABLE reading_conversation (
 conversation_id VARCHAR(36) PRIMARY KEY,
 document_id VARCHAR(100) NOT NULL,
 document_version VARCHAR(64) NOT NULL,
 initial_page INTEGER NOT NULL,
 FOREIGN KEY (conversation_id) REFERENCES conversation(id)
);
CREATE INDEX reading_document ON reading_conversation(document_id,document_version);
CREATE TABLE reading_request_snapshot (
 run_id VARCHAR(36) PRIMARY KEY,
 snapshot TEXT NOT NULL,
 FOREIGN KEY (run_id) REFERENCES run_record(id)
);
