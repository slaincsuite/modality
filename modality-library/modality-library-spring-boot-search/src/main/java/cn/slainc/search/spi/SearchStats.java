package cn.slainc.search.spi;

import java.time.LocalDateTime;

public record SearchStats(long documentCount, long termCount, LocalDateTime lastIndexedAt) {

    public static SearchStats empty() {
        return new SearchStats(0L, 0L, null);
    }
}
