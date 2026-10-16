-- modality 首次部署播种脚本（幂等，可重复执行）
-- 执行前提：应用已启动过一次，各模块已完成自动建表
-- 用法：docker compose exec -T mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE" < db/seed-admin.sql
-- 默认管理员：admin / Admin@12345（bcrypt 哈希由本项目 BCryptUtils 生成，登录后请立即修改口令）

SET @now = UNIX_TIMESTAMP() * 1000;

INSERT IGNORE INTO t_permission (code, name, type, parent_id, sort, status, create_time, update_time)
SELECT code, code, 'API', 0, 0, 'enabled', @now, @now FROM (
  SELECT 'upms:access:grant' AS code UNION ALL
  SELECT 'upms:access:query' UNION ALL
  SELECT 'upms:access:revoke' UNION ALL
  SELECT 'upms:dept:create' UNION ALL
  SELECT 'upms:dept:delete' UNION ALL
  SELECT 'upms:dept:query' UNION ALL
  SELECT 'upms:dept:update' UNION ALL
  SELECT 'upms:group:assign' UNION ALL
  SELECT 'upms:group:create' UNION ALL
  SELECT 'upms:group:delete' UNION ALL
  SELECT 'upms:group:query' UNION ALL
  SELECT 'upms:group:update' UNION ALL
  SELECT 'upms:log:export' UNION ALL
  SELECT 'upms:log:query' UNION ALL
  SELECT 'upms:menu:create' UNION ALL
  SELECT 'upms:menu:delete' UNION ALL
  SELECT 'upms:menu:query' UNION ALL
  SELECT 'upms:menu:update' UNION ALL
  SELECT 'upms:permission:create' UNION ALL
  SELECT 'upms:permission:delete' UNION ALL
  SELECT 'upms:permission:export' UNION ALL
  SELECT 'upms:permission:import' UNION ALL
  SELECT 'upms:permission:query' UNION ALL
  SELECT 'upms:permission:update' UNION ALL
  SELECT 'upms:policy:bind' UNION ALL
  SELECT 'upms:policy:query' UNION ALL
  SELECT 'upms:policy:unbind' UNION ALL
  SELECT 'upms:role:assign-permission' UNION ALL
  SELECT 'upms:role:assign-user' UNION ALL
  SELECT 'upms:role:create' UNION ALL
  SELECT 'upms:role:data-scope' UNION ALL
  SELECT 'upms:role:delete' UNION ALL
  SELECT 'upms:role:query' UNION ALL
  SELECT 'upms:role:update' UNION ALL
  SELECT 'upms:stats:query' UNION ALL
  SELECT 'upms:system:create' UNION ALL
  SELECT 'upms:system:delete' UNION ALL
  SELECT 'upms:system:query' UNION ALL
  SELECT 'upms:system:update' UNION ALL
  SELECT 'upms:system:visibility' UNION ALL
  SELECT 'upms:user:assign-role' UNION ALL
  SELECT 'upms:user:create' UNION ALL
  SELECT 'upms:user:delete' UNION ALL
  SELECT 'upms:user:disable' UNION ALL
  SELECT 'upms:user:export' UNION ALL
  SELECT 'upms:user:import' UNION ALL
  SELECT 'upms:user:merge' UNION ALL
  SELECT 'upms:user:query' UNION ALL
  SELECT 'upms:user:reset-password' UNION ALL
  SELECT 'upms:user:update' UNION ALL
  SELECT 'tenant:manage:create' UNION ALL
  SELECT 'tenant:manage:delete' UNION ALL
  SELECT 'tenant:manage:export' UNION ALL
  SELECT 'audit:log:query' UNION ALL
  SELECT 'audit:log:export' UNION ALL
  SELECT 'audit:api-log:query' UNION ALL
  SELECT 'audit:api-log:export' UNION ALL
  SELECT 'audit:policy-log:query' UNION ALL
  SELECT 'audit:online:query' UNION ALL
  SELECT 'audit:online:export' UNION ALL
  SELECT 'audit:online:kick' UNION ALL
  SELECT 'audit:maintenance:cleanup' UNION ALL
  SELECT 'infra:config:query' UNION ALL
  SELECT 'infra:config:create' UNION ALL
  SELECT 'infra:config:update' UNION ALL
  SELECT 'infra:config:delete' UNION ALL
  SELECT 'infra:config:rollback' UNION ALL
  SELECT 'infra:config:transfer' UNION ALL
  SELECT 'infra:dict:query' UNION ALL
  SELECT 'infra:dict:create' UNION ALL
  SELECT 'infra:dict:update' UNION ALL
  SELECT 'infra:dict:delete' UNION ALL
  SELECT 'infra:dict:transfer' UNION ALL
  SELECT 'infra:notice:query' UNION ALL
  SELECT 'infra:notice:create' UNION ALL
  SELECT 'infra:notice:update' UNION ALL
  SELECT 'infra:notice:delete' UNION ALL
  SELECT 'infra:notify:query' UNION ALL
  SELECT 'infra:notify:create' UNION ALL
  SELECT 'infra:notify:update' UNION ALL
  SELECT 'infra:notify:delete' UNION ALL
  SELECT 'infra:notify:send' UNION ALL
  SELECT 'infra:notify:retry' UNION ALL
  SELECT 'infra:file:query' UNION ALL
  SELECT 'infra:file:upload' UNION ALL
  SELECT 'infra:file:delete' UNION ALL
  SELECT 'infra:file:manage' UNION ALL
  SELECT 'infra:redis:query' UNION ALL
  SELECT 'infra:redis:delete' UNION ALL
  SELECT 'infra:sql:query' UNION ALL
  SELECT 'infra:log:query' UNION ALL
  SELECT 'infra:log:level' UNION ALL
  SELECT 'infra:cache:query' UNION ALL
  SELECT 'infra:cache:clear' UNION ALL
  SELECT 'infra:apikey:query' UNION ALL
  SELECT 'infra:apikey:issue' UNION ALL
  SELECT 'infra:apikey:manage' UNION ALL
  SELECT 'infra:system:query' UNION ALL
  SELECT 'infra:system:alert' UNION ALL
  SELECT 'infra:search:query' UNION ALL
  SELECT 'infra:search:index' UNION ALL
  SELECT 'infra:search:remove' UNION ALL
  SELECT 'infra:recycle:query' UNION ALL
  SELECT 'infra:recycle:restore' UNION ALL
  SELECT 'infra:recycle:purge' UNION ALL
  SELECT 'job:task:query' UNION ALL
  SELECT 'job:task:create' UNION ALL
  SELECT 'job:task:update' UNION ALL
  SELECT 'job:task:delete' UNION ALL
  SELECT 'job:task:trigger' UNION ALL
  SELECT 'job:task:preview' UNION ALL
  SELECT 'job:log:query' UNION ALL
  SELECT 'job:log:delete' UNION ALL
  SELECT 'job:log:clean' UNION ALL
  SELECT 'job:node:query' UNION ALL
  SELECT 'open:app:query' UNION ALL
  SELECT 'open:app:review' UNION ALL
  SELECT 'open:app:offline' UNION ALL
  SELECT 'open:app:delete' UNION ALL
  SELECT 'open:scope:query' UNION ALL
  SELECT 'open:scope:create' UNION ALL
  SELECT 'open:scope:update' UNION ALL
  SELECT 'open:scope:delete' UNION ALL
  SELECT 'open:consent:query' UNION ALL
  SELECT 'open:consent:export' UNION ALL
  SELECT 'open:consent:revoke' UNION ALL
  SELECT 'open:stats:query' UNION ALL
  SELECT 'open:webhook:query' UNION ALL
  SELECT 'open:webhook:create' UNION ALL
  SELECT 'open:webhook:update' UNION ALL
  SELECT 'open:webhook:delete' UNION ALL
  SELECT 'open:webhook:delivery:query' UNION ALL
  SELECT 'open:webhook:delivery:retry' UNION ALL
  SELECT 'policy:manage:query' UNION ALL
  SELECT 'policy:manage:create' UNION ALL
  SELECT 'policy:manage:update' UNION ALL
  SELECT 'policy:manage:delete' UNION ALL
  SELECT 'policy:manage:version' UNION ALL
  SELECT 'policy:manage:transfer' UNION ALL
  SELECT 'policy:simulate:run' UNION ALL
  SELECT 'policy:test-case:query' UNION ALL
  SELECT 'policy:test-case:save' UNION ALL
  SELECT 'policy:test-case:delete' UNION ALL
  SELECT 'policy:test-case:run' UNION ALL
  SELECT 'policy:binding:query' UNION ALL
  SELECT 'policy:binding:bind' UNION ALL
  SELECT 'policy:binding:unbind' UNION ALL
  SELECT 'tenant:plan:query' UNION ALL
  SELECT 'tenant:plan:create' UNION ALL
  SELECT 'tenant:plan:update' UNION ALL
  SELECT 'tenant:plan:delete' UNION ALL
  SELECT 'tenant:plan:assign' UNION ALL
  SELECT 'tenant:plan:bind' UNION ALL
  SELECT 'workorder:order:query' UNION ALL
  SELECT 'workorder:order:assign' UNION ALL
  SELECT 'workorder:order:close' UNION ALL
  SELECT 'workorder:order:export' UNION ALL
  SELECT 'workorder:stats:query' UNION ALL
  SELECT 'workorder:comment:manage' UNION ALL
  SELECT 'workorder:attachment:manage' UNION ALL
  SELECT 'workorder:notification:manage' UNION ALL
  SELECT 'tenant:manage:query' UNION ALL
  SELECT 'tenant:manage:update'
) AS seed;

INSERT IGNORE INTO t_role (tenant_id, code, name, parent_id, data_scope, sort, status, create_time, update_time)
VALUES (1, 'platform-admin', '平台管理员', 0, 'ALL', 0, 'enabled', @now, @now);

INSERT IGNORE INTO t_user (tenant_id, username, nickname, password_hash, login_type, status, must_change_password,
                           create_time, update_time)
VALUES (1, 'admin', '平台管理员', '$2a$10$.VHlHQyXkl5Lod9WGjkpi.Ct2F/3V.Z2GiIK.Bh9PnwdMOvkUL63K', 'TENANT_USER', 'enabled', 1, @now, @now);

INSERT IGNORE INTO t_user_role (tenant_id, user_id, role_id, create_time)
SELECT 1, u.id, r.id, @now
FROM t_user u
JOIN t_role r ON r.tenant_id = 1 AND r.code = 'platform-admin'
WHERE u.login_type = 'TENANT_USER' AND u.username = 'admin' AND u.deleted = 0;

INSERT IGNORE INTO t_role_permission (tenant_id, role_id, permission_id, create_time)
SELECT 1, r.id, p.id, @now
FROM t_role r
CROSS JOIN t_permission p
WHERE r.tenant_id = 1 AND r.code = 'platform-admin';

SELECT r.code AS role_code, COUNT(*) AS granted_permissions
FROM t_role r
JOIN t_role_permission rp ON rp.role_id = r.id
WHERE r.tenant_id = 1 AND r.code = 'platform-admin'
GROUP BY r.code;
