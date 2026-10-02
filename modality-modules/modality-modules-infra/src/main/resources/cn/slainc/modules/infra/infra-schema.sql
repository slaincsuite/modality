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

CREATE TABLE IF NOT EXISTS t_infra_notice (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    level VARCHAR(16) NOT NULL DEFAULT 'INFO',
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    publish_time BIGINT,
    expire_time BIGINT,
    operator VARCHAR(64),
    create_time BIGINT NOT NULL,
    update_time BIGINT NOT NULL
);
