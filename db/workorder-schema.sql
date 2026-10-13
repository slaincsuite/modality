CREATE TABLE IF NOT EXISTS t_work_order (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    number VARCHAR(64),
    type_code VARCHAR(32) NOT NULL DEFAULT 'generic',
    title VARCHAR(200) NOT NULL,
    content VARCHAR(4000) NOT NULL,
    priority VARCHAR(16) NOT NULL DEFAULT 'MEDIUM',
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    source VARCHAR(16) NOT NULL DEFAULT 'MANUAL',
    source_system VARCHAR(64),
    source_biz_id VARCHAR(64),
    parent_order_id BIGINT,
    collaborator_ids VARCHAR(500),
    submitter_id BIGINT NOT NULL,
    assignee_id BIGINT,
    custom_fields TEXT,
    version BIGINT NOT NULL DEFAULT 0,
    create_time BIGINT NOT NULL,
    update_time BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS t_work_order_activity (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    action VARCHAR(32) NOT NULL,
    operator_id BIGINT,
    target_user_id BIGINT,
    reason VARCHAR(500),
    create_time BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS t_work_order_reply (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    work_order_id BIGINT NOT NULL,
    replier_id BIGINT NOT NULL,
    content VARCHAR(2000) NOT NULL,
    create_time BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS t_work_order_attachment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    file_name VARCHAR(256) NOT NULL,
    object_key VARCHAR(500) NOT NULL,
    file_size BIGINT NOT NULL DEFAULT 0,
    content_type VARCHAR(128),
    uploader_id BIGINT NOT NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    create_time BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS t_work_order_comment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    author_id BIGINT NOT NULL,
    content VARCHAR(4000) NOT NULL,
    visibility VARCHAR(16) NOT NULL DEFAULT 'INTERNAL',
    parent_id BIGINT,
    quoted_comment_id BIGINT,
    like_count BIGINT NOT NULL DEFAULT 0,
    deleted TINYINT NOT NULL DEFAULT 0,
    create_time BIGINT NOT NULL,
    update_time BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS t_work_order_subscription (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    order_id BIGINT,
    type_code VARCHAR(32),
    subscriber_id BIGINT NOT NULL,
    create_time BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS t_work_order_notification (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    order_id BIGINT,
    number VARCHAR(64),
    recipient_id BIGINT NOT NULL,
    event_code VARCHAR(64) NOT NULL,
    title VARCHAR(256),
    content VARCHAR(1000),
    channel VARCHAR(32) NOT NULL,
    is_read TINYINT NOT NULL DEFAULT 0,
    create_time BIGINT NOT NULL
);
