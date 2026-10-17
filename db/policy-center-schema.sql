CREATE TABLE IF NOT EXISTS t_policy_meta (
    policy_id VARCHAR(64) NOT NULL PRIMARY KEY,
    group_name VARCHAR(64) NOT NULL DEFAULT '默认',
    system_flag TINYINT NOT NULL DEFAULT 0,
    create_time BIGINT NOT NULL,
    update_time BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS t_policy_test_case (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    policy_id VARCHAR(64),
    name VARCHAR(128) NOT NULL,
    description VARCHAR(256),
    request_json TEXT NOT NULL,
    expected VARCHAR(16) NOT NULL,
    enabled TINYINT NOT NULL DEFAULT 1,
    create_time BIGINT NOT NULL,
    update_time BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS t_policy_template (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(256),
    document TEXT NOT NULL,
    creator VARCHAR(64),
    create_time BIGINT NOT NULL,
    update_time BIGINT NOT NULL,
    CONSTRAINT uk_policy_template_name UNIQUE (name)
);

CREATE TABLE IF NOT EXISTS t_policy_boundary (
    policy_id VARCHAR(64) NOT NULL PRIMARY KEY,
    document TEXT NOT NULL,
    operator VARCHAR(64),
    create_time BIGINT NOT NULL,
    update_time BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS t_policy_binding_source (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    binding_id BIGINT NOT NULL,
    policy_id VARCHAR(64) NOT NULL,
    source_policy_id VARCHAR(64) NOT NULL,
    subject_type VARCHAR(16) NOT NULL,
    subject_id VARCHAR(64) NOT NULL,
    create_time BIGINT NOT NULL,
    CONSTRAINT uk_policy_binding_source_policy UNIQUE (policy_id),
    CONSTRAINT uk_policy_binding_source_subject UNIQUE (tenant_id, source_policy_id, subject_type, subject_id),
    KEY idx_policy_binding_source_source (tenant_id, source_policy_id)
);

-- 策略引擎存储表（与 JdbcPolicyStore 懒建语句一致；表名可通过 modality.policy-center.* 自定义时以懒建为准）
CREATE TABLE IF NOT EXISTS t_policy (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    subject_type VARCHAR(16) NOT NULL,
    subject_id VARCHAR(64),
    login_type VARCHAR(16),
    status VARCHAR(16) NOT NULL DEFAULT 'enabled',
    version INT NOT NULL DEFAULT 1,
    document TEXT NOT NULL,
    description VARCHAR(256),
    creator VARCHAR(64),
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS t_policy_version (
    policy_id VARCHAR(64) NOT NULL,
    version INT NOT NULL,
    document TEXT NOT NULL,
    operator VARCHAR(64),
    created_at BIGINT NOT NULL,
    PRIMARY KEY (policy_id, version)
);

CREATE TABLE IF NOT EXISTS t_policy_audit (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    policy_id VARCHAR(64) NOT NULL,
    operation VARCHAR(16) NOT NULL,
    operator VARCHAR(64),
    before_content TEXT,
    after_content TEXT,
    created_at BIGINT NOT NULL
);
