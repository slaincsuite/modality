package cn.slainc.logger.audit;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import cn.slainc.core.context.RequestContext;
import cn.slainc.core.context.TenantContext;
import cn.slainc.core.context.UserContext;
import cn.slainc.logger.autoconfigure.LoggerProperties;
import cn.slainc.logger.constant.LogMdcKeys;
import cn.slainc.logger.context.LoginIdResolver;
import cn.slainc.logger.logback.LogbackLevelManager;
import cn.slainc.logger.mask.LogMasker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuditLogAspectTest {

    static class CollectingStore implements AuditLogStore {

        final List<AuditRecord> records = new CopyOnWriteArrayList<>();

        @Override
        public void save(AuditRecord record) {
            records.add(record);
        }
    }

    static class OrderService {

        @AuditLog(value = "创建订单", operation = AuditOperation.CREATE, module = "订单中心",
                recordParams = true, recordResult = true)
        public String create(String phone, String password) {
            return "ORDER-1";
        }

        @AuditLog(value = "删除订单", operation = AuditOperation.DELETE, category = AuditCategory.SECURITY,
                level = AuditLevel.CRITICAL)
        public void remove(Long orderId) {
            throw new IllegalStateException("订单已锁定");
        }

        @AuditLog(value = "用户登录", operation = AuditOperation.LOGIN)
        public void login() {
        }

        @AuditLog(value = "修改配置", operation = AuditOperation.CONFIG)
        public void config() {
        }

        @AuditLog(value = "角色授权", operation = AuditOperation.GRANT)
        public void grant() {
        }
    }

    private final CollectingStore store = new CollectingStore();

    private OrderService service;

    private LoggerProperties properties;

    @BeforeEach
    void setUp() {
        properties = new LoggerProperties();
        properties.getAudit().setAsync(false);
        LogMasker masker = LogMasker.of(properties);
        AuditLogDispatcher dispatcher = new AuditLogDispatcher(properties.getAudit(), List.of(store));
        AuditLogAspect aspect = new AuditLogAspect(properties.getAudit(), dispatcher, masker, List.of(),
                LoginIdResolver.USER_CONTEXT);
        AspectJProxyFactory factory = new AspectJProxyFactory(new OrderService());
        factory.addAspect(aspect);
        service = factory.getProxy();

        RequestContext.set(new RequestContext.RequestInfo("trace-audit-1", "10.9.9.9", "Mozilla/5.0",
                "POST", "/api/orders"));
        UserContext.set(UserContext.UserInfo.of(1001L, "alice", null));
        TenantContext.set(9L);
        MDC.put(LogMdcKeys.SPAN_ID, "span-audit-1");
        MDC.put(LogMdcKeys.DEVICE_ID, "device-audit-1");
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
        RequestContext.clear();
        UserContext.clear();
        TenantContext.clear();
    }

    @Test
    void collectsOperatorCostCategoryAndLevelForNormalCall() {
        String result = service.create("13800138000", "p@ssw0rd");
        assertEquals("ORDER-1", result);
        assertEquals(1, store.records.size());
        AuditRecord record = store.records.get(0);
        assertEquals("创建订单", record.description());
        assertEquals("订单中心", record.module());
        assertEquals(AuditOperation.CREATE, record.operation());
        assertEquals(AuditCategory.DATA, record.category());
        assertEquals(AuditLevel.INFO, record.level());
        assertTrue(record.success());
        assertEquals(1001L, record.userId());
        assertEquals("1001", record.loginId());
        assertEquals("alice", record.username());
        assertEquals(9L, record.tenantId());
        assertEquals("10.9.9.9", record.clientIp());
        assertEquals("device-audit-1", record.deviceId());
        assertEquals("trace-audit-1", record.traceId());
        assertEquals("span-audit-1", record.spanId());
        assertEquals("/api/orders", record.requestPath());
        assertEquals("POST", record.httpMethod());
        assertTrue(record.costMillis() >= 0L, "必须采集耗时");
        assertTrue(record.target().endsWith("OrderService#create"));
        assertTrue(record.result().contains("ORDER-1"));
        assertNotNull(record.params());
        assertFalse(record.params().contains("13800138000"), "审计参数里的手机号必须脱敏：" + record.params());
        assertFalse(record.params().contains("p@ssw0rd"), "审计参数里的密码必须脱敏：" + record.params());
        assertTrue(record.params().contains("\"phone\":\"******\""));
        assertTrue(record.params().contains("\"password\":\"******\""));
    }

    @Test
    void recordsErrorLevelAuditAndRethrowsOriginalException() {
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> service.remove(1L));
        assertEquals("订单已锁定", error.getMessage(), "原异常必须原样抛给调用方");
        assertEquals(1, store.records.size());
        AuditRecord record = store.records.get(0);
        assertFalse(record.success());
        assertEquals(AuditLevel.ERROR, record.level(), "方法抛异常必须记 ERROR 级审计");
        assertEquals(AuditCategory.SECURITY, record.category(), "显式声明的分类必须保留");
        assertEquals("java.lang.IllegalStateException", record.exceptionClass());
        assertEquals("订单已锁定", record.exceptionMessage());
        assertTrue(record.costMillis() >= 0L);
    }

    @Test
    void derivesCategoryFromOperationType() {
        service.login();
        service.config();
        service.grant();
        assertEquals(3, store.records.size());
        assertEquals(AuditCategory.LOGIN, store.records.get(0).category());
        assertEquals(AuditCategory.SYSTEM, store.records.get(1).category());
        assertEquals(AuditCategory.PERMISSION, store.records.get(2).category());
    }

    @Test
    void slf4jStorePrintsAuditAsJson() {
        LoggerContext context = LogbackLevelManager.currentContext();
        Logger auditLogger = context.getLogger(Slf4jAuditLogStore.AUDIT_LOGGER_NAME);
        auditLogger.setLevel(Level.INFO);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.setContext(context);
        appender.start();
        auditLogger.addAppender(appender);
        try {
            service.create("13800138000", "p@ssw0rd");
            new Slf4jAuditLogStore().save(store.records.get(0));
            assertEquals(1, appender.list.size());
            String line = appender.list.get(0).getFormattedMessage();
            assertTrue(line.startsWith("{"), "审计打印必须是 JSON：" + line);
            assertTrue(line.contains("创建订单"));
            assertTrue(line.contains("\"level\":\"INFO\""));
            assertFalse(line.contains("13800138000"));
        } finally {
            auditLogger.detachAndStopAllAppenders();
            auditLogger.setLevel(null);
        }
    }
}
