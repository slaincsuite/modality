package cn.slainc.modules.open.store;

import cn.slainc.modules.open.OpenKeys;
import cn.slainc.openapi.model.OpenScope;
import cn.slainc.openapi.model.ScopeRiskLevel;
import cn.slainc.openapi.spi.ScopeStore;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class JdbcScopeStore implements ScopeStore {

    private final JdbcTemplate jdbcTemplate;

    public JdbcScopeStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public OpenScope save(OpenScope scope) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + OpenKeys.SCOPE_TABLE + " WHERE name = ?", Long.class, scope.getName());
        if (count != null && count > 0) {
            jdbcTemplate.update("UPDATE " + OpenKeys.SCOPE_TABLE
                            + " SET title = ?, description = ?, group_name = ?, risk_level = ?, requires_audit = ?"
                            + " WHERE name = ?",
                    scope.getTitle(), scope.getDescription(), scope.getGroup(), scope.getRiskLevel().name(),
                    scope.isRequiresAudit() ? 1 : 0, scope.getName());
            return scope;
        }
        jdbcTemplate.update("INSERT INTO " + OpenKeys.SCOPE_TABLE
                        + " (name, title, description, group_name, risk_level, requires_audit)"
                        + " VALUES (?, ?, ?, ?, ?, ?)",
                scope.getName(), scope.getTitle(), scope.getDescription(), scope.getGroup(),
                scope.getRiskLevel().name(), scope.isRequiresAudit() ? 1 : 0);
        return scope;
    }

    @Override
    public Optional<OpenScope> find(String name) {
        return jdbcTemplate.query("SELECT * FROM " + OpenKeys.SCOPE_TABLE + " WHERE name = ?",
                JdbcScopeStore::mapRow, name).stream().findFirst();
    }

    @Override
    public List<OpenScope> list() {
        return jdbcTemplate.query("SELECT * FROM " + OpenKeys.SCOPE_TABLE + " ORDER BY group_name, name",
                JdbcScopeStore::mapRow);
    }

    @Override
    public void remove(String name) {
        jdbcTemplate.update("DELETE FROM " + OpenKeys.SCOPE_TABLE + " WHERE name = ?", name);
    }

    private static OpenScope mapRow(ResultSet rs, int rowNum) throws SQLException {
        OpenScope scope = new OpenScope();
        scope.setName(rs.getString("name"));
        scope.setTitle(rs.getString("title"));
        scope.setDescription(rs.getString("description"));
        scope.setGroup(rs.getString("group_name"));
        scope.setRiskLevel(ScopeRiskLevel.valueOf(rs.getString("risk_level")));
        scope.setRequiresAudit(rs.getInt("requires_audit") == 1);
        return scope;
    }
}
