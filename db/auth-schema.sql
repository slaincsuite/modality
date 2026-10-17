CREATE TABLE IF NOT EXISTS t_auth_account_closure (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT,
    user_id BIGINT NOT NULL,
    login_type VARCHAR(32) NOT NULL,
    username VARCHAR(64),
    reason VARCHAR(256),
    status VARCHAR(16) NOT NULL,
    requested_at BIGINT NOT NULL,
    cool_down_until BIGINT NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    executed_at BIGINT,
    execute_error VARCHAR(256)
);

CREATE TABLE IF NOT EXISTS t_mfa_factor (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT,
    user_id BIGINT NOT NULL,
    login_type VARCHAR(32),
    username VARCHAR(64),
    factor_type VARCHAR(16) NOT NULL DEFAULT 'TOTP',
    secret_cipher TEXT,
    secret_key_id VARCHAR(64),
    activated TINYINT NOT NULL DEFAULT 0,
    recovery_codes TEXT,
    last_used_at BIGINT,
    fail_count INT NOT NULL DEFAULT 0,
    locked_until BIGINT,
    create_time BIGINT NOT NULL,
    update_time BIGINT NOT NULL,
    CONSTRAINT uk_mfa_factor_user_type UNIQUE (user_id, factor_type)
);

CREATE TABLE IF NOT EXISTS t_auth_security_policy (
    id BIGINT NOT NULL PRIMARY KEY,
    login_protect TINYINT NOT NULL DEFAULT 1,
    operation_protect TINYINT NOT NULL DEFAULT 1,
    remote_check TINYINT NOT NULL DEFAULT 1,
    idle_timeout_seconds BIGINT NOT NULL DEFAULT 1800,
    max_session_seconds BIGINT NOT NULL DEFAULT 7200,
    create_time BIGINT NOT NULL,
    update_time BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS t_auth_feishu_app_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    platform VARCHAR(32) NOT NULL,
    app_id VARCHAR(128),
    app_secret_cipher TEXT,
    verification_token_cipher TEXT,
    encrypt_key_cipher TEXT,
    callback_url VARCHAR(512),
    enabled TINYINT NOT NULL DEFAULT 0,
    create_time BIGINT NOT NULL,
    update_time BIGINT NOT NULL,
    CONSTRAINT uk_auth_feishu_app_platform UNIQUE (platform)
);

CREATE TABLE IF NOT EXISTS t_auth_third_sync_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    platform VARCHAR(32) NOT NULL,
    sync_type VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL,
    total_count INT NOT NULL DEFAULT 0,
    success_count INT NOT NULL DEFAULT 0,
    failure_count INT NOT NULL DEFAULT 0,
    message VARCHAR(512),
    operator VARCHAR(64),
    started_at BIGINT NOT NULL,
    finished_at BIGINT,
    create_time BIGINT NOT NULL,
    KEY idx_auth_third_sync_platform_time (platform, create_time)
);

CREATE TABLE IF NOT EXISTS t_auth_passkey (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT,
    user_id BIGINT NOT NULL,
    login_type VARCHAR(32),
    credential_id VARCHAR(256) NOT NULL,
    name VARCHAR(128) NOT NULL,
    public_key TEXT NOT NULL,
    created_at BIGINT NOT NULL,
    last_used_at BIGINT,
    deleted TINYINT NOT NULL DEFAULT 0,
    create_time BIGINT NOT NULL,
    update_time BIGINT NOT NULL,
    CONSTRAINT uk_auth_passkey_credential UNIQUE (credential_id),
    KEY idx_auth_passkey_user (user_id, deleted)
);
