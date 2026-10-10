-- 文档表
CREATE TABLE IF NOT EXISTS document (
                                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                        title VARCHAR(200) NOT NULL,
    file_name VARCHAR(255),
    file_path VARCHAR(500),
    file_size BIGINT,
    status VARCHAR(20) DEFAULT 'UPLOADED',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    );

-- 知识分块表
CREATE TABLE IF NOT EXISTS knowledge_chunk (
                                               id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                               document_id BIGINT NOT NULL,
                                               content TEXT,
                                               chunk_index INT,
                                               token_count INT,
                                               vector_id VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (document_id) REFERENCES document(id)
    );

-- 用户表
CREATE TABLE IF NOT EXISTS sys_user (
                                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                        username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    display_name VARCHAR(100),
    student_no VARCHAR(50),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    );

-- 问答日志表
CREATE TABLE IF NOT EXISTS chat_log (
                                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                        user_id BIGINT,
                                        question TEXT NOT NULL,
                                        answer TEXT,
                                        references_json TEXT,
                                        retrieval_mode VARCHAR(20),
    latency_ms INT,
    feedback VARCHAR(20),
    feedback_note TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    );

-- 危机预警表
CREATE TABLE IF NOT EXISTS crisis_alert (
                                            id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                            chat_log_id BIGINT,
                                            user_id BIGINT,
                                            question TEXT,
                                            matched_keywords VARCHAR(500),
    level VARCHAR(20) NOT NULL DEFAULT 'WARNING',
    handled BOOLEAN NOT NULL DEFAULT FALSE,
    handled_by BIGINT,
    handled_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    );

-- 系统配置表
CREATE TABLE IF NOT EXISTS system_config (
                                             id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                             config_key VARCHAR(100) NOT NULL UNIQUE,
    config_value TEXT,
    config_type VARCHAR(20) NOT NULL DEFAULT 'STRING',
    description VARCHAR(200),
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    );