-- Enable vector extension
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS hstore;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Spring AI default vector store table
CREATE TABLE IF NOT EXISTS vector_store (
	id uuid DEFAULT uuid_generate_v4() PRIMARY KEY,
	content text,
	metadata json,
	embedding vector(1536) -- Adjust dimension based on your model
);

CREATE INDEX ON vector_store USING HNSW (embedding vector_cosine_ops);

-- Custom Document Table (Metadata)
CREATE TABLE IF NOT EXISTS document (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    title VARCHAR(255) NOT NULL,
    source_url VARCHAR(1024),
    file_path VARCHAR(1024),
    mime_type VARCHAR(100),
    total_tokens INTEGER,
    chunk_count INTEGER,
    metadata JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    is_deleted BOOLEAN DEFAULT FALSE
);

-- Custom Document Chunk Table (Vector Data)
CREATE TABLE IF NOT EXISTS document_chunk (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    document_id UUID REFERENCES document(id),
    content TEXT NOT NULL,
    embedding vector(1536), -- Adjust dimension based on your model
    token_count INTEGER,
    chunk_index INTEGER,
    metadata JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX ON document_chunk USING hnsw (embedding vector_cosine_ops);
CREATE INDEX ON document_chunk (document_id);
CREATE INDEX ON document_chunk USING gin (metadata);

-- Session Archives Table (from session_archive.sql)
CREATE TABLE IF NOT EXISTS session_archives (
    id VARCHAR(36) PRIMARY KEY,
    conversation_id VARCHAR(255) NOT NULL,
    type VARCHAR(50) NOT NULL,
    payload JSONB NOT NULL,
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_session_archives_conversation_id ON session_archives(conversation_id);
CREATE INDEX IF NOT EXISTS idx_session_archives_timestamp ON session_archives(timestamp);

