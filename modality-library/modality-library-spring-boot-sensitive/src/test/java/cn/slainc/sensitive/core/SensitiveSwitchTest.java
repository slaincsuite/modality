package cn.slainc.sensitive.core;

import cn.slainc.core.config.DynamicConfigService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SensitiveSwitchTest {

    @AfterEach
    void tearDown() {
        SensitiveContext.clear();
    }

    @Test
    void followsPropertyWhenDynamicConfigAbsent() {
        assertTrue(new SensitiveSwitch(true, () -> null).isEnabled());
        assertFalse(new SensitiveSwitch(false, () -> null).isEnabled());
    }

    @Test
    void contextOverridesProperty() {
        SensitiveSwitch sensitiveSwitch = new SensitiveSwitch(true, () -> null);
        SensitiveContext.disable();
        assertFalse(sensitiveSwitch.isEnabled());
        SensitiveContext.enable();
        assertTrue(sensitiveSwitch.isEnabled());
    }

    @Test
    void dynamicConfigOverridesProperty() {
        DynamicConfigService service = Mockito.mock(DynamicConfigService.class);
        Mockito.when(service.getBoolean(SensitiveSwitch.DYNAMIC_KEY, true)).thenReturn(false);
        assertFalse(new SensitiveSwitch(true, () -> service).isEnabled());
        Mockito.when(service.getBoolean(SensitiveSwitch.DYNAMIC_KEY, true)).thenReturn(true);
        assertTrue(new SensitiveSwitch(true, () -> service).isEnabled());
    }

    @Test
    void fallsBackToDefaultWhenDynamicConfigThrows() {
        DynamicConfigService service = Mockito.mock(DynamicConfigService.class);
        Mockito.when(service.getBoolean(SensitiveSwitch.DYNAMIC_KEY, true))
                .thenThrow(new IllegalStateException("Redis 不可用"));
        assertTrue(new SensitiveSwitch(true, () -> service).isEnabled());
        assertFalse(new SensitiveSwitch(false, () -> service).isEnabled());
    }

    @Test
    void contextWinsOverDynamicConfig() {
        DynamicConfigService service = Mockito.mock(DynamicConfigService.class);
        Mockito.when(service.getBoolean(SensitiveSwitch.DYNAMIC_KEY, true)).thenReturn(true);
        SensitiveContext.disable();
        assertFalse(new SensitiveSwitch(true, () -> service).isEnabled());
    }
}
