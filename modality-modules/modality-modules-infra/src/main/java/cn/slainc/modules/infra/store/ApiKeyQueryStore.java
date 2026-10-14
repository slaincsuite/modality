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
}
