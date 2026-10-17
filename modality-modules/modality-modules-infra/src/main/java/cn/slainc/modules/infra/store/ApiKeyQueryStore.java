package cn.slainc.modules.infra.store;

import cn.slainc.modules.infra.InfraKeys;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;


public class ApiKeyQueryStore {

    private final JdbcTemplate jdbc;

    public ApiKeyQueryStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> page(String status, String keyword, long offset, long limit) {
        return jdbc.queryForList("SELECT id, api_key, title, login_id, scopes, status, expires_time, "
                + "create_time, update_time FROM " + InfraKeys.API_KEY_TABLE
                + " WHERE status != 'deleted'"
                + " AND (? IS NULL OR status = ?)"
                + " AND (? IS NULL OR title LIKE CONCAT('%', ?, '%') OR api_key LIKE CONCAT('%', ?, '%'))"
                + " ORDER BY create_time DESC LIMIT ? OFFSET ?",
                (Object) status, status, keyword, keyword, keyword, limit, offset);
    }

    public long count(String status, String keyword) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM " + InfraKeys.API_KEY_TABLE
                + " WHERE status != 'deleted'"
                + " AND (? IS NULL OR status = ?)"
                + " AND (? IS NULL OR title LIKE CONCAT('%', ?, '%') OR api_key LIKE CONCAT('%', ?, '%'))",
                Long.class, (Object) status, status, keyword, keyword, keyword);
        return count == null ? 0 : count;
    }

    public List<Map<String, Object>> listByLoginId(String loginId) {
        return jdbc.queryForList("SELECT id, api_key, title, intro, login_id, scopes, status, expires_time, "
                + "create_time, update_time FROM " + InfraKeys.API_KEY_TABLE
                + " WHERE login_id = ? AND status != 'deleted' ORDER BY create_time DESC", loginId);
    }

    public Map<String, Object> find(String apiKey) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, api_key, title, intro, login_id, scopes, status, expires_time, create_time, update_time"
                        + " FROM " + InfraKeys.API_KEY_TABLE + " WHERE api_key = ? AND status != 'deleted'", apiKey);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public int updateMetadata(String apiKey, String title, String intro) {
        return jdbc.update("UPDATE " + InfraKeys.API_KEY_TABLE
                        + " SET title = COALESCE(?, title), intro = COALESCE(?, intro), update_time = ?"
                        + " WHERE api_key = ? AND status != 'deleted'",
                title, intro, System.currentTimeMillis(), apiKey);
    }
}
