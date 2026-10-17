CREATE TABLE IF NOT EXISTS t_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    username VARCHAR(64) NOT NULL,
    nickname VARCHAR(64),
    password_hash VARCHAR(100) NOT NULL,
    email VARCHAR(128),
    phone VARCHAR(32),
    avatar_url VARCHAR(256),
    dept_id BIGINT,
    login_type VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'enabled',
    must_change_password TINYINT NOT NULL DEFAULT 0,
    password_changed_at BIGINT,
    allowed_ips VARCHAR(512),
    allowed_time_range VARCHAR(64),
    allowed_devices VARCHAR(256),
    tags VARCHAR(512),
    ext_json TEXT,
    merged_to BIGINT,
    remark VARCHAR(256),
    console_access TINYINT NOT NULL DEFAULT 1,
    deleted TINYINT NOT NULL DEFAULT 0,
    create_time BIGINT NOT NULL,
    update_time BIGINT NOT NULL,
    CONSTRAINT uk_user_login UNIQUE (login_type, username)
);

CREATE TABLE IF NOT EXISTS t_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(64) NOT NULL,
    parent_id BIGINT NOT NULL DEFAULT 0,
    data_scope VARCHAR(32) NOT NULL DEFAULT 'SELF',
    sort INT NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'enabled',
    tags VARCHAR(256),
    remark VARCHAR(256),
    create_time BIGINT NOT NULL,
    update_time BIGINT NOT NULL,
    CONSTRAINT uk_role_tenant_code UNIQUE (tenant_id, code)
);

CREATE TABLE IF NOT EXISTS t_permission (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(128) NOT NULL,
    name VARCHAR(64) NOT NULL,
    type VARCHAR(16) NOT NULL DEFAULT 'MENU',
    parent_id BIGINT NOT NULL DEFAULT 0,
    sort INT NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'enabled',
    remark VARCHAR(256),
    create_time BIGINT NOT NULL,
    update_time BIGINT NOT NULL,
    CONSTRAINT uk_permission_code UNIQUE (code)
);

CREATE TABLE IF NOT EXISTS t_menu (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    parent_id BIGINT NOT NULL DEFAULT 0,
    name VARCHAR(64) NOT NULL,
    path VARCHAR(256),
    component VARCHAR(256),
    icon VARCHAR(64),
    type VARCHAR(16) NOT NULL DEFAULT 'MENU',
    permission_code VARCHAR(128),
    visible TINYINT NOT NULL DEFAULT 1,
    sort INT NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'enabled',
    create_time BIGINT NOT NULL,
    update_time BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS t_dept (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    parent_id BIGINT NOT NULL DEFAULT 0,
    name VARCHAR(64) NOT NULL,
    leader_user_id BIGINT,
    sort INT NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'enabled',
    create_time BIGINT NOT NULL,
    update_time BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS t_user_group (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    name VARCHAR(64) NOT NULL,
    remark VARCHAR(256),
    create_time BIGINT NOT NULL,
    update_time BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS t_user_group_member (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    group_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    create_time BIGINT NOT NULL,
    CONSTRAINT uk_group_member UNIQUE (group_id, user_id)
);

CREATE TABLE IF NOT EXISTS t_user_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    create_time BIGINT NOT NULL,
    CONSTRAINT uk_user_role UNIQUE (user_id, role_id),
    KEY idx_user_role_role (role_id)
);

CREATE TABLE IF NOT EXISTS t_role_permission (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    create_time BIGINT NOT NULL,
    CONSTRAINT uk_role_permission UNIQUE (role_id, permission_id)
);

CREATE TABLE IF NOT EXISTS t_role_data_scope (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    role_id BIGINT NOT NULL,
    dept_id BIGINT NOT NULL,
    CONSTRAINT uk_role_scope_dept UNIQUE (role_id, dept_id)
);

CREATE TABLE IF NOT EXISTS t_role_group (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    role_id BIGINT NOT NULL,
    group_id BIGINT NOT NULL,
    create_time BIGINT NOT NULL,
    CONSTRAINT uk_role_group UNIQUE (role_id, group_id),
    KEY idx_role_group_group (group_id)
);

CREATE TABLE IF NOT EXISTS t_system_registry (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(64) NOT NULL,
    module_code VARCHAR(32) NOT NULL,
    visibility VARCHAR(16) NOT NULL DEFAULT 'PUBLIC',
    group_name VARCHAR(64),
    icon VARCHAR(64),
    url VARCHAR(256),
    sort INT NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'enabled',
    create_time BIGINT NOT NULL,
    update_time BIGINT NOT NULL,
    CONSTRAINT uk_system_code UNIQUE (code)
);

CREATE TABLE IF NOT EXISTS t_access_control (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    system_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    granted_by VARCHAR(64),
    granted_at BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'granted',
    CONSTRAINT uk_access_system_user UNIQUE (system_id, user_id)
);

CREATE TABLE IF NOT EXISTS t_policy_binding (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    policy_id VARCHAR(64) NOT NULL,
    subject_type VARCHAR(16) NOT NULL,
    subject_id VARCHAR(64) NOT NULL,
    operator VARCHAR(64),
    create_time BIGINT NOT NULL,
    CONSTRAINT uk_policy_binding UNIQUE (policy_id, subject_type, subject_id),
    KEY idx_policy_binding_subject (subject_type, subject_id)
);

CREATE TABLE IF NOT EXISTS t_user_operation_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    audit_id VARCHAR(64),
    user_id BIGINT,
    username VARCHAR(64),
    module_name VARCHAR(32),
    operation VARCHAR(32),
    description VARCHAR(256),
    success TINYINT NOT NULL DEFAULT 1,
    client_ip VARCHAR(64),
    user_agent VARCHAR(256),
    request_path VARCHAR(256),
    http_method VARCHAR(16),
    cost_millis BIGINT NOT NULL DEFAULT 0,
    detail TEXT,
    create_time BIGINT NOT NULL,
    KEY idx_user_op_log_tenant_time (tenant_id, create_time),
    KEY idx_user_op_log_user (user_id, create_time)
);

CREATE TABLE IF NOT EXISTS t_user_login_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    user_id BIGINT,
    username VARCHAR(64) NOT NULL,
    login_type VARCHAR(32) NOT NULL,
    client_ip VARCHAR(64),
    user_agent VARCHAR(256),
    device VARCHAR(32),
    succeeded TINYINT NOT NULL DEFAULT 1,
    failure_reason VARCHAR(256),
    create_time BIGINT NOT NULL,
    KEY idx_user_login_tenant_time (tenant_id, create_time),
    KEY idx_user_login_user (user_id, create_time)
);

CREATE TABLE IF NOT EXISTS t_favorite (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    user_id BIGINT NOT NULL,
    target_type VARCHAR(32) NOT NULL,
    target_key VARCHAR(128) NOT NULL,
    name VARCHAR(128) NOT NULL,
    path VARCHAR(256),
    icon VARCHAR(64),
    sort INT NOT NULL DEFAULT 0,
    create_time BIGINT NOT NULL,
    update_time BIGINT NOT NULL,
    CONSTRAINT uk_favorite_user_target UNIQUE (user_id, target_type, target_key)
);

CREATE TABLE IF NOT EXISTS t_recent_visit (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    user_id BIGINT NOT NULL,
    target_type VARCHAR(32) NOT NULL,
    target_key VARCHAR(128) NOT NULL,
    name VARCHAR(128) NOT NULL,
    path VARCHAR(256),
    visited_at BIGINT NOT NULL,
    create_time BIGINT NOT NULL,
    update_time BIGINT NOT NULL,
    CONSTRAINT uk_recent_visit_user_target UNIQUE (user_id, target_type, target_key),
    KEY idx_recent_visit_user_time (user_id, visited_at)
);

CREATE TABLE IF NOT EXISTS t_user_preferences (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    user_id BIGINT NOT NULL,
    scene VARCHAR(64) NOT NULL,
    settings_json TEXT NOT NULL,
    create_time BIGINT NOT NULL,
    update_time BIGINT NOT NULL,
    CONSTRAINT uk_user_preferences UNIQUE (user_id, scene)
);

CREATE TABLE IF NOT EXISTS t_user_permission_boundary (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    user_id BIGINT NOT NULL,
    effect VARCHAR(16) NOT NULL,
    permission_code VARCHAR(128) NOT NULL,
    create_time BIGINT NOT NULL,
    update_time BIGINT NOT NULL,
    CONSTRAINT uk_user_permission_boundary UNIQUE (user_id, effect, permission_code),
    KEY idx_user_permission_boundary_user (user_id)
);

CREATE TABLE IF NOT EXISTS t_user_settings (
    id BIGINT PRIMARY KEY,
    password_min_length INT NOT NULL DEFAULT 8,
    password_require_classes INT NOT NULL DEFAULT 3,
    password_expire_days INT NOT NULL DEFAULT 90,
    password_allow_self_reset TINYINT NOT NULL DEFAULT 0,
    default_login_type VARCHAR(32) NOT NULL DEFAULT 'TENANT_USER',
    default_role_id BIGINT,
    send_initial_password_email TINYINT NOT NULL DEFAULT 1,
    force_change_password TINYINT NOT NULL DEFAULT 1,
    login_failure_threshold INT NOT NULL DEFAULT 5,
    login_lock_minutes INT NOT NULL DEFAULT 30,
    idle_timeout_minutes INT NOT NULL DEFAULT 30,
    max_session_minutes INT NOT NULL DEFAULT 120,
    restrict_idle_users TINYINT NOT NULL DEFAULT 0,
    idle_user_days INT NOT NULL DEFAULT 90,
    create_time BIGINT NOT NULL,
    update_time BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS t_user_settings_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    setting_key VARCHAR(64) NOT NULL,
    before_json TEXT,
    after_json TEXT,
    operator VARCHAR(64),
    create_time BIGINT NOT NULL,
    KEY idx_user_settings_history_time (create_time),
    KEY idx_user_settings_history_key (setting_key, create_time)
);
