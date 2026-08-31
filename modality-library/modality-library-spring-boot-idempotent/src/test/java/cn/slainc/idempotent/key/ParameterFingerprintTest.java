package cn.slainc.idempotent.key;

import cn.slainc.idempotent.annotation.Idempotent;
import cn.slainc.idempotent.annotation.IdempotentStrategy;
import cn.slainc.idempotent.support.IdempotentContext;
import cn.slainc.idempotent.support.IdempotentSettings;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ParameterFingerprintTest {

    private final ParameterFingerprint parameterFingerprint = new ParameterFingerprint();

    @Test
    void 字段顺序不同但内容相同的参数指纹一致() throws Exception {
        Method method = handleMethod();
        assertThat(fp(context(method, new Object[]{new OrderA("M-1", 10L)}, settings(List.of(), true, true))))
                .isEqualTo(fp(context(method, new Object[]{new OrderB(10L, "M-1")}, settings(List.of(), true, true))));
    }

    @Test
    void 嵌套对象字段顺序与Map顺序被规范化() throws Exception {
        Method method = handleMethod();
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("orderNo", "M-2");
        first.put("extra", Map.of("amount", 10L, "channel", "app"));
        Map<String, Object> second = new LinkedHashMap<>();
        second.put("extra", nestedReversed());
        second.put("orderNo", "M-2");
        assertThat(fp(context(method, new Object[]{first}, settings(List.of(), true, true))))
                .isEqualTo(fp(context(method, new Object[]{second}, settings(List.of(), true, true))));
    }

    @Test
    void 忽略字段后仅时间戳不同的参数指纹一致() throws Exception {
        Method method = handleMethod();
        IdempotentSettings settings = settings(List.of("timestamp"), true, true);
        assertThat(fp(context(method, new Object[]{new OrderRequest("M-3", 10L, 111L)}, settings)))
                .isEqualTo(fp(context(method, new Object[]{new OrderRequest("M-3", 10L, 999L)}, settings)));
    }

    @Test
    void 参数内容不同则指纹不同() throws Exception {
        Method method = handleMethod();
        IdempotentSettings settings = settings(List.of(), true, true);
        assertThat(fp(context(method, new Object[]{new OrderRequest("M-4", 10L, 1L)}, settings)))
                .isNotEqualTo(fp(context(method, new Object[]{new OrderRequest("M-5", 10L, 1L)}, settings)));
    }

    @Test
    void 用户与接口路径参与指纹() throws Exception {
        Method method = handleMethod();
        IdempotentSettings settings = settings(List.of(), true, true);
        Object[] args = {new OrderRequest("M-6", 10L, 1L)};
        String first = fp(context(method, args, settings, 1001L, "POST /order/create"));
        assertThat(first).isNotEqualTo(fp(context(method, args, settings, 1002L, "POST /order/create")));
        assertThat(first).isNotEqualTo(fp(context(method, args, settings, 1001L, "POST /order/other")));
        IdempotentSettings withoutPath = settings(List.of(), true, false);
        assertThat(fp(context(method, args, withoutPath, 1001L, "POST /order/other")))
                .isEqualTo(fp(context(method, args, withoutPath, 1001L, "POST /order/create")));
    }

    @Test
    void 方法不同则指纹不同() throws Exception {
        Method first = handleMethod();
        Method second = getClass().getDeclaredMethod("handleOther", Object.class);
        IdempotentSettings settings = settings(List.of(), true, true);
        Object[] args = {new OrderRequest("M-7", 10L, 1L)};
        assertThat(fp(context(first, args, settings))).isNotEqualTo(fp(context(second, args, settings)));
    }

    private Map<String, Object> nestedReversed() {
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("channel", "app");
        nested.put("amount", 10L);
        return nested;
    }

    private String fp(IdempotentContext context) {
        return parameterFingerprint.fingerprint(context);
    }

    private Method handleMethod() throws Exception {
        return getClass().getDeclaredMethod("handle", Object.class);
    }

    @SuppressWarnings("unused")
    private void handle(Object payload) {
    }

    @SuppressWarnings("unused")
    private void handleOther(Object payload) {
    }

    private IdempotentSettings settings(List<String> ignoreFields, boolean includeUserId, boolean includePath) {
        return new IdempotentSettings(true, IdempotentStrategy.PARAMETER, null, "重复", Duration.ofMinutes(5), 3,
                Duration.ofMinutes(5), ignoreFields, includeUserId, includePath, true, true, true);
    }

    private IdempotentContext context(Method method, Object[] args, IdempotentSettings settings) {
        return context(method, args, settings, 1001L, "POST /order/create");
    }

    private IdempotentContext context(Method method, Object[] args, IdempotentSettings settings, Long userId,
                                      String requestPath) {
        Idempotent annotation = method.getAnnotation(Idempotent.class);
        return new IdempotentContext(method, args, this, annotation,
                method.getDeclaringClass().getName() + "#" + method.getName(), requestPath, userId, null, null, settings,
                null);
    }

    private record OrderA(String orderNo, long amount) {
    }

    private record OrderB(long amount, String orderNo) {
    }

    @SuppressWarnings("unused")
    private record OrderRequest(String orderNo, long amount, long timestamp) {
    }
}
