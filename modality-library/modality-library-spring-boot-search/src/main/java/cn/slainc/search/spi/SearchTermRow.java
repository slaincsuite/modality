package cn.slainc.search.spi;

public record SearchTermRow(Long documentId, String term, String field, Integer weight, Long tenantId) {
}
