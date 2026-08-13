package cn.slainc.utils.id;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class OrderNoGenerator {

    public static final String DEFAULT_PATTERN = "yyyyMMddHHmmss";

    private final String prefix;
    private final DateTimeFormatter formatter;
    private final int sequenceDigits;
    private final AtomicInteger sequence = new AtomicInteger(0);
    private final AtomicLong currentSlot = new AtomicLong(-1L);

    public OrderNoGenerator() {
        this("", DEFAULT_PATTERN, 4);
    }

    public OrderNoGenerator(String prefix, String pattern, int sequenceDigits) {
        if (sequenceDigits <= 0 || sequenceDigits > 9) {
            throw new IllegalArgumentException("序列号位数必须在 1 到 9 之间");
        }
        this.prefix = prefix == null ? "" : prefix;
        this.formatter = DateTimeFormatter.ofPattern(pattern == null ? DEFAULT_PATTERN : pattern);
        this.sequenceDigits = sequenceDigits;
    }

    public String next() {
        LocalDateTime now = LocalDateTime.now();
        long slot = now.atZone(java.time.ZoneId.systemDefault()).toEpochSecond();
        if (currentSlot.get() != slot) {
            synchronized (this) {
                if (currentSlot.get() != slot) {
                    currentSlot.set(slot);
                    sequence.set(0);
                }
            }
        }
        int value = sequence.incrementAndGet();
        return prefix + now.format(formatter) + pad(value);
    }

    private String pad(int value) {
        String text = Integer.toString(value % (int) Math.pow(10, sequenceDigits));
        return "0".repeat(Math.max(0, sequenceDigits - text.length())) + text;
    }
}
