CREATE TABLE IF NOT EXISTS t_tenant (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    tenant_code VARCHAR(64) NOT NULL,
    tenant_name VARCHAR(64) NOT NULL,
    contact_name VARCHAR(64),
    contact_phone VARCHAR(32),
    contact_email VARCHAR(128),
    expire_at DATETIME,
    status VARCHAR(32) NOT NULL DEFAULT 'TRIAL',
    isolation_strategy VARCHAR(32) NOT NULL DEFAULT 'ROW',
    schema_name VARCHAR(64),
    datasource_name VARCHAR(64),
    package_id BIGINT,
    remark VARCHAR(255),
    created_at DATETIME,
    updated_at DATETIME,
    created_by VARCHAR(64),
    updated_by VARCHAR(64),
    deleted TINYINT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS t_tenant_plan (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    plan_code VARCHAR(64) NOT NULL,
    plan_name VARCHAR(64) NOT NULL,
    max_users INT,
    storage_quota_mb BIGINT,
    max_file_size_mb BIGINT,
    max_export_rows INT,
    log_retention_days INT,
    features VARCHAR(512),
    rate_limit_per_minute INT,
    max_concurrent_exports INT,
    search_max_page_size INT,
    data_scope VARCHAR(32),
    sort INT NOT NULL DEFAULT 0,
    enabled TINYINT NOT NULL DEFAULT 1,
    remark VARCHAR(255),
    version BIGINT NOT NULL DEFAULT 0,
    create_time BIGINT NOT NULL,
    update_time BIGINT NOT NULL,
    CONSTRAINT uk_tenant_plan_code UNIQUE (plan_code)
);

CREATE TABLE IF NOT EXISTS t_tenant_plan_menu (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    plan_id BIGINT NOT NULL,
    menu_id BIGINT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    create_time BIGINT NOT NULL,
    CONSTRAINT uk_tenant_plan_menu UNIQUE (plan_id, menu_id)
);
