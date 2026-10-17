package cn.slainc.modules.open.store;

import cn.slainc.modules.open.OpenKeys;
import cn.slainc.modules.open.entity.ScopeGroupRecord;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class JdbcScopeGroupStore {

    private final JdbcTemplate jdbcTemplate;

    public JdbcScopeGroupStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(ScopeGroupRecord group) {
        jdbcTemplate.update("INSERT INTO " + OpenKeys.SCOPE_GROUP_TABLE
                        + " (name, description, sort_order, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
                group.getName(), group.getDescription(), group.getSort(), group.getCreatedAt(),
                group.getUpdatedAt());
    }

    public int update(String name, String description, int sort, long updatedAt) {
        return jdbcTemplate.update("UPDATE " + OpenKeys.SCOPE_GROUP_TABLE
                        + " SET description = ?, sort_order = ?, updated_at = ? WHERE name = ?",
                description, sort, updatedAt, name);
    }

    public Optional<ScopeGroupRecord> find(String name) {
        return jdbcTemplate.query("SELECT * FROM " + OpenKeys.SCOPE_GROUP_TABLE + " WHERE name = ?",
                JdbcScopeGroupStore::mapRow, name).stream().findFirst();
    }

    public List<ScopeGroupRecord> list() {
        return jdbcTemplate.query("SELECT * FROM " + OpenKeys.SCOPE_GROUP_TABLE
                + " ORDER BY sort_order, name", JdbcScopeGroupStore::mapRow);
    }

    public int delete(String name) {
        return jdbcTemplate.update("DELETE FROM " + OpenKeys.SCOPE_GROUP_TABLE + " WHERE name = ?", name);
    }

    private static ScopeGroupRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new ScopeGroupRecord(rs.getString("name"), rs.getString("description"),
                rs.getInt("sort_order"), rs.getLong("created_at"), rs.getLong("updated_at"));
    }
}
