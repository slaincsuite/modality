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

CREATE TABLE IF NOT EXISTS t_infra_notify_template (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    title_template VARCHAR(200) NOT NULL,
    content_template TEXT NOT NULL,
    channel VARCHAR(16) NOT NULL DEFAULT 'INAPP',
    enabled TINYINT NOT NULL DEFAULT 1,
    create_time BIGINT NOT NULL,
    update_time BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS t_infra_notify_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    receiver_id BIGINT NOT NULL,
    login_type VARCHAR(32) NOT NULL DEFAULT 'TENANT_USER',
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    level VARCHAR(16) NOT NULL DEFAULT 'INFO',
    template_code VARCHAR(64),
    read_flag TINYINT NOT NULL DEFAULT 0,
    read_time BIGINT,
    create_time BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS t_infra_notify_webhook (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    platform VARCHAR(16) NOT NULL DEFAULT 'CUSTOM',
    url VARCHAR(512) NOT NULL,
    secret VARCHAR(256),
    enabled TINYINT NOT NULL DEFAULT 1,
    create_time BIGINT NOT NULL,
    update_time BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS t_infra_notify_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    channel VARCHAR(16) NOT NULL,
    target VARCHAR(512) NOT NULL,
    template_code VARCHAR(64),
    title VARCHAR(200),
    content TEXT,
    status VARCHAR(16) NOT NULL,
    error VARCHAR(1000),
    retry_count INT NOT NULL DEFAULT 0,
    create_time BIGINT NOT NULL,
    sent_time BIGINT
);

CREATE TABLE IF NOT EXISTS t_infra_file_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    file_key VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    folder VARCHAR(255) NOT NULL DEFAULT '',
    content_type VARCHAR(128),
    ext VARCHAR(32),
    size BIGINT NOT NULL DEFAULT 0,
    backend VARCHAR(32),
    md5 VARCHAR(64),
    uploader_id BIGINT,
    uploader_name VARCHAR(64),
    tenant_id BIGINT,
    status VARCHAR(16) NOT NULL DEFAULT 'NORMAL',
    create_time BIGINT NOT NULL,
    update_time BIGINT NOT NULL
);


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
