CREATE TABLE IF NOT EXISTS t_change_audit_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    entity_type VARCHAR(64) NOT NULL,
    entity_id VARCHAR(64),
    entity_name VARCHAR(128),
    operation VARCHAR(32),
    changes TEXT,
    changed_fields INT,
    operator VARCHAR(64),
    operator_id BIGINT,
    request_uri VARCHAR(256),
    trace_id VARCHAR(64),
    change_source VARCHAR(32),
    change_time BIGINT NOT NULL,
    tenant_id BIGINT,
    create_time BIGINT NOT NULL,
    KEY idx_change_audit_entity (entity_type, entity_id, change_time),
    KEY idx_change_audit_tenant (tenant_id, change_time)
);
