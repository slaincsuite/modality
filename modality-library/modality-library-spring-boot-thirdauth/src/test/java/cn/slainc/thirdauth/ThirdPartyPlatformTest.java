package cn.slainc.thirdauth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ThirdPartyPlatformTest {

    @ParameterizedTest
    @CsvSource({"wechat,WECHAT", "WECHAT_WORK,WECHAT_WORK", "wechat-work,WECHAT_WORK",
            "feishu,FEISHU", "DingTalk,DINGTALK", "dingtalk,DINGTALK"})
    void platformCodeNormalizedParsing(String input, ThirdPartyPlatform expected) {
        assertEquals(expected, ThirdPartyPlatform.of(input));
    }

    @Test
    void unknownPlatformGivesChineseErrorWithSupportedList() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> ThirdPartyPlatform.of("twitter"));
        org.junit.jupiter.api.Assertions.assertTrue(exception.getMessage().contains("不支持的第三方平台"));
        org.junit.jupiter.api.Assertions.assertTrue(exception.getMessage().contains("wechat"));
        org.junit.jupiter.api.Assertions.assertTrue(exception.getMessage().contains("dingtalk"));
    }

    @Test
    void fourPlatformsHaveChineseLabels() {
        assertEquals("微信", ThirdPartyPlatform.WECHAT.label());
        assertEquals("企业微信", ThirdPartyPlatform.WECHAT_WORK.label());
        assertEquals("飞书", ThirdPartyPlatform.FEISHU.label());
        assertEquals("钉钉", ThirdPartyPlatform.DINGTALK.label());
    }
}
