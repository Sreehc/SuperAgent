CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS
$$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TABLE tenant (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    code VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'active',
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ NULL,
    CONSTRAINT uk_tenant_code UNIQUE (code),
    CONSTRAINT chk_tenant_status CHECK (status IN ('active', 'disabled', 'deleted'))
);

CREATE TABLE user_account (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(128) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    email VARCHAR(255) NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'active',
    default_tenant_id BIGINT NULL REFERENCES tenant (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ NULL,
    CONSTRAINT uk_user_username UNIQUE (username)
);

CREATE TABLE tenant_member (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenant (id),
    user_id BIGINT NOT NULL REFERENCES user_account (id),
    role VARCHAR(32) NOT NULL DEFAULT 'MEMBER',
    status VARCHAR(32) NOT NULL DEFAULT 'active',
    joined_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_tenant_member UNIQUE (tenant_id, user_id),
    CONSTRAINT chk_tenant_member_role CHECK (role IN ('OWNER', 'ADMIN', 'MEMBER'))
);

CREATE TABLE refresh_token (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES user_account (id),
    tenant_id BIGINT NULL REFERENCES tenant (id),
    token_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_refresh_token_hash UNIQUE (token_hash)
);

CREATE TABLE knowledge_base (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenant (id),
    name VARCHAR(128) NOT NULL,
    description TEXT NULL,
    visibility VARCHAR(32) NOT NULL DEFAULT 'tenant',
    status VARCHAR(32) NOT NULL DEFAULT 'draft',
    owner_id BIGINT NOT NULL REFERENCES user_account (id),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ NULL
);

CREATE UNIQUE INDEX uk_kb_tenant_name_active
    ON knowledge_base (tenant_id, name)
    WHERE deleted_at IS NULL;

CREATE TABLE knowledge_document (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenant (id),
    knowledge_base_id BIGINT NOT NULL REFERENCES knowledge_base (id),
    title VARCHAR(255) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_type VARCHAR(64) NOT NULL,
    file_size BIGINT NOT NULL,
    object_key VARCHAR(512) NOT NULL,
    content_hash VARCHAR(128) NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'uploaded',
    chunk_count INTEGER NOT NULL DEFAULT 0,
    error_message TEXT NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_by BIGINT NOT NULL REFERENCES user_account (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ NULL,
    CONSTRAINT chk_knowledge_document_status CHECK (
        status IN ('uploaded', 'parsing', 'chunking', 'embedding', 'indexing', 'ready', 'failed', 'deleted')
    )
);

CREATE TABLE document_chunk (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenant (id),
    document_id BIGINT NOT NULL REFERENCES knowledge_document (id),
    parent_chunk_id BIGINT NULL REFERENCES document_chunk (id),
    chunk_no INTEGER NOT NULL,
    section_title VARCHAR(255) NULL,
    content TEXT NOT NULL,
    content_hash VARCHAR(128) NOT NULL,
    char_count INTEGER NOT NULL DEFAULT 0,
    token_count INTEGER NULL,
    search_vector TSVECTOR NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_chunk_document_no UNIQUE (document_id, chunk_no)
);

CREATE TABLE document_task (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenant (id),
    document_id BIGINT NOT NULL REFERENCES knowledge_document (id),
    task_type VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'pending',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    input_summary TEXT NULL,
    output_summary TEXT NULL,
    error_message TEXT NULL,
    started_at TIMESTAMPTZ NULL,
    finished_at TIMESTAMPTZ NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_document_task_type CHECK (
        task_type IN ('parse', 'chunk', 'embed', 'index_vector', 'index_keyword', 'reprocess')
    ),
    CONSTRAINT chk_document_task_status CHECK (status IN ('pending', 'running', 'success', 'failed', 'cancelled'))
);

CREATE TABLE document_embedding (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenant (id),
    document_id BIGINT NOT NULL REFERENCES knowledge_document (id),
    chunk_id BIGINT NOT NULL REFERENCES document_chunk (id),
    provider VARCHAR(64) NOT NULL,
    model VARCHAR(128) NOT NULL,
    dimension INTEGER NOT NULL,
    embedding VECTOR(1536) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_embedding_chunk_model UNIQUE (chunk_id, provider, model)
);

CREATE TABLE conversation_session (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenant (id),
    owner_id BIGINT NOT NULL REFERENCES user_account (id),
    title VARCHAR(255) NOT NULL DEFAULT '新会话',
    memory_strategy VARCHAR(32) NOT NULL DEFAULT 'NONE',
    knowledge_base_id BIGINT NULL REFERENCES knowledge_base (id),
    status VARCHAR(32) NOT NULL DEFAULT 'active',
    last_message_at TIMESTAMPTZ NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ NULL,
    CONSTRAINT chk_conversation_status CHECK (status IN ('active', 'archived', 'deleted'))
);

CREATE TABLE conversation_message (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenant (id),
    session_id BIGINT NOT NULL REFERENCES conversation_session (id),
    role VARCHAR(32) NOT NULL,
    content TEXT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'success',
    token_count INTEGER NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_message_role CHECK (role IN ('user', 'assistant', 'system'))
);

CREATE TABLE conversation_exchange (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenant (id),
    session_id BIGINT NOT NULL REFERENCES conversation_session (id),
    user_message_id BIGINT NOT NULL REFERENCES conversation_message (id),
    assistant_message_id BIGINT NULL REFERENCES conversation_message (id),
    execution_mode VARCHAR(64) NOT NULL DEFAULT 'RAG_QA',
    status VARCHAR(32) NOT NULL DEFAULT 'running',
    route_reason TEXT NULL,
    route_confidence NUMERIC(5, 4) NULL,
    started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    finished_at TIMESTAMPTZ NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_execution_mode CHECK (
        execution_mode IN ('CLARIFICATION', 'RAG_QA', 'REACT_AGENT_RESERVED')
    )
);

CREATE TABLE conversation_reference (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenant (id),
    exchange_id BIGINT NOT NULL REFERENCES conversation_exchange (id),
    document_id BIGINT NOT NULL REFERENCES knowledge_document (id),
    chunk_id BIGINT NOT NULL REFERENCES document_chunk (id),
    ordinal INTEGER NOT NULL,
    title VARCHAR(255) NOT NULL,
    quote TEXT NULL,
    score NUMERIC(8, 6) NULL,
    source_uri TEXT NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_reference_exchange_ordinal UNIQUE (exchange_id, ordinal)
);

CREATE TABLE conversation_memory_summary (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenant (id),
    session_id BIGINT NOT NULL REFERENCES conversation_session (id),
    summary_text TEXT NOT NULL,
    covered_message_id BIGINT NULL,
    version INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE exchange_trace_stage (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenant (id),
    exchange_id BIGINT NOT NULL REFERENCES conversation_exchange (id),
    stage_code VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'pending',
    input_summary TEXT NULL,
    output_summary TEXT NULL,
    error_message TEXT NULL,
    started_at TIMESTAMPTZ NULL,
    finished_at TIMESTAMPTZ NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_trace_stage_status CHECK (status IN ('pending', 'running', 'success', 'failed', 'skipped'))
);

CREATE TABLE model_call_trace (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenant (id),
    exchange_id BIGINT NOT NULL REFERENCES conversation_exchange (id),
    stage_id BIGINT NULL REFERENCES exchange_trace_stage (id),
    provider VARCHAR(64) NOT NULL,
    model VARCHAR(128) NOT NULL,
    call_type VARCHAR(64) NOT NULL DEFAULT 'chat',
    prompt_summary TEXT NULL,
    output_summary TEXT NULL,
    input_tokens INTEGER NULL,
    output_tokens INTEGER NULL,
    latency_ms INTEGER NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'success',
    error_message TEXT NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE retrieval_trace (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenant (id),
    exchange_id BIGINT NOT NULL REFERENCES conversation_exchange (id),
    stage_id BIGINT NULL REFERENCES exchange_trace_stage (id),
    sub_question_no INTEGER NOT NULL DEFAULT 1,
    channel VARCHAR(32) NOT NULL,
    query_text TEXT NOT NULL,
    filters JSONB NOT NULL DEFAULT '{}'::jsonb,
    result_count INTEGER NOT NULL DEFAULT 0,
    selected_count INTEGER NOT NULL DEFAULT 0,
    latency_ms INTEGER NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE retrieval_trace_item (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenant (id),
    retrieval_trace_id BIGINT NOT NULL REFERENCES retrieval_trace (id),
    document_id BIGINT NOT NULL REFERENCES knowledge_document (id),
    chunk_id BIGINT NOT NULL REFERENCES document_chunk (id),
    rank_no INTEGER NOT NULL,
    raw_score NUMERIC(12, 8) NULL,
    fused_score NUMERIC(12, 8) NULL,
    selected BOOLEAN NOT NULL DEFAULT FALSE,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE rerank_trace (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenant (id),
    exchange_id BIGINT NOT NULL REFERENCES conversation_exchange (id),
    provider VARCHAR(64) NULL,
    model VARCHAR(128) NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    skipped_reason TEXT NULL,
    input_count INTEGER NOT NULL DEFAULT 0,
    output_count INTEGER NOT NULL DEFAULT 0,
    latency_ms INTEGER NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'skipped',
    error_message TEXT NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE audit_log (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NULL REFERENCES tenant (id),
    actor_id BIGINT NULL REFERENCES user_account (id),
    action VARCHAR(128) NOT NULL,
    resource_type VARCHAR(64) NOT NULL,
    resource_id BIGINT NULL,
    ip_address INET NULL,
    user_agent TEXT NULL,
    detail JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
