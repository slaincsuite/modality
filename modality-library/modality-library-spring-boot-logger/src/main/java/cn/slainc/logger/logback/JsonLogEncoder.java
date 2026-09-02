package cn.slainc.logger.logback;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.encoder.EncoderBase;
import cn.slainc.core.constant.SystemConstants;
import cn.slainc.logger.autoconfigure.LoggerProperties;
import cn.slainc.logger.constant.LogMdcKeys;
import cn.slainc.logger.mask.LogMasker;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class JsonLogEncoder extends EncoderBase<ILoggingEvent> {

    private static final DateTimeFormatter TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneId.of(SystemConstants.DEFAULT_ZONE_NAME));

    private final LoggerProperties properties;

    private final LogMasker masker;

    public JsonLogEncoder(LoggerProperties properties, LogMasker masker) {
        this.properties = properties;
        this.masker = masker;
    }

    @Override
    public byte[] headerBytes() {
        return null;
    }

    @Override
    public byte[] footerBytes() {
        return null;
    }

    @Override
    public byte[] encode(ILoggingEvent event) {
        StringBuilder builder = new StringBuilder(320);
        appendEvent(builder, event, properties, masker);
        builder.append('\n');
        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }

    public String encodeToString(ILoggingEvent event) {
        StringBuilder builder = new StringBuilder(320);
        appendEvent(builder, event, properties, masker);
        return builder.toString();
    }

    private static void appendEvent(StringBuilder builder, ILoggingEvent event, LoggerProperties properties,
                                    LogMasker masker) {
        builder.append('{');
        field(builder, "timestamp", TIMESTAMP_FORMATTER.format(Instant.ofEpochMilli(event.getTimeStamp())));
        field(builder, "level", event.getLevel().toString());
        field(builder, "logger", event.getLoggerName());
        if (properties.getJson().isIncludeThread()) {
            field(builder, "thread", event.getThreadName());
        }
        int maxMessageLength = properties.getJson().getMaxMessageLength();
        String message = safeMessage(event);
        if (masker != null) {
            message = masker.maskMessage(message);
        }
        field(builder, "message", truncate(message, maxMessageLength));

        // 这里必须用 event 自带的 MDC 快照：AsyncAppender 在生产者线程已调用 prepareForDeferredProcessing 固化
        Map<String, String> mdc = event.getMDCPropertyMap();
        if (mdc == null) {
            mdc = Map.of();
        }
        field(builder, "traceId", mdc.get(SystemConstants.TRACE_ID_MDC_KEY));
        field(builder, "spanId", mdc.get(LogMdcKeys.SPAN_ID));
        field(builder, "parentSpanId", mdc.get(LogMdcKeys.PARENT_SPAN_ID));
        field(builder, "userId", mdc.get(SystemConstants.USER_ID_MDC_KEY));
        field(builder, "tenantId", mdc.get(SystemConstants.TENANT_ID_MDC_KEY));
        field(builder, "loginId", mdc.get(LogMdcKeys.LOGIN_ID));
        field(builder, "username", mdc.get(LogMdcKeys.USERNAME));
        field(builder, "clientIp", mdc.get(LogMdcKeys.CLIENT_IP));
        field(builder, "userAgent", mdc.get(LogMdcKeys.USER_AGENT));
        field(builder, "deviceId", mdc.get(LogMdcKeys.DEVICE_ID));
        field(builder, "httpMethod", mdc.get(LogMdcKeys.HTTP_METHOD));
        field(builder, "requestPath", mdc.get(LogMdcKeys.REQUEST_PATH));
        field(builder, "auditCategory", mdc.get(LogMdcKeys.AUDIT_CATEGORY));
        field(builder, "auditLevel", mdc.get(LogMdcKeys.AUDIT_LEVEL));

        if (properties.getJson().isIncludeException()) {
            appendException(builder, event, properties, masker);
        }
        if (properties.getJson().isIncludeMdcFields()) {
            appendCustomMdc(builder, mdc, masker);
        }
        builder.append('}');
    }

    private static void appendException(StringBuilder builder, ILoggingEvent event, LoggerProperties properties,
                                        LogMasker masker) {
        IThrowableProxy throwableProxy = event.getThrowableProxy();
        if (throwableProxy == null) {
            return;
        }
        builder.append(",\"exception\":{");
        field(builder, "class", throwableProxy.getClassName());
        field(builder, "rootCause", StackTraceRenderer.rootCauseClassName(throwableProxy));
        String message = throwableProxy.getMessage();
        field(builder, "message", masker == null ? message : masker.maskMessage(message));
        List<String> stackTrace = StackTraceRenderer.render(throwableProxy, properties.getJson().getMaxStackDepth());
        builder.append(",\"stackTrace\":[");
        for (int i = 0; i < stackTrace.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            String line = stackTrace.get(i);
            writeString(builder, masker == null ? line : masker.maskText(line));
        }
        builder.append(']');
        builder.append('}');
    }

    private static void appendCustomMdc(StringBuilder builder, Map<String, String> mdc, LogMasker masker) {
        Map<String, String> custom = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : mdc.entrySet()) {
            if (LogMdcKeys.isReserved(entry.getKey())) {
                continue;
            }
            custom.put(entry.getKey(), masker == null ? entry.getValue() : masker.maskText(entry.getValue()));
        }
        if (custom.isEmpty()) {
            return;
        }
        builder.append(",\"mdc\":{");
        boolean first = true;
        for (Map.Entry<String, String> entry : custom.entrySet()) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            writeString(builder, entry.getKey());
            builder.append(':');
            writeString(builder, entry.getValue());
        }
        builder.append('}');
    }

    private static String safeMessage(ILoggingEvent event) {
        try {
            String message = event.getFormattedMessage();
            return message == null ? "" : message;
        } catch (RuntimeException ex) {
            return "日志消息格式化失败：" + ex.getClass().getSimpleName();
        }
    }

    private static String truncate(String text, int maxLength) {
        if (text == null || maxLength <= 0 || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...(已截断，共 " + text.length() + " 字符)";
    }

    private static void field(StringBuilder builder, String name, String value) {
        if (value == null) {
            return;
        }
        if (builder.charAt(builder.length() - 1) != '{') {
            builder.append(',');
        }
        writeString(builder, name);
        builder.append(':');
        writeString(builder, value);
    }

    static void writeString(StringBuilder builder, String value) {
        builder.append('"');
        if (value != null) {
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                switch (c) {
                    case '"' -> builder.append("\\\"");
                    case '\\' -> builder.append("\\\\");
                    case '\n' -> builder.append("\\n");
                    case '\r' -> builder.append("\\r");
                    case '\t' -> builder.append("\\t");
                    case '\b' -> builder.append("\\b");
                    case '\f' -> builder.append("\\f");
                    default -> {
                        if (c < 0x20) {
                            builder.append(String.format("\\u%04x", (int) c));
                        } else {
                            builder.append(c);
                        }
                    }
                }
            }
        }
        builder.append('"');
    }
}
