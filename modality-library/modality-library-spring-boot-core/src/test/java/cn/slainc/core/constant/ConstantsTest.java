package cn.slainc.core.constant;

import cn.slainc.core.enums.DeleteStatus;
import cn.slainc.core.enums.EnabledStatus;
import cn.slainc.utils.string.RegexUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConstantsTest {

    @Test
    void cacheKeyPrefixes() {
        assertEquals("modality:", CacheKeys.PREFIX);
        assertTrue(CacheKeys.CONFIG.startsWith(CacheKeys.PREFIX));
        assertTrue(CacheKeys.TOKEN.startsWith(CacheKeys.PREFIX));
        assertEquals("modality:user:", CacheKeys.USER);
        assertEquals("modality:", CacheKeys.key());
        assertEquals("modality:user:100", CacheKeys.key("user", "100"));
        assertEquals("modality:a:b:c", CacheKeys.key("a", "b", "c"));
    }

    @Test
    void httpHeaderNames() {
        assertEquals("X-Trace-Id", HttpHeaders.TRACE_ID);
        assertEquals("X-Tenant-Id", HttpHeaders.TENANT_ID);
        assertEquals("Authorization", HttpHeaders.AUTHORIZATION);
        assertEquals("X-Api-Key", HttpHeaders.API_KEY);
        assertEquals("X-Sign", HttpHeaders.SIGN);
        assertEquals("X-Forwarded-For", HttpHeaders.FORWARDED_FOR);
        assertEquals("CF-Connecting-IP", HttpHeaders.CF_CONNECTING_IP);
        assertEquals("Bearer ", HttpHeaders.TOKEN_PREFIX);
    }

    @Test
    void systemConstantsAlignWithEnums() {
        assertEquals(EnabledStatus.ENABLED.value(), SystemConstants.ENABLED);
        assertEquals(EnabledStatus.DISABLED.value(), SystemConstants.DISABLED);
        assertEquals(DeleteStatus.NOT_DELETED.value(), SystemConstants.NOT_DELETED);
        assertEquals(DeleteStatus.DELETED.value(), SystemConstants.DELETED);
        assertEquals(1L, (long) SystemConstants.SUPER_ADMIN_ID);
        assertEquals("system", SystemConstants.SYSTEM_USER);
        assertEquals("Asia/Shanghai", SystemConstants.DEFAULT_ZONE_NAME);
        assertEquals("traceId", SystemConstants.TRACE_ID_MDC_KEY);
    }

    @Test
    void regexPatternsDelegateToUtils() {
        assertEquals(RegexUtils.MOBILE, RegexPatterns.MOBILE);
        assertEquals(RegexUtils.EMAIL, RegexPatterns.EMAIL);
        assertEquals(RegexUtils.ID_CARD, RegexPatterns.ID_CARD);
        assertTrue(RegexUtils.isMobile("13812345678"));
    }

    @Test
    void mimeTypes() {
        assertEquals("application/json", MimeTypes.JSON);
        assertEquals("application/octet-stream", MimeTypes.OCTET_STREAM);
        assertEquals("multipart/form-data", MimeTypes.MULTIPART_FORM_DATA);
        assertTrue(MimeTypes.XLSX.contains("spreadsheetml"));
    }
}
