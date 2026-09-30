CREATE TABLE IF NOT EXISTS t_policy_meta (
    policy_id VARCHAR(64) NOT NULL PRIMARY KEY,
    group_name VARCHAR(64) NOT NULL DEFAULT '默认',
    system_flag TINYINT NOT NULL DEFAULT 0,
    create_time BIGINT NOT NULL,
    update_time BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS t_policy_test_case (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    policy_id VARCHAR(64),
    name VARCHAR(128) NOT NULL,
    description VARCHAR(256),
    request_json TEXT NOT NULL,
    expected VARCHAR(16) NOT NULL,
    enabled TINYINT NOT NULL DEFAULT 1,
    create_time BIGINT NOT NULL,
    update_time BIGINT NOT NULL
);
