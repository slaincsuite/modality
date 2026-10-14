package cn.slainc.modules.open.store;

import cn.slainc.core.exception.BizException;
import cn.slainc.core.result.ResultCode;
import cn.slainc.modules.open.OpenKeys;
import cn.slainc.openapi.domain.App;
import cn.slainc.openapi.domain.AppStatus;
import cn.slainc.openapi.spi.AppStore;
import cn.slainc.utils.json.JsonUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

// 应用表实现 open-api 的 AppStore：delete 语义为逻辑删除（deleted=1），find/list 一律过滤已删行
public class JdbcAppStore implements AppStore {

    private final JdbcTemplate jdbcTemplate;

    public JdbcAppStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public App save(App app) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + OpenKeys.APP_TABLE + " WHERE app_id = ?", Long.class, app.getAppId());
        if (count != null && count > 0) {
            jdbcTemplate.update("UPDATE " + OpenKeys.APP_TABLE + " SET name = ?, description = ?, logo = ?,"
                            + " category = ?, contact = ?, tags = ?, app_type = ?, status = ?, owner_id = ?,"
                            + " redirect_uris = ?, scopes = ?, updated_at = ? WHERE app_id = ?",
                    app.getName(), app.getDescription(), app.getLogo(), app.getCategory(), app.getContact(),
                    JsonUtils.toJson(app.getTags()), app.getAppType().name(), app.getStatus().name(),
                    app.getOwnerId(), JsonUtils.toJson(app.getRedirectUris()), JsonUtils.toJson(app.getScopes()),
                    System.currentTimeMillis(), app.getAppId());
            return app;
        }
        insert(app);
        return app;
    }

    @Override
    public boolean compareAndSetStatus(App app, AppStatus expectedStatus) {
        int updated = jdbcTemplate.update("UPDATE " + OpenKeys.APP_TABLE + " SET status = ?, updated_at = ?"
                        + " WHERE app_id = ? AND status = ? AND deleted = 0",
                app.getStatus().name(), System.currentTimeMillis(), app.getAppId(), expectedStatus.name());
        return updated > 0;
    }

    // COUNT 判定与 INSERT 之间存在竞态窗口，唯一键冲突统一转成中文业务异常
    void insert(App app) {
        try {
            jdbcTemplate.update("INSERT INTO " + OpenKeys.APP_TABLE
                            + " (app_id, name, description, logo, category, contact, tags, app_type, status, owner_id,"
                            + " redirect_uris, scopes, deleted, created_at, updated_at)"
                            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)",
                    app.getAppId(), app.getName(), app.getDescription(), app.getLogo(), app.getCategory(),
                    app.getContact(), JsonUtils.toJson(app.getTags()), app.getAppType().name(),
                    app.getStatus().name(), app.getOwnerId(), JsonUtils.toJson(app.getRedirectUris()),
                    JsonUtils.toJson(app.getScopes()), app.getCreatedAt(), app.getUpdatedAt());
        } catch (DuplicateKeyException exception) {
            throw new BizException(ResultCode.DATA_DUPLICATE, "应用 " + app.getAppId() + " 已存在");
        }
    }

    @Override
    public Optional<App> find(String appId) {
        return jdbcTemplate.query("SELECT * FROM " + OpenKeys.APP_TABLE + " WHERE app_id = ? AND deleted = 0",
                JdbcAppStore::mapRow, appId).stream().findFirst();
    }

    @Override
    public List<App> list() {
        return jdbcTemplate.query("SELECT * FROM " + OpenKeys.APP_TABLE + " WHERE deleted = 0"
                + " ORDER BY created_at DESC", JdbcAppStore::mapRow);
    }

    @Override
    public void delete(String appId) {
        jdbcTemplate.update("UPDATE " + OpenKeys.APP_TABLE + " SET deleted = 1, updated_at = ? WHERE app_id = ?",
                System.currentTimeMillis(), appId);
    }

    private static App mapRow(ResultSet rs, int rowNum) throws SQLException {
        App app = new App();
        app.setAppId(rs.getString("app_id"));
        app.setName(rs.getString("name"));
        app.setDescription(rs.getString("description"));
        app.setLogo(rs.getString("logo"));
        app.setCategory(rs.getString("category"));
        app.setContact(rs.getString("contact"));
        app.setTags(readList(rs.getString("tags")));
        app.setAppType(rs.getString("app_type"));
        app.setStatus(rs.getString("status"));
        app.setOwnerId(rs.getString("owner_id"));
        app.setRedirectUris(readList(rs.getString("redirect_uris")));
        app.setScopes(readList(rs.getString("scopes")));
        app.setCreatedAt(rs.getLong("created_at"));
        app.setUpdatedAt(rs.getLong("updated_at"));
        return app;
    }

    private static List<String> readList(String json) {
        List<String> values = JsonUtils.parseList(json == null ? "[]" : json, String.class);
        return values == null ? List.of() : values;
    }
}
