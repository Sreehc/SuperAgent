-- Knowledge document operational fields (Task 6.2)
ALTER TABLE knowledge_document
    ADD COLUMN owner_user_id BIGINT NULL REFERENCES user_account (id),
    ADD COLUMN expires_at TIMESTAMPTZ NULL,
    ADD COLUMN review_status VARCHAR(32) NOT NULL DEFAULT 'approved',
    ADD COLUMN reviewed_by BIGINT NULL REFERENCES user_account (id),
    ADD COLUMN reviewed_at TIMESTAMPTZ NULL;

ALTER TABLE knowledge_document
    ADD CONSTRAINT chk_knowledge_document_review_status CHECK (
        review_status IN ('draft', 'pending_review', 'approved', 'rejected')
    );

CREATE INDEX idx_knowledge_document_owner ON knowledge_document (tenant_id, owner_user_id);
CREATE INDEX idx_knowledge_document_expires ON knowledge_document (tenant_id, expires_at) WHERE expires_at IS NOT NULL;
CREATE INDEX idx_knowledge_document_review_status ON knowledge_document (tenant_id, review_status);
CREATE INDEX idx_knowledge_document_content_hash ON knowledge_document (tenant_id, content_hash) WHERE content_hash IS NOT NULL;

-- Tenant invitation (Task 7.1)
CREATE TABLE tenant_invitation (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenant (id),
    email VARCHAR(255) NOT NULL,
    role VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'pending',
    token_hash VARCHAR(255) NOT NULL,
    invited_by BIGINT NOT NULL REFERENCES user_account (id),
    expires_at TIMESTAMPTZ NOT NULL,
    accepted_at TIMESTAMPTZ NULL,
    accepted_by BIGINT NULL REFERENCES user_account (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_tenant_invitation_role CHECK (role IN ('OWNER', 'ADMIN', 'MEMBER')),
    CONSTRAINT chk_tenant_invitation_status CHECK (status IN ('pending', 'accepted', 'expired', 'revoked'))
);

CREATE UNIQUE INDEX uk_tenant_invitation_pending
    ON tenant_invitation (tenant_id, lower(email))
    WHERE status = 'pending';

CREATE INDEX idx_tenant_invitation_token ON tenant_invitation (token_hash);
CREATE INDEX idx_tenant_invitation_tenant_status ON tenant_invitation (tenant_id, status, created_at DESC);

CREATE TRIGGER trg_tenant_invitation_updated_at
    BEFORE UPDATE ON tenant_invitation
    FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

-- Tenant member status guard (Task 7.1)
-- tenant_member.status already exists from V2; ensure constraint and index.
ALTER TABLE tenant_member
    DROP CONSTRAINT IF EXISTS chk_tenant_member_status;

ALTER TABLE tenant_member
    ADD CONSTRAINT chk_tenant_member_status CHECK (status IN ('active', 'suspended'));

CREATE INDEX IF NOT EXISTS idx_tenant_member_tenant_status ON tenant_member (tenant_id, status);

-- Agent eval case-level result (Task 5.1)
CREATE TABLE agent_eval_run_case (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NULL REFERENCES tenant (id),
    run_id BIGINT NOT NULL REFERENCES agent_eval_run (id) ON DELETE CASCADE,
    case_id BIGINT NOT NULL REFERENCES agent_eval_case (id),
    status VARCHAR(32) NOT NULL,
    score NUMERIC(6, 4) NULL,
    actual_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    expected_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    diff_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    latency_ms INTEGER NULL,
    error_message TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_agent_eval_run_case_status CHECK (status IN ('passed', 'failed', 'error', 'skipped'))
);

CREATE INDEX idx_agent_eval_run_case_run_status ON agent_eval_run_case (run_id, status);
CREATE INDEX idx_agent_eval_run_case_case ON agent_eval_run_case (case_id, created_at DESC);
CREATE INDEX idx_agent_eval_run_case_tenant ON agent_eval_run_case (tenant_id, created_at DESC);
