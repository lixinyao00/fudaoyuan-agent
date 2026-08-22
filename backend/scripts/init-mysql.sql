-- MySQL 初始化脚本
-- 用法: mysql -u daoyuan -p daoyuan < scripts/init-mysql.sql

CREATE TABLE IF NOT EXISTS document (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    file_name VARCHAR(500) NOT NULL,
    version VARCHAR(50) NOT NULL,
    file_path VARCHAR(1000),
    sha256 VARCHAR(128) NOT NULL,
    page_count INT,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    effective_date DATE,
    review_notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS knowledge_chunk (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    chunk_id VARCHAR(64) NOT NULL,
    document_id BIGINT NOT NULL,
    page_start INT NOT NULL,
    page_end INT NOT NULL,
    chapter_title VARCHAR(500),
    regulation_name VARCHAR(500),
    article_ref VARCHAR(200),
    categories VARCHAR(500),
    content TEXT NOT NULL,
    source_hash VARCHAR(128),
    embedding_json TEXT,
    review_status VARCHAR(20) DEFAULT 'PENDING',
    canonical_flag TINYINT(1) DEFAULT 1,
    effective_date TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS conversation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(100),
    channel VARCHAR(20) DEFAULT 'web',
    started_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_message_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS chat_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    message_id VARCHAR(64) NOT NULL,
    conversation_id VARCHAR(64) NOT NULL,
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    category VARCHAR(50),
    related_categories VARCHAR(500),
    risk_level VARCHAR(20) DEFAULT 'NONE',
    needs_human_review TINYINT(1) DEFAULT 0,
    confidence DOUBLE DEFAULT 0.0,
    request_id VARCHAR(64),
    prompt_version VARCHAR(20),
    fallback TINYINT(1) DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS chat_citation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    message_id VARCHAR(64) NOT NULL,
    chunk_id VARCHAR(64),
    document_name VARCHAR(500),
    chapter_title VARCHAR(500),
    page_start INT,
    page_end INT,
    article_ref VARCHAR(200),
    content_snippet TEXT,
    score DOUBLE DEFAULT 0.0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS feedback (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    message_id VARCHAR(64) NOT NULL,
    feedback_type VARCHAR(20) NOT NULL,
    reason TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS risk_event (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    message_id VARCHAR(64),
    conversation_id VARCHAR(64),
    risk_level VARCHAR(20) NOT NULL,
    risk_type VARCHAR(50),
    description TEXT,
    status VARCHAR(20) DEFAULT 'OPEN',
    handler VARCHAR(100),
    handler_notes TEXT,
    resolved TINYINT(1) DEFAULT 0,
    resolved_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS review_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    target_id VARCHAR(64) NOT NULL,
    target_type VARCHAR(50) NOT NULL,
    status VARCHAR(20) DEFAULT 'PENDING',
    reviewer VARCHAR(100),
    notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    reviewed_at TIMESTAMP NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS sensitive_rule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    keyword VARCHAR(200) NOT NULL,
    risk_type VARCHAR(50),
    risk_level VARCHAR(20),
    enabled TINYINT(1) DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS audit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    action VARCHAR(50) NOT NULL,
    operator VARCHAR(100),
    target_type VARCHAR(50),
    target_id VARCHAR(64),
    details TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Indexes
CREATE INDEX idx_chunk_document ON knowledge_chunk(document_id);
CREATE INDEX idx_chunk_category ON knowledge_chunk(categories(100));
CREATE INDEX idx_chunk_status ON knowledge_chunk(review_status);
CREATE INDEX idx_chunk_page ON knowledge_chunk(page_start);
CREATE INDEX idx_chunk_hash ON knowledge_chunk(source_hash);
CREATE INDEX idx_conv_id ON conversation(conversation_id);
CREATE INDEX idx_msg_conv ON chat_message(conversation_id);
CREATE INDEX idx_msg_id ON chat_message(message_id);
CREATE INDEX idx_citation_msg ON chat_citation(message_id);
CREATE INDEX idx_feedback_msg ON feedback(message_id);
CREATE INDEX idx_risk_conv ON risk_event(conversation_id);
CREATE INDEX idx_risk_status ON risk_event(status);
CREATE INDEX idx_audit_action ON audit_log(action);

-- Fulltext index for MySQL content search
CREATE FULLTEXT INDEX idx_chunk_content_ft ON knowledge_chunk(content);

-- Initial sensitive rules
INSERT IGNORE INTO sensitive_rule (keyword, risk_type, risk_level, enabled) VALUES
('自杀', 'SELF_HARM', 'CRITICAL', 1),
('自残', 'SELF_HARM', 'CRITICAL', 1),
('想死', 'SELF_HARM', 'CRITICAL', 1),
('杀人', 'VIOLENCE', 'CRITICAL', 1),
('放火', 'VIOLENCE', 'CRITICAL', 1),
('抑郁', 'MENTAL_HEALTH', 'HIGH', 1),
('崩溃', 'MENTAL_HEALTH', 'HIGH', 1),
('火灾', 'SAFETY', 'HIGH', 1),
('失联', 'SAFETY', 'HIGH', 1),
('打架', 'VIOLENCE', 'MEDIUM', 1);
