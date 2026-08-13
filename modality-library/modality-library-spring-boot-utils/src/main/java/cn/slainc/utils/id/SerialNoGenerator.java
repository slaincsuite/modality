package cn.slainc.utils.id;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class SerialNoGenerator {

    private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final String prefix;
    private final int digits;
    private final Map<String, AtomicLong> counters = new ConcurrentHashMap<>();

    public SerialNoGenerator(String prefix, int digits) {
        if (digits <= 0 || digits > 18) {
            throw new IllegalArgumentException("序列号位数必须在 1 到 18 之间");
        }
        this.prefix = prefix == null ? "" : prefix;
        this.digits = digits;
    }

    public String nextDaily() {
        return nextDaily(LocalDate.now());
    }

    public String nextDaily(LocalDate date) {
        LocalDate actual = date == null ? LocalDate.now() : date;
        String day = actual.format(DAY_FORMATTER);
        long value = counters.computeIfAbsent(day, key -> new AtomicLong(0L)).incrementAndGet();
        return prefix + day + pad(value);
    }

    public String next(String businessKey) {
        String key = businessKey == null || businessKey.isBlank() ? "默认" : businessKey;
        long value = counters.computeIfAbsent(key, ignored -> new AtomicLong(0L)).incrementAndGet();
        return prefix + key + pad(value);
    }

    public void reset() {
        counters.clear();
    }

    private String pad(long value) {
        String text = Long.toString(value);
        if (text.length() >= digits) {
            return text;
        }
        return "0".repeat(digits - text.length()) + text;
    }
}
