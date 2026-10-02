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

CREATE TABLE IF NOT EXISTS t_infra_dict_type (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    remark VARCHAR(512),
    enabled TINYINT NOT NULL DEFAULT 1,
    create_time BIGINT NOT NULL,
    update_time BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS t_infra_dict_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    type_code VARCHAR(64) NOT NULL,
    label VARCHAR(128) NOT NULL,
    item_value VARCHAR(128) NOT NULL,
    sort INT NOT NULL DEFAULT 0,
    enabled TINYINT NOT NULL DEFAULT 1,
    remark VARCHAR(512),
    create_time BIGINT NOT NULL,
    update_time BIGINT NOT NULL
);
