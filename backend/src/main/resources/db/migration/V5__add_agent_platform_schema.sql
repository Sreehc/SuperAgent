ALTER TABLE conversation_exchange
    DROP CONSTRAINT IF EXISTS chk_execution_mode;

ALTER TABLE conversation_exchange
    ADD CONSTRAINT chk_execution_mode CHECK (
        execution_mode IN ('CLARIFICATION', 'RAG_QA', 'REACT_AGENT')
    );

CREATE TABLE knowledge_domain (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenant (id),
    code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    description TEXT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'active',
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ NULL,
    CONSTRAINT uk_knowledge_domain_tenant_code UNIQUE (tenant_id, code)
);

CREATE TABLE chunking_profile (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenant (id),
    code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    strategy VARCHAR(64) NOT NULL,
    config_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(32) NOT NULL DEFAULT 'active',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ NULL,
    CONSTRAINT uk_chunking_profile_tenant_code UNIQUE (tenant_id, code)
);

ALTER TABLE knowledge_document
    ADD COLUMN knowledge_domain_id BIGINT NULL REFERENCES knowledge_domain (id),
    ADD COLUMN chunking_profile_id BIGINT NULL REFERENCES chunking_profile (id),
    ADD COLUMN graph_sync_status VARCHAR(32) NOT NULL DEFAULT 'pending',
    ADD COLUMN graph_error_message TEXT NULL,
    ADD COLUMN active_version_no INTEGER NOT NULL DEFAULT 1;

CREATE TABLE knowledge_document_version (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenant (id),
    document_id BIGINT NOT NULL REFERENCES knowledge_document (id),
    version_no INTEGER NOT NULL,
    chunking_profile_id BIGINT NULL REFERENCES chunking_profile (id),
    status VARCHAR(32) NOT NULL DEFAULT 'ready',
    chunk_count INTEGER NOT NULL DEFAULT 0,
    graph_sync_status VARCHAR(32) NOT NULL DEFAULT 'pending',
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_by BIGINT NULL REFERENCES user_account (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_document_version UNIQUE (document_id, version_no)
);

CREATE TABLE plugin_registry (
    id BIGSERIAL PRIMARY KEY,
    plugin_key VARCHAR(128) NOT NULL,
    version VARCHAR(64) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    manifest_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    status VARCHAR(32) NOT NULL DEFAULT 'active',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_plugin_registry_key UNIQUE (plugin_key)
);

CREATE TABLE plugin_installation (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenant (id),
    plugin_id BIGINT NOT NULL REFERENCES plugin_registry (id),
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    config_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_by BIGINT NULL REFERENCES user_account (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_plugin_installation UNIQUE (tenant_id, plugin_id)
);

CREATE TABLE tenant_tool_binding (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenant (id),
    tool_id VARCHAR(128) NOT NULL,
    plugin_id BIGINT NULL REFERENCES plugin_registry (id),
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    risk_level VARCHAR(32) NOT NULL DEFAULT 'standard',
    config_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_tenant_tool_binding UNIQUE (tenant_id, tool_id)
);

CREATE TABLE tenant_tool_secret (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenant (id),
    tool_id VARCHAR(128) NOT NULL,
    secret_key VARCHAR(128) NOT NULL,
    secret_value TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_tenant_tool_secret UNIQUE (tenant_id, tool_id, secret_key)
);

CREATE TABLE agent_run (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenant (id),
    session_id BIGINT NOT NULL REFERENCES conversation_session (id),
    exchange_id BIGINT NULL REFERENCES conversation_exchange (id),
    trigger_message_id BIGINT NULL REFERENCES conversation_message (id),
    status VARCHAR(32) NOT NULL DEFAULT 'pending',
    memory_strategy VARCHAR(32) NOT NULL DEFAULT 'SUMMARY_PLUS_WINDOW',
    question TEXT NOT NULL,
    route_reason TEXT NULL,
    model_step_count INTEGER NOT NULL DEFAULT 0,
    tool_call_count INTEGER NOT NULL DEFAULT 0,
    latest_checkpoint_no INTEGER NOT NULL DEFAULT 0,
    error_message TEXT NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    started_at TIMESTAMPTZ NULL,
    finished_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE agent_run_step (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenant (id),
    agent_run_id BIGINT NOT NULL REFERENCES agent_run (id),
    step_no INTEGER NOT NULL,
    phase VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'pending',
    decision_summary TEXT NULL,
    observation_summary TEXT NULL,
    selected_tool_id VARCHAR(128) NULL,
    selected_tool_reason TEXT NULL,
    error_message TEXT NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    started_at TIMESTAMPTZ NULL,
    finished_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_agent_run_step UNIQUE (agent_run_id, step_no)
);

CREATE TABLE agent_checkpoint (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenant (id),
    agent_run_id BIGINT NOT NULL REFERENCES agent_run (id),
    checkpoint_no INTEGER NOT NULL,
    step_id BIGINT NULL REFERENCES agent_run_step (id),
    checkpoint_type VARCHAR(64) NOT NULL,
    stable BOOLEAN NOT NULL DEFAULT TRUE,
    payload_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_agent_checkpoint UNIQUE (agent_run_id, checkpoint_no)
);

CREATE TABLE tool_call_trace (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenant (id),
    agent_run_id BIGINT NOT NULL REFERENCES agent_run (id),
    step_id BIGINT NULL REFERENCES agent_run_step (id),
    tool_id VARCHAR(128) NOT NULL,
    plugin_id BIGINT NULL REFERENCES plugin_registry (id),
    request_summary TEXT NULL,
    response_summary TEXT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'pending',
    latency_ms INTEGER NULL,
    error_message TEXT NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE tool_call_artifact (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenant (id),
    tool_call_trace_id BIGINT NOT NULL REFERENCES tool_call_trace (id),
    artifact_type VARCHAR(64) NOT NULL,
    name VARCHAR(255) NOT NULL,
    content TEXT NULL,
    uri TEXT NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE agent_eval_suite (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NULL REFERENCES tenant (id),
    suite_key VARCHAR(128) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_agent_eval_suite UNIQUE (suite_key)
);

CREATE TABLE agent_eval_case (
    id BIGSERIAL PRIMARY KEY,
    suite_id BIGINT NOT NULL REFERENCES agent_eval_suite (id),
    case_key VARCHAR(128) NOT NULL,
    input_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    expected_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_agent_eval_case UNIQUE (suite_id, case_key)
);

CREATE TABLE agent_eval_run (
    id BIGSERIAL PRIMARY KEY,
    suite_id BIGINT NOT NULL REFERENCES agent_eval_suite (id),
    status VARCHAR(32) NOT NULL DEFAULT 'pending',
    passed_count INTEGER NOT NULL DEFAULT 0,
    failed_count INTEGER NOT NULL DEFAULT 0,
    report_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    finished_at TIMESTAMPTZ NULL
);

CREATE INDEX idx_knowledge_domain_tenant_status ON knowledge_domain (tenant_id, status);
CREATE INDEX idx_chunking_profile_tenant_status ON chunking_profile (tenant_id, status);
CREATE INDEX idx_document_version_document ON knowledge_document_version (document_id, version_no DESC);
CREATE INDEX idx_plugin_installation_tenant_enabled ON plugin_installation (tenant_id, enabled);
CREATE INDEX idx_tenant_tool_binding_tenant_enabled ON tenant_tool_binding (tenant_id, enabled);
CREATE INDEX idx_agent_run_tenant_status ON agent_run (tenant_id, status, created_at DESC);
CREATE INDEX idx_agent_run_session ON agent_run (session_id, created_at DESC);
CREATE INDEX idx_agent_run_step_run ON agent_run_step (agent_run_id, step_no);
CREATE INDEX idx_agent_checkpoint_run ON agent_checkpoint (agent_run_id, checkpoint_no DESC);
CREATE INDEX idx_tool_call_trace_run ON tool_call_trace (agent_run_id, created_at ASC);
CREATE INDEX idx_tool_call_trace_tool ON tool_call_trace (tenant_id, tool_id, created_at DESC);

CREATE TRIGGER trg_knowledge_domain_updated_at
    BEFORE UPDATE ON knowledge_domain
    FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_chunking_profile_updated_at
    BEFORE UPDATE ON chunking_profile
    FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_knowledge_document_version_updated_at
    BEFORE UPDATE ON knowledge_document_version
    FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_plugin_registry_updated_at
    BEFORE UPDATE ON plugin_registry
    FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_plugin_installation_updated_at
    BEFORE UPDATE ON plugin_installation
    FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_tenant_tool_binding_updated_at
    BEFORE UPDATE ON tenant_tool_binding
    FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_tenant_tool_secret_updated_at
    BEFORE UPDATE ON tenant_tool_secret
    FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_agent_run_updated_at
    BEFORE UPDATE ON agent_run
    FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_agent_run_step_updated_at
    BEFORE UPDATE ON agent_run_step
    FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_agent_eval_suite_updated_at
    BEFORE UPDATE ON agent_eval_suite
    FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_agent_eval_case_updated_at
    BEFORE UPDATE ON agent_eval_case
    FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_agent_eval_run_updated_at
    BEFORE UPDATE ON agent_eval_run
    FOR EACH ROW
EXECUTE FUNCTION set_updated_at();
