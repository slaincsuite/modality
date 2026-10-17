package cn.slainc.modules.open.store;

import cn.slainc.core.jdbc.JdbcPager;
import cn.slainc.core.result.PageResult;
import cn.slainc.modules.open.OpenKeys;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

// 授权记录多条件分页：管理端与用户视角共用（直接查 t_open_consent，不经过 SPI 的窄接口）
public class ConsentQueryDao {

    private final JdbcTemplate jdbcTemplate;

    public ConsentQueryDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public PageResult<ConsentQueryRow> page(String userId, String appId, String scope, Boolean active,
                                            Long from, Long to, long current, long size) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        if (userId != null && !userId.isBlank()) {
            where.append(" AND c.user_id = ?");
            args.add(userId.trim());
        }
        if (appId != null && !appId.isBlank()) {
            where.append(" AND c.app_id = ?");
            args.add(appId.trim());
        }
        if (active != null) {
            where.append(active ? " AND c.revoked_at IS NULL" : " AND c.revoked_at IS NOT NULL");
        }
        if (from != null) {
            where.append(" AND c.granted_at >= ?");
            args.add(from);
        }
        if (to != null) {
            where.append(" AND c.granted_at <= ?");
            args.add(to);
        }
        PageResult<ConsentQueryRow> result = JdbcPager.page(jdbcTemplate,
                "SELECT COUNT(*) FROM " + OpenKeys.CONSENT_TABLE + " c" + where,
                "SELECT c.id, c.user_id, c.app_id, c.scopes, c.granted_at, c.revoked_at, c.revoke_reason,"
                        + " c.source, a.name AS app_name FROM " + OpenKeys.CONSENT_TABLE + " c"
                        + " LEFT JOIN " + OpenKeys.APP_TABLE + " a ON a.app_id = c.app_id"
                        + where + " ORDER BY c.granted_at DESC",
                args, current, size, ConsentQueryDao::mapRow);
        if (scope != null && !scope.isBlank()) {
            // scope 过滤在内存做：scopes 是 JSON 列，先按其余条件收敛再精确匹配，total 保持过滤前的口径
            List<ConsentQueryRow> filtered = result.records().stream()
                    .filter(record -> record.scopes().contains(scope.trim())).toList();
            result = PageResult.of(filtered, result.total(), result.current(), result.size());
        }
        return result;
    }

    public void markRevokeReason(String id, String reason) {
        if (id == null || reason == null) {
            return;
        }
        jdbcTemplate.update("UPDATE " + OpenKeys.CONSENT_TABLE + " SET revoke_reason = ? WHERE id = ?",
                reason, id);
    }

    public List<String> activeUserIds() {
        return jdbcTemplate.queryForList("SELECT DISTINCT user_id FROM " + OpenKeys.CONSENT_TABLE
                + " WHERE revoked_at IS NULL", String.class);
    }

    private static ConsentQueryRow mapRow(ResultSet rs, int rowNum) throws SQLException {
        List<String> scopes = cn.slainc.utils.json.JsonUtils.parseList(rs.getString("scopes"), String.class);
        long revoked = rs.getLong("revoked_at");
        boolean revokedNull = rs.wasNull();
        return new ConsentQueryRow(rs.getString("id"), rs.getString("user_id"), rs.getString("app_id"),
                rs.getString("app_name"), scopes == null ? List.of() : scopes, rs.getLong("granted_at"),
                revokedNull ? null : revoked, rs.getString("source"), rs.getString("revoke_reason"));
    }
}
