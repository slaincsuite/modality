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
    executed_at BIGINT,
    execute_error VARCHAR(256)
);
