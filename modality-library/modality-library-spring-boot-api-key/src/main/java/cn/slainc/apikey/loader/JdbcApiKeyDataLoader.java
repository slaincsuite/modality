package cn.slainc.apikey.loader;

import cn.dev33.satoken.apikey.loader.SaApiKeyDataLoader;
import cn.dev33.satoken.apikey.model.ApiKeyModel;
import cn.dev33.satoken.dao.SaTokenDao;
import cn.slainc.apikey.ApiKeyRecord;
import cn.slainc.apikey.ApiKeyStatus;
import cn.slainc.apikey.mapper.ApiKeyMapper;
import cn.slainc.apikey.support.ApiKeyJson;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class JdbcApiKeyDataLoader implements SaApiKeyDataLoader {

    private final ApiKeyMapper mapper;

    private final String namespace;

    public JdbcApiKeyDataLoader(ApiKeyMapper mapper, String namespace) {
        if (mapper == null) {
            throw new IllegalArgumentException("apiKeyMapper 不能为空");
        }
        this.mapper = mapper;
        this.namespace = namespace;
    }

    @Override
    public ApiKeyModel getApiKeyModelFromDatabase(String namespace, String apiKey) {
        if (apiKey == null || apiKey.isBlank() || !this.namespace.equals(namespace)) {
            return null;
        }
        ApiKeyRecord record = mapper.selectOne(new LambdaQueryWrapper<ApiKeyRecord>()
                .eq(ApiKeyRecord::getApiKey, apiKey)
                .eq(ApiKeyRecord::getStatus, ApiKeyStatus.ENABLED));
        if (record == null || record.expired(System.currentTimeMillis())) {
            return null;
        }
        return toModel(record);
    }

    public static ApiKeyModel toModel(ApiKeyRecord record) {
        ApiKeyModel model = new ApiKeyModel();
        model.setTitle(record.getTitle());
        model.setIntro(record.getIntro());
        model.setApiKey(record.getApiKey());
        model.setLoginId(record.getLoginId());
        model.setCreateTime(record.getCreateTime() == null ? System.currentTimeMillis() : record.getCreateTime());
        long expiresTime = record.getExpiresTime() == null ? SaTokenDao.NEVER_EXPIRE : record.getExpiresTime();
        model.setExpiresTime(expiresTime);
        model.setIsValid(ApiKeyStatus.ENABLED.equals(record.getStatus())
                && !(expiresTime != SaTokenDao.NEVER_EXPIRE
                && System.currentTimeMillis() > expiresTime));
        model.setScopes(splitScopes(record.getScopes()));
        Map<String, Object> extra = ApiKeyJson.parseExtra(record.getExtraData());
        if (extra != null) {
            model.setExtraData(extra);
        }
        return model;
    }

    public static List<String> splitScopes(String scopes) {
        List<String> result = new ArrayList<>();
        if (scopes != null && !scopes.isBlank()) {
            for (String part : scopes.split(",")) {
                if (!part.isBlank()) {
                    result.add(part.trim());
                }
            }
        }
        return result;
    }
}
