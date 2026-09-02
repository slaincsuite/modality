package cn.slainc.logger.trace;

import cn.slainc.core.constant.HttpHeaders;
import cn.slainc.core.context.RequestContext;
import cn.slainc.logger.constant.LogHeaders;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;

public final class TraceHeaderPropagator {

    public static Map<String, String> currentHeaders() {
        Map<String, String> headers = new LinkedHashMap<>();
        inject(headers::put);
        return headers;
    }

    public static void inject(BiConsumer<String, String> setter) {
        put(setter, HttpHeaders.TRACE_ID, RequestContext.traceId());
        put(setter, LogHeaders.SPAN_ID, SpanScope.currentSpanId());
        put(setter, LogHeaders.PARENT_SPAN_ID, SpanScope.currentParentSpanId());
    }

    private static void put(BiConsumer<String, String> setter, String name, String value) {
        if (value != null && !value.isBlank()) {
            setter.accept(name, value);
        }
    }

    private TraceHeaderPropagator() {
    }
}
