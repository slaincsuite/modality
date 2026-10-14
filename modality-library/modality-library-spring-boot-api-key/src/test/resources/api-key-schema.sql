CREATE TABLE IF NOT EXISTS t_infra_api_key (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    api_key VARCHAR(64) NOT NULL UNIQUE,
    title VARCHAR(128),
    intro VARCHAR(256),
    login_id VARCHAR(64) NOT NULL,
    scopes VARCHAR(512),
    extra_data TEXT,
    status VARCHAR(16) NOT NULL DEFAULT 'enabled',
    expires_time BIGINT NOT NULL DEFAULT -1,
    create_time BIGINT NOT NULL,
    update_time BIGINT NOT NULL
);
