-- Add regulation_name column to knowledge_chunk
ALTER TABLE knowledge_chunk ADD COLUMN IF NOT EXISTS regulation_name VARCHAR(500);
