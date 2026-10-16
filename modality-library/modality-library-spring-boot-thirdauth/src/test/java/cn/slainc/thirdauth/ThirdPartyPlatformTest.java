package cn.slainc.thirdauth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThirdPartyPlatformTest {

    @Test
    void feishuCodeParsedWithNormalization() {
        assertEquals(ThirdPartyPlatform.FEISHU, ThirdPartyPlatform.of("feishu"));
        assertEquals(ThirdPartyPlatform.FEISHU, ThirdPartyPlatform.of(" FEISHU "));
        assertEquals("feishu", ThirdPartyPlatform.FEISHU.code());
        assertEquals("飞书", ThirdPartyPlatform.FEISHU.label());
    }

    @Test
    void onlyFeishuRemains() {
        assertEquals(1, ThirdPartyPlatform.values().length);
        assertEquals("feishu", ThirdPartyPlatform.supported());
    }

    @Test
    void pausedPlatformsRejectedWithChineseError() {
        for (String code : new String[]{"wechat", "WECHAT_WORK", "wechat-work", "dingtalk", "twitter"}) {
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                    () -> ThirdPartyPlatform.of(code));
            assertTrue(exception.getMessage().contains("不支持的第三方平台"));
            assertTrue(exception.getMessage().contains("feishu"));
        }
    }
}
