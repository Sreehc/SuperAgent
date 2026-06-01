CREATE TABLE tenant_runtime_setting (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenant (id),
    setting_key VARCHAR(64) NOT NULL,
    config_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_tenant_runtime_setting UNIQUE (tenant_id, setting_key)
);

CREATE INDEX idx_runtime_setting_tenant_key ON tenant_runtime_setting (tenant_id, setting_key);

CREATE TRIGGER trg_tenant_runtime_setting_updated_at
    BEFORE UPDATE ON tenant_runtime_setting
    FOR EACH ROW
EXECUTE FUNCTION set_updated_at();
