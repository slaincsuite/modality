package cn.slainc.apikey.template;

import cn.dev33.satoken.apikey.model.ApiKeyModel;
import cn.dev33.satoken.apikey.template.SaApiKeyTemplate;
import cn.dev33.satoken.context.model.SaRequest;
import cn.slainc.apikey.guard.ApiKeyGuard;

public class GuardedApiKeyTemplate extends SaApiKeyTemplate {

    private final String header;

    private final ApiKeyGuard guard;

    public GuardedApiKeyTemplate(String namespace, String header, ApiKeyGuard guard) {
        super(namespace);
        this.header = header;
        this.guard = guard;
    }

    @Override
    public String readApiKeyValue(SaRequest request) {
        if (request != null && header != null && !header.isBlank()) {
            String value = request.getHeader(header);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return super.readApiKeyValue(request);
    }

    @Override
    public ApiKeyModel checkApiKey(String apiKey) {
        ApiKeyModel model = super.checkApiKey(apiKey);
        if (guard != null) {
            guard.check(apiKey, model);
        }
        return model;
    }
}
