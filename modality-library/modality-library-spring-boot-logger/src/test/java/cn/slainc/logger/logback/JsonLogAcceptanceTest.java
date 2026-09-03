package cn.slainc.logger.logback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import cn.slainc.logger.autoconfigure.LoggerProperties;
import cn.slainc.logger.mask.LogMasker;
import cn.slainc.utils.json.JsonUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.MDC;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonLogAcceptanceTest {

    private static final String LOGGER_NAME = "cn.slainc.logger.acceptance";

    @TempDir
    Path tempDir;

    private LogbackConfigurer configurer;

    private Logger logger;

    @AfterEach
    void tearDown() {
        if (configurer != null) {
            configurer.destroy();
        }
        if (logger != null) {
            logger.detachAndStopAllAppenders();
            logger.setLevel(null);
        }
        MDC.clear();
    }

    private LogbackConfigurer configure(Path directory, boolean async) {
        LoggerProperties properties = new LoggerProperties();
        properties.getOutput().setDir(directory.toString());
        properties.getOutput().setFileName("modality.log");
        properties.getOutput().setRollingPattern("%d{yyyy-MM-dd}.%i.log.gz");
        properties.getOutput().setImmediateFlush(!async);
        properties.getJson().setAttachToRoot(false);
        properties.getAsync().setEnabled(async);
        properties.getAsync().setQueueSize(2048);
        LogbackConfigurer configurer = new LogbackConfigurer(properties, LogMasker.of(properties), null, null);
        configurer.configure();
        return configurer;
    }

    private void attach(LogbackConfigurer configurer) {
        LoggerContext context = LogbackLevelManager.currentContext();
        assertNotNull(context, "测试必须运行在 Logback 之上");
        logger = context.getLogger(LOGGER_NAME);
        logger.setLevel(Level.INFO);
        logger.addAppender(configurer.jsonAppender());
    }

    @Test
    void writesStructuredJsonWithTraceAndMdcFields() throws IOException {
        configurer = configure(tempDir, false);
        attach(configurer);
        MDC.put("traceId", "trace-acceptance-001");
        MDC.put("spanId", "span-001");
        MDC.put("parentSpanId", "span-root");
        MDC.put("userId", "1001");
        MDC.put("tenantId", "7");
        MDC.put("clientIp", "10.1.2.3");
        MDC.put("customBizField", "订单中心");
        logger.info("用户下单成功");
        configurer.destroy();
        MDC.clear();

        List<JsonNode> events = readEvents(configurer.logFile());
        assertEquals(1, events.size(), "应当只产出一条 JSON 日志");
        JsonNode event = events.get(0);
        assertTrue(event.has("timestamp"), "缺少 timestamp 字段");
        assertEquals("INFO", event.get("level").asString());
        assertEquals(LOGGER_NAME, event.get("logger").asString());
        assertEquals("用户下单成功", event.get("message").asString());
        assertEquals("trace-acceptance-001", event.get("traceId").asString());
        assertEquals("span-001", event.get("spanId").asString());
        assertEquals("span-root", event.get("parentSpanId").asString());
        assertEquals("1001", event.get("userId").asString());
        assertEquals("7", event.get("tenantId").asString());
        assertEquals("10.1.2.3", event.get("clientIp").asString());
        assertEquals("订单中心", event.get("mdc").get("customBizField").asString());
    }

    @Test
    void masksSensitiveFieldsInsideJsonMessage() throws IOException {
        configurer = configure(tempDir, false);
        attach(configurer);
        String payload = "{\"phone\":\"13800138000\",\"password\":\"p@ssw0rd\",\"idCard\":\"110101199003071234\","
                + "\"orderId\":\"A1001\",\"nested\":{\"token\":\"tok-abcdef\"}}";
        logger.info("下单请求 {}", payload);
        configurer.destroy();

        Path file = configurer.logFile();
        String raw = Files.readString(file, StandardCharsets.UTF_8);
        assertFalse(raw.contains("13800138000"), "日志原文中不应出现原始手机号");
        assertFalse(raw.contains("p@ssw0rd"), "日志原文中不应出现原始密码");
        assertFalse(raw.contains("110101199003071234"), "日志原文中不应出现原始身份证号");
        assertFalse(raw.contains("tok-abcdef"), "日志原文中不应出现原始 token");
        assertTrue(raw.contains("******"), "日志应出现掩码");

        List<JsonNode> events = readEvents(file);
        String message = events.get(0).get("message").asString();
        JsonNode maskedPayload = JsonUtils.readTree(message.substring(message.indexOf('{')));
        assertEquals("******", maskedPayload.get("phone").asString());
        assertEquals("******", maskedPayload.get("password").asString());
        assertEquals("******", maskedPayload.get("idCard").asString());
        assertEquals("A1001", maskedPayload.get("orderId").asString());
        assertEquals("******", maskedPayload.get("nested").get("token").asString());
    }

    @Test
    void masksSensitiveKeyValueInPlainText() throws IOException {
        configurer = configure(tempDir, false);
        attach(configurer);
        logger.info("登录参数 password=abc123 userPhone=13900139000 orderId=A9");
        configurer.destroy();

        String raw = Files.readString(configurer.logFile(), StandardCharsets.UTF_8);
        assertFalse(raw.contains("abc123"), "明文键值对中的密码必须脱敏");
        assertFalse(raw.contains("13900139000"), "明文键值对中的手机号必须脱敏");
        assertTrue(raw.contains("orderId=A9"), "非敏感字段必须原样保留");
        assertTrue(raw.contains("password=******"));
    }

    @Test
    void writesExceptionWithStackTrace() throws IOException {
        configurer = configure(tempDir, false);
        attach(configurer);
        Logger exceptionLogger = LogbackLevelManager.currentContext().getLogger("cn.slainc.logger.boom");
        exceptionLogger.setLevel(Level.INFO);
        exceptionLogger.addAppender(configurer.jsonAppender());
        exceptionLogger.error("调用下游失败", new IllegalStateException("连接超时 token=tok-xyz"));
        configurer.destroy();

        List<JsonNode> events = readEvents(configurer.logFile());
        assertEquals(1, events.size());
        JsonNode exception = events.get(0).get("exception");
        assertNotNull(exception, "异常日志必须带 exception 字段");
        assertEquals("java.lang.IllegalStateException", exception.get("class").asString());
        assertTrue(exception.get("stackTrace").size() > 3, "堆栈至少要有若干行");
        assertFalse(events.get(0).toString().contains("tok-xyz"), "异常消息里的 token 也要脱敏");
        assertTrue(events.get(0).toString().contains("******"));
    }

    @Test
    void asyncAppenderFlushesAllQueuedEvents() throws IOException {
        configurer = configure(tempDir, true);
        attach(configurer);
        for (int i = 0; i < 20; i++) {
            logger.info("异步日志第 {} 条", i);
        }
        assertTrue(configurer.jsonAppender() instanceof ch.qos.logback.classic.AsyncAppender,
                "开启异步时必须由 AsyncAppender 包装");
        configurer.destroy();

        List<JsonNode> events = readEvents(configurer.logFile());
        assertEquals(20, events.size(), "AsyncAppender 停止前必须排干队列，不能丢日志");
        for (JsonNode event : events) {
            assertEquals("INFO", event.get("level").asString());
        }
    }

    private List<JsonNode> readEvents(Path file) throws IOException {
        assertTrue(Files.exists(file), "日志文件不存在：" + file);
        List<JsonNode> events = new ArrayList<>();
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            if (line.isBlank()) {
                continue;
            }
            events.add(JsonUtils.mapper().readTree(line));
        }
        return events;
    }
}
