package cn.slainc.modules.infra.service.apikey;

import cn.dev33.satoken.apikey.model.ApiKeyModel;
import cn.slainc.apikey.support.ApiKeyAdminService;
import cn.slainc.apikey.support.ApiKeyStatsService;
import cn.slainc.core.exception.BizException;
import cn.slainc.core.jdbc.JdbcPager;
import cn.slainc.core.lang.BizAssert;
import cn.slainc.core.result.PageResult;
import cn.slainc.core.result.ResultCode;
import cn.slainc.modules.infra.InfraKeys;
import cn.slainc.modules.infra.store.ApiKeyQueryStore;
import cn.slainc.modules.infra.store.ApiKeyRequestMetricsStore;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

public class ApiKeyManageService {

    public record ApiKeyView(long id, String apiKey, String title, String intro, String loginId, String scopes,
                             String status, Long expiresTime, Long lastUsedTime, Long createTime, Long updateTime) {
    }

    public record IssuedKeyView(String apiKey, String title, String intro, String loginId, List<String> scopes,
                                Map<String, Object> extraData, boolean isValid, long expiresTime, long createTime) {
    }

    public record ApiKeyStatsView(long total, long failureCount, double averageElapsedMillis, Long lastUsedTime,
                                  boolean detailedMetricsAvailable) {
    }

    public record RotationView(long id, String oldApiKey, String newApiKey, long graceHours, String operator,
                               String description, long rotateTime) {
    }

    private final JdbcTemplate jdbc;

    private final ApiKeyQueryStore queryStore;

    private final ApiKeyAdminService adminService;

    private final ApiKeyStatsService statsService;

    private final ApiKeyRequestMetricsStore metricsStore;

    public ApiKeyManageService(JdbcTemplate jdbc, ApiKeyQueryStore queryStore, ApiKeyAdminService adminService,
                               ApiKeyStatsService statsService, ApiKeyRequestMetricsStore metricsStore) {
        this.jdbc = jdbc;
        this.queryStore = queryStore;
        this.adminService = adminService;
        this.statsService = statsService;
        this.metricsStore = metricsStore;
    }

    public PageResult<Map<String, Object>> page(String status, String keyword, long current, long size) {
        long currentPage = JdbcPager.normalizeCurrent(current);
        long pageSize = JdbcPager.normalizeSize(size, 200);
        long offset = currentPage > Long.MAX_VALUE / pageSize
                ? Long.MAX_VALUE : (currentPage - 1) * pageSize;
        List<Map<String, Object>> records = new ArrayList<>();
        for (Map<String, Object> row : queryStore.page(status, keyword, offset, pageSize)) {
            Map<String, Object> record = new LinkedHashMap<>(row);
            Long lastUsedTime = lastUsed(stringOf(row.get("api_key")));
            record.put("lastUsedTime", lastUsedTime);
            record.put("last_used_time", lastUsedTime);
            records.add(record);
        }
        return PageResult.of(records, queryStore.count(status, keyword), currentPage, pageSize);
    }

    public List<ApiKeyView> listByLoginId(String loginId) {
        BizAssert.hasText(loginId, "loginId 不能为空");
        return queryStore.listByLoginId(loginId.trim()).stream().map(this::toView).toList();
    }

    public ApiKeyView detail(String apiKey) {
        BizAssert.hasText(apiKey, "API Key 不能为空");
        Map<String, Object> row = queryStore.find(apiKey.trim());
        if (row == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "API Key 不存在");
        }
        return toView(row);
    }

    public List<IssuedKeyView> issue(String loginId, String title, String intro, List<String> scopes, Long ttlDays,
                                     Map<String, Object> extraData, int count) {
        BizAssert.isTrue(intro == null || intro.length() <= 256, "用途说明不能超过 256 个字符");
        List<ApiKeyModel> models = count > 1
                ? adminService.batchIssue(loginId, title, scopes, ttlDays, extraData, count)
                : List.of(adminService.issue(loginId, title, scopes, ttlDays, extraData));
        List<IssuedKeyView> issued = new ArrayList<>(models.size());
        for (ApiKeyModel model : models) {
            applyIntro(model, intro);
            issued.add(toIssuedView(model));
        }
        return issued;
    }

    @Transactional(rollbackFor = Exception.class)
    public IssuedKeyView rotate(String apiKey, long graceHours, String operator) {
        ApiKeyView old = detail(apiKey);
        ApiKeyModel fresh = adminService.rotate(apiKey, graceHours);
        applyIntro(fresh, old.intro());
        String description = "轮换生成新密钥，旧密钥进入 " + graceHours + " 小时过渡期";
        jdbc.update("INSERT INTO " + InfraKeys.API_KEY_ROTATION_TABLE
                        + " (old_api_key, new_api_key, grace_hours, operator, description, rotate_time)"
                        + " VALUES (?, ?, ?, ?, ?, ?)",
                old.apiKey(), fresh.getApiKey(), graceHours, operator, description, System.currentTimeMillis());
        return toIssuedView(fresh);
    }

    public List<RotationView> rotations(String apiKey) {
        BizAssert.hasText(apiKey, "API Key 不能为空");
        detail(apiKey);
        return jdbc.query("SELECT id, old_api_key, new_api_key, grace_hours, operator, description, rotate_time FROM "
                        + InfraKeys.API_KEY_ROTATION_TABLE
                        + " WHERE old_api_key = ? OR new_api_key = ? ORDER BY rotate_time DESC, id DESC",
                (rs, rowNum) -> new RotationView(rs.getLong("id"), rs.getString("old_api_key"),
                        rs.getString("new_api_key"), rs.getLong("grace_hours"), rs.getString("operator"),
                        rs.getString("description"), rs.getLong("rotate_time")),
                apiKey.trim(), apiKey.trim());
    }

    public ApiKeyStatsView stats(String apiKey) {
        detail(apiKey);
        ApiKeyRequestMetricsStore.Summary summary = metricsStore.summary(apiKey);
        return new ApiKeyStatsView(summary.total(), summary.failureCount(), summary.averageElapsedMillis(),
                summary.lastUsedTime(), true);
    }

    @Transactional(rollbackFor = Exception.class)
    public ApiKeyView updateForUser(String loginId, String apiKey, String title, String intro, Boolean enabled) {
        BizAssert.hasText(loginId, "用户标识不能为空");
        ApiKeyView existing = detail(apiKey);
        BizAssert.isTrue(loginId.equals(existing.loginId()), "API Key 不属于指定用户");
        if (title != null || intro != null) {
            BizAssert.isTrue(intro == null || intro.length() <= 256, "用途说明不能超过 256 个字符");
            int updated = queryStore.updateMetadata(apiKey, title, intro);
            BizAssert.isTrue(updated > 0, "API Key 不存在");
        }
        if (enabled != null) {
            if (enabled) {
                adminService.enable(apiKey);
            } else {
                adminService.disable(apiKey);
            }
        }
        return detail(apiKey);
    }

    private ApiKeyView toView(Map<String, Object> row) {
        String apiKey = stringOf(row.get("api_key"));
        return new ApiKeyView(longOf(row.get("id")), apiKey, stringOf(row.get("title")),
                stringOf(row.get("intro")), stringOf(row.get("login_id")), stringOf(row.get("scopes")),
                stringOf(row.get("status")), nullableLong(row.get("expires_time")), lastUsed(apiKey),
                nullableLong(row.get("create_time")), nullableLong(row.get("update_time")));
    }

    private void applyIntro(ApiKeyModel model, String intro) {
        if (intro == null) {
            return;
        }
        int updated = queryStore.updateMetadata(model.getApiKey(), null, intro);
        BizAssert.isTrue(updated > 0, "API Key 用途说明保存失败");
        model.setIntro(intro);
    }

    private static IssuedKeyView toIssuedView(ApiKeyModel model) {
        return new IssuedKeyView(model.getApiKey(), model.getTitle(), model.getIntro(),
                String.valueOf(model.getLoginId()), model.getScopes(), model.getExtraData(),
                Boolean.TRUE.equals(model.getIsValid()), model.getExpiresTime(), model.getCreateTime());
    }

    private Long lastUsed(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return null;
        }
        Long cached = statsService.lastUsed(apiKey);
        return cached == null ? metricsStore.lastUsed(apiKey) : cached;
    }

    private static long longOf(Object value) {
        Long parsed = nullableLong(value);
        return parsed == null ? 0L : parsed;
    }

    private static Long nullableLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(String.valueOf(value));
    }

    private static String stringOf(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
