package cn.slainc.logger.trace;

import cn.slainc.logger.constant.LogMdcKeys;
import org.slf4j.MDC;

public final class SpanScope implements AutoCloseable {

    private final String spanId;

    private final String parentSpanId;

    private final String previousSpanId;

    private final String previousParentSpanId;

    private SpanScope(String spanId, String parentSpanId, String previousSpanId, String previousParentSpanId) {
        this.spanId = spanId;
        this.parentSpanId = parentSpanId;
        this.previousSpanId = previousSpanId;
        this.previousParentSpanId = previousParentSpanId;
    }

    public static SpanScope open(String spanId, String parentSpanId) {
        String previousSpanId = MDC.get(LogMdcKeys.SPAN_ID);
        String previousParentSpanId = MDC.get(LogMdcKeys.PARENT_SPAN_ID);
        put(LogMdcKeys.SPAN_ID, spanId);
        put(LogMdcKeys.PARENT_SPAN_ID, parentSpanId);
        return new SpanScope(spanId, parentSpanId, previousSpanId, previousParentSpanId);
    }

    public static SpanScope start(SpanIdGenerator generator) {
        String parent = currentSpanId();
        return open(generator.generate(), parent);
    }

    public static String currentSpanId() {
        return MDC.get(LogMdcKeys.SPAN_ID);
    }

    public static String currentParentSpanId() {
        return MDC.get(LogMdcKeys.PARENT_SPAN_ID);
    }

    public String spanId() {
        return spanId;
    }

    public String parentSpanId() {
        return parentSpanId;
    }

    @Override
    public void close() {
        restore(LogMdcKeys.SPAN_ID, previousSpanId);
        restore(LogMdcKeys.PARENT_SPAN_ID, previousParentSpanId);
    }

    private static void put(String key, String value) {
        if (value == null) {
            MDC.remove(key);
        } else {
            MDC.put(key, value);
        }
    }

    private static void restore(String key, String value) {
        put(key, value);
    }
}
