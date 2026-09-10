package cn.slainc.captcha;

import cn.slainc.core.config.DynamicConfigService;

public class CaptchaTypeResolver {

    public static final String CONFIG_KEY = "modality.captcha.type";

    private final CaptchaProperties properties;

    private final DynamicConfigService dynamicConfigService;

    public CaptchaTypeResolver(CaptchaProperties properties, DynamicConfigService dynamicConfigService) {
        if (properties == null) {
            throw new IllegalArgumentException("验证码配置不能为空");
        }
        this.properties = properties;
        this.dynamicConfigService = dynamicConfigService;
    }

    public CaptchaType resolve() {
        CaptchaType dynamic = dynamicConfigService == null ? null
                : CaptchaType.of(dynamicConfigService.getString(CONFIG_KEY, null));
        if (dynamic != null) {
            return dynamic;
        }
        CaptchaType configured = CaptchaType.of(properties.getType());
        return configured != null ? configured : CaptchaType.CHAR_ALNUM;
    }
}
