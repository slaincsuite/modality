-- modality 首次部署播种脚本（幂等，可重复执行）
-- 执行前提：应用已启动过一次，各模块已完成自动建表
-- 用法：docker compose exec -T mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE" < deploy/seed-admin.sql
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
