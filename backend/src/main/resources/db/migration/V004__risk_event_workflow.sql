-- Add status and handler fields to risk_event for review workflow
ALTER TABLE risk_event ADD COLUMN IF NOT EXISTS status VARCHAR(20) DEFAULT 'OPEN';
ALTER TABLE risk_event ADD COLUMN IF NOT EXISTS handler VARCHAR(100);
ALTER TABLE risk_event ADD COLUMN IF NOT EXISTS handler_notes TEXT;
ALTER TABLE risk_event ADD COLUMN IF NOT EXISTS resolved_at TIMESTAMP;
ALTER TABLE risk_event ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

-- Migrate existing data: set status based on resolved flag
UPDATE risk_event SET status = 'RESOLVED' WHERE resolved = TRUE AND status IS NULL;
UPDATE risk_event SET status = 'OPEN' WHERE resolved = FALSE AND status IS NULL;
