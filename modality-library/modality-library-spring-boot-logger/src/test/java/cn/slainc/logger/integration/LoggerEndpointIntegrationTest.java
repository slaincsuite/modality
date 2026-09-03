package cn.slainc.logger.integration;

import ch.qos.logback.classic.LoggerContext;
import cn.slainc.core.config.DynamicConfigService;
import cn.slainc.core.context.TenantContext;
import cn.slainc.core.context.UserContext;
import cn.slainc.logger.audit.AuditCategory;
import cn.slainc.logger.audit.AuditLevel;
import cn.slainc.logger.audit.AuditLog;
import cn.slainc.logger.audit.AuditLogStore;
import cn.slainc.logger.audit.AuditOperation;
import cn.slainc.logger.audit.AuditRecord;
import cn.slainc.logger.autoconfigure.LoggerProperties;
import cn.slainc.logger.logback.LogbackConfigurer;
import cn.slainc.logger.logback.LogbackLevelManager;
import cn.slainc.logger.mask.LogMasker;
import cn.slainc.logger.monitor.InMemoryLogMetrics;
import cn.slainc.logger.monitor.LogMetricsSnapshot;
import cn.slainc.logger.query.LogFileQueryService;
import cn.slainc.logger.query.LogQuery;
import cn.slainc.logger.support.InMemoryDynamicConfigService;
import cn.slainc.utils.json.JsonUtils;
import cn.slainc.web.filter.FilterOrder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        classes = {LoggerEndpointIntegrationTest.TestApplication.class,
                LoggerEndpointIntegrationTest.DemoController.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.autoconfigure.exclude=org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration,"
                        + "cn.slainc.redis.autoconfigure.RedisAutoConfiguration,"
                        + "cn.slainc.redis.autoconfigure.RedisConfigAutoConfiguration,"
                        + "cn.slainc.redis.autoconfigure.RedisCacheAutoConfiguration,"
                        + "cn.slainc.redis.autoconfigure.RedisLockAutoConfiguration,"
                        + "cn.slainc.redis.autoconfigure.RedisRateLimitAutoConfiguration,"
                        + "cn.slainc.redis.autoconfigure.RedisPubSubAutoConfiguration,"
                        + "cn.slainc.redis.autoconfigure.RedisStructureAutoConfiguration,"
                        + "cn.slainc.redis.autoconfigure.RedisSaTokenAutoConfiguration,"
                        + "cn.slainc.redis.autoconfigure.RedisMonitorAutoConfiguration,"
                        + "cn.slainc.redis.autoconfigure.RedisJdbcConfigAutoConfiguration",
                "modality.logger.audit.async=false",
                "modality.logger.monitor.error-count-threshold=100000"})
class LoggerEndpointIntegrationTest {

    private static final Path LOG_DIR;

    static {
        try {
            LOG_DIR = Files.createTempDirectory("modality-logger-e2e");
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("modality.logger.output.dir", LOG_DIR::toString);
        registry.add("modality.logger.output.immediate-flush", () -> true);
        registry.add("modality.logger.async.enabled", () -> false);
        registry.add("modality.logger.json.attach-to-root", () -> true);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {

        @Bean
        DynamicConfigService dynamicConfigService() {
            return new InMemoryDynamicConfigService()
                    .putGroup("logger-level", "cn.slainc.logger.e2e", "DEBUG")
                    .put("modality.logger.sample.rate", "1.0");
        }

        @Bean
        CollectingAuditStore collectingAuditStore() {
            return new CollectingAuditStore();
        }

        @Bean
        DemoAuditService demoAuditService() {
            return new DemoAuditService();
        }

        @Bean
        FilterRegistrationBean<DemoContextFilter> demoContextFilter() {
            FilterRegistrationBean<DemoContextFilter> registration =
                    new FilterRegistrationBean<>(new DemoContextFilter());
            registration.setOrder(FilterOrder.SA_TOKEN);
            registration.addUrlPatterns("/*");
            return registration;
        }

        @Bean
        FilterRegistrationBean<MdcProbeFilter> mdcProbeFilter() {
            FilterRegistrationBean<MdcProbeFilter> registration =
                    new FilterRegistrationBean<>(new MdcProbeFilter());
            registration.setOrder(FilterOrder.TRACE - 100);
            registration.addUrlPatterns("/*");
            return registration;
        }
    }

    static class CollectingAuditStore implements AuditLogStore {

        final List<AuditRecord> records = new CopyOnWriteArrayList<>();

        @Override
        public void save(AuditRecord record) {
            records.add(record);
        }
    }

    static class DemoAuditService {

        @AuditLog(value = "创建订单", operation = AuditOperation.CREATE, module = "订单中心",
                recordParams = true, recordResult = true)
        public String create(String phone, String password) {
            return "ORDER-E2E-1";
        }
    }

    static class DemoContextFilter extends OncePerRequestFilter implements Ordered {

        @Override
        public int getOrder() {
            return FilterOrder.SA_TOKEN;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                        FilterChain filterChain) throws ServletException, IOException {
            UserContext.set(UserContext.UserInfo.of(1001L, "alice", null));
            TenantContext.set(9L);
            try {
                filterChain.doFilter(request, response);
            } finally {
                UserContext.clear();
                TenantContext.clear();
            }
        }
    }

    static class MdcProbeFilter extends OncePerRequestFilter implements Ordered {

        static final AtomicReference<Map<String, String>> MDC_AFTER_REQUEST = new AtomicReference<>();

        @Override
        public int getOrder() {
            return FilterOrder.TRACE - 100;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                        FilterChain filterChain) throws ServletException, IOException {
            try {
                filterChain.doFilter(request, response);
            } finally {
                MDC_AFTER_REQUEST.set(MDC.getCopyOfContextMap());
            }
        }
    }

    @RestController
    @RequestMapping("/demo")
    static class DemoController {

        private static final Logger log = LoggerFactory.getLogger("cn.slainc.logger.e2e");

        private final DemoAuditService auditService;

        DemoController(DemoAuditService auditService) {
            this.auditService = auditService;
        }

        @GetMapping("/echo")
        public Map<String, Object> echo() {
            log.info("端到端日志 手机号=13800138000 密码=p@ssw0rd");
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("ok", true);
            data.put("userId", UserContext.userId());
            data.put("tenantId", TenantContext.get());
            return data;
        }

        @GetMapping("/audit")
        public Map<String, Object> audit() {
            String orderId = auditService.create("13800138000", "p@ssw0rd");
            return Map.of("orderId", orderId);
        }
    }

    @LocalServerPort
    private int port;

    @Autowired
    private org.springframework.context.ApplicationContext applicationContext;

    @Autowired
    private CollectingAuditStore auditStore;

    @Autowired
    private LogbackLevelManager levelManager;

    @Autowired
    private LogbackConfigurer logbackConfigurer;

    @Autowired
    private LogFileQueryService queryService;

    @Test
    void autoConfigurationRegistersAllBeans() {
        assertNotNull(applicationContext.getBean(LogMasker.class));
        assertNotNull(applicationContext.getBean(LoggerProperties.class));
        assertNotNull(applicationContext.getBean(LogbackConfigurer.class));
        assertNotNull(applicationContext.getBean(LogbackLevelManager.class));
        assertNotNull(applicationContext.getBean(LogFileQueryService.class));
        assertNotNull(applicationContext.getBean(InMemoryLogMetrics.class));
        assertEquals("modality.log", logbackConfigurer.logFile().getFileName().toString());
    }

    @Test
    void writesJsonLogWithRequestContextAndClearsMdcAfterRequest() throws Exception {
        HttpResponse<String> first = get("/demo/echo", Map.of("X-Trace-Id", "trace-e2e-1",
                "X-Span-Id", "upstream-span-e2e", "X-Device-Id", "device-e2e"));
        assertEquals(200, first.statusCode());
        assertNotNull(first.headers().firstValue("X-Span-Id").orElse(null), "响应必须回写 SpanId");

        HttpResponse<String> second = get("/demo/echo", Map.of("X-Trace-Id", "trace-e2e-2"));
        assertEquals(200, second.statusCode());

        Map<String, String> mdcAfterRequest = MdcProbeFilter.MDC_AFTER_REQUEST.get();
        assertTrue(mdcAfterRequest == null || mdcAfterRequest.isEmpty(),
                "真实容器里请求结束后 MDC 必须为空，实际残留：" + mdcAfterRequest);

        List<JsonNode> events = readLogEvents();
        JsonNode firstEvent = findByTraceId(events, "trace-e2e-1");
        assertNotNull(firstEvent, "必须抓到 trace-e2e-1 的 JSON 日志");
        assertEquals("INFO", firstEvent.get("level").asString());
        assertEquals("cn.slainc.logger.e2e", firstEvent.get("logger").asString());
        assertEquals("GET", firstEvent.get("httpMethod").asString());
        assertEquals("/demo/echo", firstEvent.get("requestPath").asString());
        assertEquals("device-e2e", firstEvent.get("deviceId").asString());
        assertEquals("upstream-span-e2e", firstEvent.get("parentSpanId").asString());
        assertNotNull(firstEvent.get("spanId"));
        assertEquals("1001", firstEvent.get("userId").asString());
        assertEquals("9", firstEvent.get("tenantId").asString());
        assertEquals("1001", firstEvent.get("loginId").asString());
        assertEquals("alice", firstEvent.get("username").asString());
        assertNotNull(firstEvent.get("clientIp"));
        assertFalse(firstEvent.get("message").asString().contains("13800138000"),
                "日志消息里的手机号必须脱敏");
        assertFalse(firstEvent.get("message").asString().contains("p@ssw0rd"),
                "日志消息里的密码必须脱敏");

        JsonNode secondEvent = findByTraceId(events, "trace-e2e-2");
        assertNotNull(secondEvent);
        assertFalse(secondEvent.has("deviceId"), "第二次请求不应残留第一次的 deviceId");
        assertFalse(secondEvent.has("parentSpanId"), "第二次请求不应残留第一次的 parentSpanId");
    }

    @Test
    void dynamicLevelChangeTakesEffectThroughDynamicConfigService() {
        assertEquals("DEBUG", levelManager.getLevel("cn.slainc.logger.e2e"),
                "启动时应已按动态配置把 logger 调成 DEBUG");
        assertTrue(levelManager.setLevel("cn.slainc.logger.e2e", "WARN"));
        assertEquals("WARN", levelManager.getLevel("cn.slainc.logger.e2e"));
        assertTrue(levelManager.setLevel("cn.slainc.logger.e2e", "DEBUG"));
        assertEquals(1, levelManager.refresh(), "刷新应重新应用 1 条动态级别配置");
    }

    @Test
    void auditLogIsCollectedThroughSpringProxy() throws Exception {
        HttpResponse<String> response = get("/demo/audit", Map.of("X-Trace-Id", "trace-e2e-audit"));
        assertEquals(200, response.statusCode());

        assertEquals(1, auditStore.records.size(), "Spring 代理下 @AuditLog 必须被采集");
        AuditRecord record = auditStore.records.get(0);
        assertEquals("创建订单", record.description());
        assertEquals(AuditCategory.DATA, record.category());
        assertEquals(AuditLevel.INFO, record.level());
        assertEquals(1001L, record.userId());
        assertEquals("alice", record.username());
        assertEquals(9L, record.tenantId());
        assertEquals("trace-e2e-audit", record.traceId());
        assertTrue(record.costMillis() >= 0L);
        assertFalse(record.params().contains("13800138000"));
        assertTrue(record.params().contains("\"password\":\"******\""));
    }

    @Test
    void queryServiceReadsProducedJsonLogs() throws Exception {
        get("/demo/echo", Map.of("X-Trace-Id", "trace-e2e-query"));
        List<cn.slainc.logger.query.LogEntry> entries = queryService.query(
                new LogQuery().setTraceId("trace-e2e-query").addLevel("INFO"));
        assertFalse(entries.isEmpty(), "查询服务必须能读到本模块产出的 JSON 日志");
        assertEquals("/demo/echo", entries.get(0).fields().get("requestPath"));
    }

    @Test
    void metricsSnapshotReflectsProducedLogs() throws Exception {
        get("/demo/echo", Map.of("X-Trace-Id", "trace-e2e-metrics"));
        InMemoryLogMetrics metrics = applicationContext.getBean(InMemoryLogMetrics.class);
        LogMetricsSnapshot snapshot = metrics.snapshot();
        assertTrue(snapshot.total() > 0L, "日志量指标必须被采集");
        assertTrue(snapshot.levelCounts().containsKey("INFO"));
    }

    private HttpResponse<String> get(String path, Map<String, String> headers) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET();
        headers.forEach(builder::header);
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        }
    }

    private List<JsonNode> readLogEvents() throws IOException {
        Path file = logbackConfigurer.logFile();
        assertTrue(Files.exists(file), "JSON 日志文件不存在：" + file);
        List<JsonNode> events = new ArrayList<>();
        try (Stream<String> lines = Files.lines(file, StandardCharsets.UTF_8)) {
            for (String line : lines.toList()) {
                if (line.isBlank() || line.charAt(0) != '{') {
                    continue;
                }
                try {
                    events.add(JsonUtils.mapper().readTree(line));
                } catch (RuntimeException ignored) {
                    // 非 JSON 行来自宿主原有 appender，忽略
                }
            }
        }
        events.sort(Comparator.comparing(node -> node.path("timestamp").asString("")));
        return events;
    }

    private JsonNode findByTraceId(List<JsonNode> events, String traceId) {
        return events.stream()
                .filter(node -> traceId.equals(node.path("traceId").asString(null)))
                .filter(node -> "cn.slainc.logger.e2e".equals(node.path("logger").asString(null)))
                .findFirst()
                .orElse(null);
    }
}
