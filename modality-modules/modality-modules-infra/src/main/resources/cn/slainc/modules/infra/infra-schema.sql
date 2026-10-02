CREATE TABLE IF NOT EXISTS t_infra_config_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    config_key VARCHAR(255) NOT NULL,
    config_group VARCHAR(128),
    description VARCHAR(512),
    old_value TEXT,
    new_value TEXT,
    encrypted TINYINT NOT NULL DEFAULT 0,
    operation VARCHAR(16) NOT NULL,
    operator VARCHAR(64),
    create_time BIGINT NOT NULL
);
