package cn.slainc.sso.template;

import cn.dev33.satoken.sso.config.SaSsoClientModel;
import cn.dev33.satoken.sso.template.SaSsoServerTemplate;
import cn.slainc.sso.client.SsoClientRecord;
import cn.slainc.sso.client.SsoClientRecordCache;

import java.util.ArrayList;
import java.util.List;

public class DynamicSsoServerTemplate extends SaSsoServerTemplate {

    private final SsoClientRecordCache cache;

    public DynamicSsoServerTemplate(SsoClientRecordCache cache) {
        this.cache = cache;
    }

    @Override
    public List<SaSsoClientModel> getClients() {
        List<SaSsoClientModel> models = new ArrayList<>();
        for (SsoClientRecord record : cache.listEnabled()) {
            models.add(record.toModel());
        }
        return models;
    }

    public void evictCache() {
        cache.evictAll();
    }
}
