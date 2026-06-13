CREATE TABLE conversation_feedback (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenant (id),
    session_id BIGINT NOT NULL REFERENCES conversation_session (id),
    exchange_id BIGINT NULL REFERENCES conversation_exchange (id),
    message_id BIGINT NOT NULL REFERENCES conversation_message (id),
    actor_user_id BIGINT NOT NULL REFERENCES user_account (id),
    rating VARCHAR(32) NOT NULL,
    comment TEXT NULL,
    correction TEXT NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_conversation_feedback_actor_message UNIQUE (tenant_id, message_id, actor_user_id),
    CONSTRAINT chk_conversation_feedback_rating CHECK (rating IN ('up', 'down', 'correction'))
);

CREATE INDEX idx_conversation_feedback_tenant_created ON conversation_feedback (tenant_id, created_at DESC);
CREATE INDEX idx_conversation_feedback_message ON conversation_feedback (tenant_id, message_id);
CREATE INDEX idx_conversation_feedback_rating ON conversation_feedback (tenant_id, rating, created_at DESC);

CREATE TRIGGER trg_conversation_feedback_updated_at
    BEFORE UPDATE ON conversation_feedback
    FOR EACH ROW
EXECUTE FUNCTION set_updated_at();
