CREATE TABLE IF NOT EXISTS t_open_app (
    app_id VARCHAR(64) NOT NULL PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(512),
    logo VARCHAR(512),
    category VARCHAR(64),
    contact VARCHAR(128),
    tags TEXT,
    app_type VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    owner_id VARCHAR(64) NOT NULL,
    redirect_uris TEXT,
    scopes TEXT,
    deleted TINYINT NOT NULL DEFAULT 0,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS t_open_scope (
    name VARCHAR(64) NOT NULL PRIMARY KEY,
    title VARCHAR(128) NOT NULL,
    description VARCHAR(512),
    group_name VARCHAR(64) NOT NULL DEFAULT '默认',
    risk_level VARCHAR(8) NOT NULL DEFAULT 'LOW',
    requires_audit TINYINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS t_open_consent (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    app_id VARCHAR(64) NOT NULL,
    scopes TEXT NOT NULL,
    granted_at BIGINT NOT NULL,
    revoked_at BIGINT,
    source VARCHAR(16) NOT NULL DEFAULT 'PORTAL'
);

CREATE TABLE IF NOT EXISTS t_open_credential (
    app_id VARCHAR(64) NOT NULL PRIMARY KEY,
    client_id VARCHAR(128),
    client_secret VARCHAR(256),
    type VARCHAR(16) NOT NULL DEFAULT 'GENERATED',
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
    previous_secret_expire_at BIGINT,
    issued_at BIGINT NOT NULL,
    rotated_at BIGINT
);

CREATE TABLE IF NOT EXISTS t_open_app_log (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    app_id VARCHAR(64) NOT NULL,
    action VARCHAR(32) NOT NULL,
    operator VARCHAR(64),
    reason VARCHAR(256),
    detail TEXT,
    create_time BIGINT NOT NULL
);
