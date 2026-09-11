package cn.slainc.captcha;

import cn.slainc.captcha.store.CaptchaStore;
import cn.slainc.core.exception.SystemException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
class CaptchaServiceStoreFailureTest {

    private final CaptchaStore store = mock(CaptchaStore.class);

    private final CaptchaMetrics metrics = new CaptchaMetrics();

    private CaptchaService service() {
        return new CaptchaService(store, new CaptchaProperties(),
                new CaptchaTypeResolver(new CaptchaProperties(), null), metrics, null, null);
    }

    @Test
    void storeFailureOnGenerateThrowsSystemException() {
        doThrow(new QueryTimeoutException("存储超时")).when(store)
                .put(anyString(), anyString(), any(Duration.class));
        SystemException exception = assertThrows(SystemException.class,
                () -> service().generate("WEB", CaptchaType.CHAR_ALNUM, "1.2.3.4"));
        assertEquals("验证码存储服务异常", exception.getMessage());
    }

    @Test
    void storeFailureOnVerifyThrowsSystemException() {
        when(store.isLocked(anyString())).thenThrow(new QueryTimeoutException("存储超时"));
        SystemException exception = assertThrows(SystemException.class,
                () -> service().verify("WEB", "some-id", CaptchaInput.ofCode("1234")));
        assertEquals("验证码存储服务异常", exception.getMessage());
    }

    @Test
    void missingStoreRejectedWithChineseMessage() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new CaptchaService(null, new CaptchaProperties(),
                        new CaptchaTypeResolver(new CaptchaProperties(), null), metrics, null, null));
        assertEquals("验证码存储不能为空", exception.getMessage());
    }
}
