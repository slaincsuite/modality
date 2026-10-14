package cn.slainc.search.spi;

import java.time.LocalDateTime;

public record SearchDocumentSnapshot(Long id, String sourceType, String sourceId, String title, String summary,
                                     String content, String tags, String url, Boolean enabled,
                                     LocalDateTime indexedAt, Long tenantId) {
}
