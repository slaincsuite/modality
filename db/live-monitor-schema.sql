CREATE TABLE IF NOT EXISTS t_live_stream (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    name VARCHAR(128) NOT NULL,
    url VARCHAR(1000) NOT NULL,
    enabled TINYINT NOT NULL DEFAULT 1,
    status VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN',
    last_check_at BIGINT,
    last_latency_ms BIGINT,
    last_error VARCHAR(1000),
    create_time BIGINT NOT NULL,
    update_time BIGINT NOT NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    KEY idx_live_stream_tenant_status (tenant_id, status),
    KEY idx_live_stream_tenant_enabled (tenant_id, enabled)
);
