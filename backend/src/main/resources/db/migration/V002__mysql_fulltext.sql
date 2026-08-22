-- MySQL专用: 添加全文索引和新字段
-- 仅在MySQL环境下执行，H2不支持

-- 添加 regulation_name 字段（如果不存在）
ALTER TABLE knowledge_chunk ADD COLUMN IF NOT EXISTS regulation_name VARCHAR(500);

-- 添加风险事件工作流字段
ALTER TABLE risk_event ADD COLUMN IF NOT EXISTS status VARCHAR(20) DEFAULT 'OPEN';
ALTER TABLE risk_event ADD COLUMN IF NOT EXISTS handler VARCHAR(100);
ALTER TABLE risk_event ADD COLUMN IF NOT EXISTS handler_notes TEXT;
ALTER TABLE risk_event ADD COLUMN IF NOT EXISTS resolved_at TIMESTAMP NULL;
ALTER TABLE risk_event ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;

-- 添加全文索引（MySQL 5.7+ InnoDB支持）
-- 注意：如果已存在会报错，可忽略
CREATE FULLTEXT INDEX idx_chunk_content_ft ON knowledge_chunk(content);

-- 添加风险状态索引
CREATE INDEX idx_risk_status ON risk_event(status);
