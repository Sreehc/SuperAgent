CREATE INDEX idx_tenant_status ON tenant (status);
CREATE INDEX idx_user_status ON user_account (status);
CREATE INDEX idx_tenant_member_user ON tenant_member (user_id);
CREATE INDEX idx_tenant_member_role ON tenant_member (tenant_id, role);
CREATE INDEX idx_refresh_token_user ON refresh_token (user_id);
CREATE INDEX idx_refresh_token_expires ON refresh_token (expires_at);
CREATE INDEX idx_kb_tenant_status ON knowledge_base (tenant_id, status);
CREATE INDEX idx_doc_kb_status ON knowledge_document (tenant_id, knowledge_base_id, status);
CREATE INDEX idx_doc_content_hash ON knowledge_document (tenant_id, content_hash);
CREATE INDEX idx_doc_metadata ON knowledge_document USING GIN (metadata);
CREATE INDEX idx_chunk_document ON document_chunk (document_id, chunk_no);
CREATE INDEX idx_chunk_search_vector ON document_chunk USING GIN (search_vector);
CREATE INDEX idx_chunk_content_trgm ON document_chunk USING GIN (content gin_trgm_ops);
CREATE INDEX idx_chunk_metadata ON document_chunk USING GIN (metadata);
CREATE INDEX idx_doc_task_document ON document_task (document_id, created_at DESC);
CREATE INDEX idx_doc_task_status ON document_task (tenant_id, status, task_type);
CREATE INDEX idx_embedding_doc ON document_embedding (tenant_id, document_id);
CREATE INDEX idx_session_tenant_owner ON conversation_session (tenant_id, owner_id, status);
CREATE INDEX idx_session_last_message ON conversation_session (tenant_id, last_message_at DESC);
CREATE INDEX idx_message_session ON conversation_message (session_id, created_at);
CREATE INDEX idx_message_tenant_session ON conversation_message (tenant_id, session_id);
CREATE INDEX idx_exchange_session ON conversation_exchange (session_id, started_at DESC);
CREATE INDEX idx_exchange_tenant_status ON conversation_exchange (tenant_id, status, started_at DESC);
CREATE INDEX idx_reference_exchange ON conversation_reference (exchange_id);
CREATE INDEX idx_memory_summary_session ON conversation_memory_summary (session_id, version DESC);
CREATE INDEX idx_trace_stage_exchange ON exchange_trace_stage (exchange_id, started_at);
CREATE INDEX idx_trace_stage_tenant_status ON exchange_trace_stage (tenant_id, status, started_at DESC);
CREATE INDEX idx_model_trace_exchange ON model_call_trace (exchange_id);
CREATE INDEX idx_model_trace_tenant_model ON model_call_trace (tenant_id, provider, model, created_at DESC);
CREATE INDEX idx_retrieval_trace_exchange ON retrieval_trace (exchange_id, sub_question_no);
CREATE INDEX idx_retrieval_trace_channel ON retrieval_trace (tenant_id, channel, created_at DESC);
CREATE INDEX idx_retrieval_item_trace ON retrieval_trace_item (retrieval_trace_id, rank_no);
CREATE INDEX idx_retrieval_item_chunk ON retrieval_trace_item (chunk_id);
CREATE INDEX idx_rerank_trace_exchange ON rerank_trace (exchange_id);
CREATE INDEX idx_rerank_trace_tenant_status ON rerank_trace (tenant_id, status, created_at DESC);
CREATE INDEX idx_audit_tenant_created ON audit_log (tenant_id, created_at DESC);
CREATE INDEX idx_audit_actor ON audit_log (actor_id, created_at DESC);
CREATE INDEX idx_audit_resource ON audit_log (resource_type, resource_id);

CREATE TRIGGER trg_tenant_updated_at
    BEFORE UPDATE ON tenant
    FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_user_account_updated_at
    BEFORE UPDATE ON user_account
    FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_tenant_member_updated_at
    BEFORE UPDATE ON tenant_member
    FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_refresh_token_updated_at
    BEFORE UPDATE ON refresh_token
    FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_knowledge_base_updated_at
    BEFORE UPDATE ON knowledge_base
    FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_knowledge_document_updated_at
    BEFORE UPDATE ON knowledge_document
    FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_document_chunk_updated_at
    BEFORE UPDATE ON document_chunk
    FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_document_task_updated_at
    BEFORE UPDATE ON document_task
    FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_document_embedding_updated_at
    BEFORE UPDATE ON document_embedding
    FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_conversation_session_updated_at
    BEFORE UPDATE ON conversation_session
    FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_conversation_message_updated_at
    BEFORE UPDATE ON conversation_message
    FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_conversation_exchange_updated_at
    BEFORE UPDATE ON conversation_exchange
    FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_conversation_reference_updated_at
    BEFORE UPDATE ON conversation_reference
    FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_conversation_memory_summary_updated_at
    BEFORE UPDATE ON conversation_memory_summary
    FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_exchange_trace_stage_updated_at
    BEFORE UPDATE ON exchange_trace_stage
    FOR EACH ROW
EXECUTE FUNCTION set_updated_at();
