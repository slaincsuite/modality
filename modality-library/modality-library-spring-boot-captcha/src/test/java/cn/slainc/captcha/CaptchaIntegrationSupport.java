package cn.slainc.captcha;

import cn.slainc.captcha.store.CaptchaStore;
import cn.slainc.captcha.support.CaptchaDataCodec;
import cn.slainc.captcha.support.CaptchaKeys;
import cn.slainc.core.config.DynamicConfigService;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Set;

final class CaptchaIntegrationSupport {

    static final String[] DYNAMIC_KEYS = {
            CaptchaService.CONFIG_TYPE,
            CaptchaService.CONFIG_LENGTH,
            CaptchaService.CONFIG_TTL_SECONDS,
            CaptchaService.CONFIG_FAIL_THRESHOLD,
            CaptchaService.CONFIG_IP_LIMIT_PER_MINUTE,
            CaptchaService.CONFIG_IP_BLACKLIST
    };

    private CaptchaIntegrationSupport() {
    }

    static void clean(StringRedisTemplate template, DynamicConfigService dynamicConfigService) {
        deleteByPattern(template, "modality:captcha:*");
        deleteByPattern(template, "modality:ratelimit:*captcha*");
        if (dynamicConfigService != null) {
            for (String key : DYNAMIC_KEYS) {
                dynamicConfigService.remove(key);
            }
        }
    }

    static void deleteByPattern(StringRedisTemplate template, String pattern) {
        Set<String> keys = template.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            template.delete(keys);
        }
    }

    static CaptchaData storedData(CaptchaStore store, String channel, String id) {
        String json = store.get(CaptchaKeys.dataKey(channel, id));
        return json == null ? null : CaptchaDataCodec.read(json);
    }
}
