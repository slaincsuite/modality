package cn.slainc.search.source;

import cn.slainc.search.spi.SearchDocumentPayload;

import java.time.Instant;
import java.util.List;

public interface SearchDocumentSource {

    String sourceType();

    List<SearchDocumentPayload> loadAll();

    List<SearchDocumentPayload> loadChangedSince(Instant since);
}
