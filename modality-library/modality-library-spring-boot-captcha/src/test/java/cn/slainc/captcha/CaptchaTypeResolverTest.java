package cn.slainc.captcha;

import cn.slainc.core.config.DynamicConfigService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CaptchaTypeResolverTest {

    @Test
    void resolvesPropertiesTypeWithoutDynamicService() {
        CaptchaProperties properties = new CaptchaProperties();
        properties.setType("SLIDE");
        assertEquals(CaptchaType.SLIDE, new CaptchaTypeResolver(properties, null).resolve());
    }

    @Test
    void dynamicValueWinsOverProperties() {
        CaptchaProperties properties = new CaptchaProperties();
        properties.setType("SLIDE");
        DynamicConfigService service = mock(DynamicConfigService.class);
        when(service.getString(eq(CaptchaTypeResolver.CONFIG_KEY), isNull())).thenReturn("CLICK");
        assertEquals(CaptchaType.CLICK, new CaptchaTypeResolver(properties, service).resolve());
    }

    @Test
    void invalidDynamicValueFallsBackToProperties() {
        CaptchaProperties properties = new CaptchaProperties();
        properties.setType("CHAR_NUMBER");
        DynamicConfigService service = mock(DynamicConfigService.class);
        when(service.getString(eq(CaptchaTypeResolver.CONFIG_KEY), isNull())).thenReturn("不存在");
        assertEquals(CaptchaType.CHAR_NUMBER, new CaptchaTypeResolver(properties, service).resolve());
    }

    @Test
    void fullyInvalidConfigurationFallsBackToCharAlnum() {
        CaptchaProperties properties = new CaptchaProperties();
        properties.setType("不存在");
        DynamicConfigService service = mock(DynamicConfigService.class);
        when(service.getString(eq(CaptchaTypeResolver.CONFIG_KEY), isNull())).thenReturn("  ");
        assertEquals(CaptchaType.CHAR_ALNUM, new CaptchaTypeResolver(properties, service).resolve());
    }
}
