package cn.slainc.logger.logback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import cn.slainc.core.constant.SystemConstants;
import cn.slainc.core.config.DynamicConfigService;
import cn.slainc.logger.autoconfigure.LoggerProperties;
import cn.slainc.logger.support.InMemoryDynamicConfigService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogbackLevelManagerTest {

    private static final String LOGGER_NAME = "cn.slainc.logger.level.probe";

    private final LoggerContext context = LogbackLevelManager.currentContext();

    private Logger logger;

    @AfterEach
    void tearDown() {
        MDC.clear();
        if (logger != null) {
            logger.setLevel(null);
            logger.detachAndStopAllAppenders();
        }
    }

    private ListAppender<ILoggingEvent> attachAppender() {
        assertNotNull(context);
        logger = context.getLogger(LOGGER_NAME);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.setContext(context);
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private LogbackLevelManager manager(DynamicConfigService service) {
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        if (service != null) {
            factory.registerSingleton("dynamicConfigService", service);
        }
        return new LogbackLevelManager(new LoggerProperties().getLevel(),
                factory.getBeanProvider(DynamicConfigService.class));
    }

    @Test
    void changesLoggerLevelAtRuntime() {
        ListAppender<ILoggingEvent> appender = attachAppender();
        LogbackLevelManager manager = manager(null);
        assertTrue(manager.setLevel(LOGGER_NAME, "INFO"));
        logger.debug("这条不该出现");
        assertEquals(0, appender.list.size(), "INFO 级别下 DEBUG 不应输出");

        assertTrue(manager.setLevel(LOGGER_NAME, "DEBUG"), "运行期改成 DEBUG 必须成功");
        assertEquals("DEBUG", manager.getLevel(LOGGER_NAME));
        logger.debug("这条必须出现");
        assertEquals(1, appender.list.size(), "改级别后 DEBUG 必须真的输出");
        assertEquals("这条必须出现", appender.list.get(0).getFormattedMessage());
    }

    @Test
    void refreshAppliesLevelsFromDynamicConfig() {
        ListAppender<ILoggingEvent> appender = attachAppender();
        InMemoryDynamicConfigService service = new InMemoryDynamicConfigService()
                .putGroup("logger-level", LOGGER_NAME, "DEBUG");
        LogbackLevelManager manager = manager(service);

        logger.debug("刷新前不该出现");
        assertEquals(0, appender.list.size());

        assertEquals(1, manager.refresh(), "应当应用 1 条动态级别配置");
        assertEquals("DEBUG", manager.getLevel(LOGGER_NAME));
        logger.debug("刷新后必须出现");
        assertEquals(1, appender.list.size());
    }

    @Test
    void refreshAppliesTenantScopedLevelAndTurboFilterEnforcesIt() {
        ListAppender<ILoggingEvent> appender = attachAppender();
        InMemoryDynamicConfigService service = new InMemoryDynamicConfigService()
                .putGroup("logger-tenant-level", "9", "ERROR");
        LogbackLevelManager manager = manager(service);
        assertEquals(1, manager.refresh());
        assertEquals(Level.ERROR, manager.levelForTenant(9L));

        TenantLevelTurboFilter filter = new TenantLevelTurboFilter(manager);
        filter.setContext(context);
        filter.start();
        context.addTurboFilter(filter);

        MDC.put(SystemConstants.TENANT_ID_MDC_KEY, "9");
        logger.info("租户 9 的 INFO 日志应被压制");
        logger.error("租户 9 的 ERROR 日志必须保留");
        assertEquals(1, appender.list.size(), "只应留下 ERROR");
        assertEquals("ERROR", appender.list.get(0).getLevel().toString());

        MDC.remove(SystemConstants.TENANT_ID_MDC_KEY);
        logger.info("其它租户不受影响");
        assertEquals(2, appender.list.size());

        context.getTurboFilterList().remove(filter);
        Map<Long, Level> tenants = manager.tenantLevels();
        assertTrue(tenants.containsKey(9L));
    }
}
