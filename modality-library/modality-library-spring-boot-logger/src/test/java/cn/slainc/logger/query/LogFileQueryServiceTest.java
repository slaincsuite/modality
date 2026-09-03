package cn.slainc.logger.query;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import cn.slainc.logger.autoconfigure.LoggerProperties;
import cn.slainc.logger.logback.LogbackConfigurer;
import cn.slainc.logger.logback.LogbackLevelManager;
import cn.slainc.logger.mask.LogMasker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.MDC;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogFileQueryServiceTest {

    private static final String LOGGER_NAME = "cn.slainc.logger.query.probe";

    @TempDir
    Path tempDir;

    private LoggerProperties properties;

    private LogbackConfigurer configurer;

    private Logger logger;

    @BeforeEach
    void setUp() throws IOException {
        properties = new LoggerProperties();
        properties.getOutput().setDir(tempDir.toString());
        properties.getOutput().setFileName("modality.log");
        properties.getOutput().setImmediateFlush(true);
        properties.getJson().setAttachToRoot(false);
        properties.getAsync().setEnabled(false);
        configurer = new LogbackConfigurer(properties, LogMasker.of(properties), null, null);
        configurer.configure();
        LoggerContext context = LogbackLevelManager.currentContext();
        logger = context.getLogger(LOGGER_NAME);
        logger.setLevel(Level.INFO);
        logger.addAppender(configurer.jsonAppender());
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
        configurer.destroy();
        logger.detachAndStopAllAppenders();
        logger.setLevel(null);
    }

    private void produce() {
        MDC.put("traceId", "trace-query-1");
        MDC.put("userId", "1001");
        MDC.put("tenantId", "9");
        logger.info("订单创建成功 orderId=A1");
        logger.warn("库存偏低 orderId=A2");
        logger.error("支付失败 orderId=A3", new IllegalStateException("渠道超时"));
        MDC.put("traceId", "trace-query-2");
        MDC.put("tenantId", "10");
        logger.info("订单创建成功 orderId=B1");
        MDC.clear();
    }

    private LogFileQueryService service() {
        return new LogFileQueryService(properties.getQuery(), tempDir, tempDir.resolve("archive"));
    }

    @Test
    void queriesByLevelKeywordAndTimeRange() {
        produce();
        LogFileQueryService service = service();

        assertEquals(4, service.query(new LogQuery()).size());
        assertEquals(1, service.query(new LogQuery().addLevel("ERROR")).size());
        assertEquals(2, service.query(new LogQuery().addLevel("WARN").addLevel("ERROR")).size());
        assertEquals(2, service.query(new LogQuery().setKeyword("订单创建成功")).size());
        assertEquals(1, service.query(new LogQuery().setKeyword("A3")).size());
        assertEquals(1, service.query(new LogQuery().setTraceId("trace-query-2")).size());
        assertEquals(1, service.query(new LogQuery().setTenantId(10L).setKeyword("订单创建成功")).size());
        assertEquals(2, service.query(new LogQuery().setLoggerPrefix("cn.slainc.logger.query")
                .addLevel("INFO")).size());

        Instant now = Instant.now();
        assertEquals(4, service.query(new LogQuery()
                .setFrom(now.minus(5, ChronoUnit.MINUTES)).setTo(now.plus(5, ChronoUnit.MINUTES))).size());
        assertTrue(service.query(new LogQuery().setTo(now.minus(5, ChronoUnit.MINUTES))).isEmpty());

        List<LogEntry> limited = service.query(new LogQuery().setLimit(2));
        assertEquals(2, limited.size());
        assertEquals("WARN", service.query(new LogQuery().addLevel("WARN")).get(0).level());
    }

    @Test
    void entryCarriesExceptionAndTraceFields() {
        produce();
        LogEntry entry = service().query(new LogQuery().addLevel("ERROR")).get(0);
        assertEquals("java.lang.IllegalStateException", entry.exceptionClass());
        assertEquals("渠道超时", entry.exceptionMessage());
        assertEquals("trace-query-1", entry.traceId());
        assertEquals("1001", entry.userId());
        assertEquals(LOGGER_NAME, entry.logger());
        assertEquals("modality.log", entry.source());
    }

    @Test
    void exportsCsvWithHeaderAndEscapedContent() throws IOException {
        produce();
        Path csv = tempDir.resolve("export").resolve("logs.csv");
        int exported = service().exportCsv(new LogQuery().setKeyword("订单创建成功"), csv);

        assertEquals(2, exported);
        String content = Files.readString(csv, StandardCharsets.UTF_8);
        assertTrue(content.startsWith("\uFEFFtimestamp,level,logger,thread,traceId,spanId,userId,tenantId,message,"),
                "CSV 必须带表头与 BOM");
        assertTrue(content.contains("订单创建成功"));
        assertTrue(content.contains(",1001,9,"));
        assertEquals(3, content.split("\n").length);
    }

    @Test
    void csvEscapesCommaAndQuote() {
        assertEquals("\"a,b\"", LogFileQueryService.csv("a,b"));
        assertEquals("\"说\"\"到\"", LogFileQueryService.csv("说\"到"));
        assertEquals("纯文本", LogFileQueryService.csv("纯文本"));
    }
}
