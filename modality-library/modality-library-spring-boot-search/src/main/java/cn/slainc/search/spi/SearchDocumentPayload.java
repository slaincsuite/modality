package cn.slainc.search.spi;

public record SearchDocumentPayload(String sourceType, String sourceId, String title, String summary,
                                    String content, String tags, String url, Boolean enabled) {
}
