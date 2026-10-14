package cn.slainc.recycle.spi;

import java.time.LocalDateTime;

public record RecycleItemQuery(String entityType, String keyword, String operator, Boolean restored,
                               LocalDateTime beginTime, LocalDateTime endTime, long pageNum, long pageSize) {
}
