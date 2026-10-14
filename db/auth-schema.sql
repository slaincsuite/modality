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
