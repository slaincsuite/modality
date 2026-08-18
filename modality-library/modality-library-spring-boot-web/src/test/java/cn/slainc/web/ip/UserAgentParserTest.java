package cn.slainc.web.ip;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserAgentParserTest {

    @Test
    void parsesChromeOnWindows() {
        UserAgent ua = UserAgentParser.parse(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
                        + "Chrome/120.0.0.0 Safari/537.36");
        assertEquals("Chrome", ua.browser());
        assertEquals("120.0.0.0", ua.browserVersion());
        assertEquals("Windows", ua.os());
        assertEquals(UserAgent.DeviceType.PC, ua.device());
        assertFalse(ua.isBot());
        assertTrue(ua.isPc());
    }

    @Test
    void parsesSafariOnIphone() {
        UserAgent ua = UserAgentParser.parse(
                "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 "
                        + "(KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1");
        assertEquals("Safari", ua.browser());
        assertEquals("iOS", ua.os());
        assertEquals(UserAgent.DeviceType.MOBILE, ua.device());
        assertTrue(ua.isMobile());
    }

    @Test
    void parsesTabletAndAndroid() {
        UserAgent ua = UserAgentParser.parse(
                "Mozilla/5.0 (Linux; Android 13; SM-T870) AppleWebKit/537.36 (KHTML, like Gecko) "
                        + "Chrome/120.0.0.0 Safari/537.36");
        assertEquals("Android", ua.os());
        assertEquals(UserAgent.DeviceType.TABLET, ua.device());
    }

    @Test
    void detectsSearchEngineBots() {
        UserAgent googlebot = UserAgentParser.parse(
                "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)");
        assertTrue(googlebot.isBot());
        assertEquals(UserAgent.DeviceType.BOT, googlebot.device());
        assertEquals("Googlebot", googlebot.browser());

        UserAgent baidu = UserAgentParser.parse(
                "Mozilla/5.0 (compatible; Baiduspider/2.0; +http://www.baidu.com/search/spider.html)");
        assertTrue(baidu.isBot());
    }

    @Test
    void handlesClientsAndUnknown() {
        assertEquals("curl", UserAgentParser.parse("curl/8.4.0").browser());
        assertTrue(UserAgentParser.parse("curl/8.4.0").isBot());
        assertEquals(UserAgent.UNKNOWN, UserAgentParser.parse(null));
        assertEquals(UserAgent.UNKNOWN, UserAgentParser.parse("   "));
        assertEquals("未知", UserAgentParser.parse("something-weird").browser());
    }
}
