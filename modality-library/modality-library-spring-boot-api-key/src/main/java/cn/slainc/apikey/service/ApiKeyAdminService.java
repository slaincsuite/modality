package cn.slainc.apikey.service;

import cn.dev33.satoken.apikey.exception.ApiKeyException;
import cn.dev33.satoken.apikey.model.ApiKeyModel;
import cn.dev33.satoken.apikey.template.SaApiKeyTemplate;
import cn.dev33.satoken.dao.SaTokenDao;
import cn.slainc.apikey.ApiKeyGenerator;
import cn.slainc.apikey.ApiKeyRecord;
import cn.slainc.apikey.ApiKeyStatus;
import cn.slainc.apikey.config.ApiKeyProperties;
import cn.slainc.apikey.loader.JdbcApiKeyDataLoader;
import cn.slainc.apikey.mapper.ApiKeyMapper;
import cn.slainc.apikey.support.ApiKeyJson;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ApiKeyAdminService {

    private static final long DAY_MILLIS = 86400000L;

    private static final long HOUR_MILLIS = 3600000L;

    private static final int MAX_KEY_RETRY = 5;

    private final ApiKeyMapper mapper;

    private final SaApiKeyTemplate template;

    private final ApiKeyProperties properties;

    private final ApiKeyGenerator generator;

    public ApiKeyAdminService(ApiKeyMapper mapper, SaApiKeyTemplate template, ApiKeyProperties properties) {
        this.mapper = mapper;
        this.template = template;
        this.properties = properties;
        this.generator = new ApiKeyGenerator(properties.getKeyPrefix(), properties.getKeyLength());
    }

    public ApiKeyModel issue(Object loginId, String title, List<String> scopes, Long ttlDays,
                             Map<String, Object> extraData) {
        if (loginId == null || String.valueOf(loginId).isBlank()) {
            throw new ApiKeyException("签发 API Key 时 loginId 不能为空");
        }
        long effectiveDays = ttlDays == null ? properties.getDefaultTtlDays() : ttlDays;
        long now = System.currentTimeMillis();
        ApiKeyRecord record = new ApiKeyRecord();
        record.setApiKey(generateUniqueKey());
        record.setTitle(title);
        record.setIntro(null);
        record.setLoginId(String.valueOf(loginId));
        record.setScopes(joinScopes(scopes));
        record.setExtraData(ApiKeyJson.writeExtra(extraData));
        record.setStatus(ApiKeyStatus.ENABLED);
        record.setExpiresTime(effectiveDays <= 0 ? SaTokenDao.NEVER_EXPIRE : now + effectiveDays * DAY_MILLIS);
        record.setCreateTime(now);
        record.setUpdateTime(now);
        mapper.insert(record);
        ApiKeyModel model = JdbcApiKeyDataLoader.toModel(record);
        template.saveApiKey(model);
        return model;
    }

    public List<ApiKeyModel> batchIssue(Object loginId, String title, List<String> scopes, Long ttlDays,
                                        Map<String, Object> extraData, int count) {
        if (count <= 0) {
            throw new ApiKeyException("批量签发数量必须大于 0，当前为 " + count);
        }
        List<ApiKeyModel> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            result.add(issue(loginId, title, scopes, ttlDays, extraData));
        }
        return result;
    }

    public void disable(String apiKey) {
        changeStatus(apiKey, ApiKeyStatus.DISABLED);
    }

    public void enable(String apiKey) {
        changeStatus(apiKey, ApiKeyStatus.ENABLED);
    }

    public void delete(String apiKey) {
        ApiKeyRecord record = findRecord(apiKey);
        if (record == null) {
            return;
        }
        record.setStatus(ApiKeyStatus.DELETED);
        touch(record);
        mapper.updateById(record);
        template.deleteApiKey(apiKey);
    }

    public ApiKeyModel extend(String apiKey, long days) {
        if (days <= 0) {
            throw new ApiKeyException("延长天数必须大于 0，当前为 " + days);
        }
        ApiKeyRecord record = findRecord(apiKey);
        if (record == null) {
            throw new ApiKeyException("无效 API Key: " + apiKey);
        }
        if (record.getExpiresTime() != null && record.getExpiresTime() > 0) {
            long base = Math.max(record.getExpiresTime(), System.currentTimeMillis());
            record.setExpiresTime(base + days * DAY_MILLIS);
            touch(record);
            mapper.updateById(record);
            template.saveApiKey(JdbcApiKeyDataLoader.toModel(record));
        }
        return JdbcApiKeyDataLoader.toModel(record);
    }

    public ApiKeyModel rotate(String apiKey, long graceHours) {
        if (graceHours < 0) {
            throw new ApiKeyException("轮换宽限期不能为负数，当前为 " + graceHours);
        }
        ApiKeyRecord old = findRecord(apiKey);
        if (old == null) {
            throw new ApiKeyException("无效 API Key: " + apiKey);
        }
        ApiKeyModel fresh = issue(old.getLoginId(), old.getTitle(),
                JdbcApiKeyDataLoader.splitScopes(old.getScopes()), properties.getDefaultTtlDays(),
                ApiKeyJson.parseExtra(old.getExtraData()));
        long now = System.currentTimeMillis();
        old.setExpiresTime(now + graceHours * HOUR_MILLIS);
        touch(old);
        mapper.updateById(old);
        if (old.getExpiresTime() > now) {
            template.saveApiKey(JdbcApiKeyDataLoader.toModel(old));
        } else {
            template.deleteApiKey(apiKey);
        }
        return fresh;
    }

    public List<ApiKeyRecord> listByLoginId(Object loginId) {
        return mapper.selectList(new LambdaQueryWrapper<ApiKeyRecord>()
                .eq(ApiKeyRecord::getLoginId, String.valueOf(loginId))
                .ne(ApiKeyRecord::getStatus, ApiKeyStatus.DELETED)
                .orderByDesc(ApiKeyRecord::getCreateTime));
    }

    public String exportCsv(Object loginId) {
        return new ApiKeyCsvWriter().write(listByLoginId(loginId));
    }

    private void changeStatus(String apiKey, String status) {
        ApiKeyRecord record = findRecord(apiKey);
        if (record == null) {
            throw new ApiKeyException("无效 API Key: " + apiKey);
        }
        record.setStatus(status);
        touch(record);
        mapper.updateById(record);
        template.saveApiKey(JdbcApiKeyDataLoader.toModel(record));
    }

    private ApiKeyRecord findRecord(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return null;
        }
        return mapper.selectOne(new LambdaQueryWrapper<ApiKeyRecord>()
                .eq(ApiKeyRecord::getApiKey, apiKey)
                .ne(ApiKeyRecord::getStatus, ApiKeyStatus.DELETED));
    }

    private String generateUniqueKey() {
        for (int i = 0; i < MAX_KEY_RETRY; i++) {
            String candidate = generator.next();
            Long count = mapper.selectCount(new LambdaQueryWrapper<ApiKeyRecord>()
                    .eq(ApiKeyRecord::getApiKey, candidate));
            if (count == null || count == 0) {
                return candidate;
            }
        }
        throw new ApiKeyException("多次生成 API Key 均与现有记录冲突，请调整 key-length 后重试");
    }

    private static void touch(ApiKeyRecord record) {
        record.setUpdateTime(System.currentTimeMillis());
    }

    private static String joinScopes(List<String> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            return null;
        }
        return String.join(",", scopes.stream().map(String::trim).filter(s -> !s.isEmpty()).toList());
    }
}
