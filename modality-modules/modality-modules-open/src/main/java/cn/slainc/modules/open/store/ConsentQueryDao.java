package cn.slainc.modules.open.store;

import cn.slainc.core.jdbc.JdbcPager;
import cn.slainc.core.result.PageResult;
import cn.slainc.modules.open.OpenKeys;
import cn.slainc.openapi.model.ConsentRecord;
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

    public PageResult<ConsentRecord> page(String userId, String appId, String scope, Boolean active,
                                          Long from, Long to, long current, long size) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        if (userId != null && !userId.isBlank()) {
            where.append(" AND user_id = ?");
            args.add(userId.trim());
        }
        if (appId != null && !appId.isBlank()) {
            where.append(" AND app_id = ?");
            args.add(appId.trim());
        }
        if (active != null) {
            where.append(active ? " AND revoked_at IS NULL" : " AND revoked_at IS NOT NULL");
        }
        if (from != null) {
            where.append(" AND granted_at >= ?");
            args.add(from);
        }
        if (to != null) {
            where.append(" AND granted_at <= ?");
            args.add(to);
        }
        PageResult<ConsentRecord> result = JdbcPager.page(jdbcTemplate,
                "SELECT COUNT(*) FROM " + OpenKeys.CONSENT_TABLE + where,
                "SELECT * FROM " + OpenKeys.CONSENT_TABLE + where + " ORDER BY granted_at DESC",
                args, current, size, ConsentQueryDao::mapRow);
        if (scope != null && !scope.isBlank()) {
            // scope 过滤在内存做：scopes 是 JSON 列，先按其余条件收敛再精确匹配，total 保持过滤前的口径
            List<ConsentRecord> filtered = result.records().stream()
                    .filter(record -> record.getScopes().contains(scope.trim())).toList();
            result = PageResult.of(filtered, result.total(), result.current(), result.size());
        }
        return result;
    }

    public List<String> activeUserIds() {
        return jdbcTemplate.queryForList("SELECT DISTINCT user_id FROM " + OpenKeys.CONSENT_TABLE
                + " WHERE revoked_at IS NULL", String.class);
    }

    private static ConsentRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
        ConsentRecord record = new ConsentRecord();
        record.setId(rs.getString("id"));
        record.setUserId(rs.getString("user_id"));
        record.setAppId(rs.getString("app_id"));
        List<String> scopes = cn.slainc.utils.json.JsonUtils.parseList(rs.getString("scopes"), String.class);
        record.setScopes(scopes == null ? List.of() : scopes);
        record.setGrantedAt(rs.getLong("granted_at"));
        long revoked = rs.getLong("revoked_at");
        record.setRevokedAt(rs.wasNull() ? null : revoked);
        record.setSource(rs.getString("source"));
        return record;
    }
}
