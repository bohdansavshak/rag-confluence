-- Enable the pgvector extension
CREATE EXTENSION IF NOT EXISTS vector;

-- Create the vector_store table if it doesn't exist
-- This table is used by Spring AI PGVector
CREATE TABLE IF NOT EXISTS vector_store (
    id uuid DEFAULT gen_random_uuid() PRIMARY KEY,
    content text,
    metadata json,
    embedding vector(768)
);

-- Create an index on the embedding column for faster similarity searches
CREATE INDEX IF NOT EXISTS vector_store_embedding_idx ON vector_store 
USING hnsw (embedding vector_cosine_ops);

-- Grant necessary permissions
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO confluence_user;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO confluence_user;
