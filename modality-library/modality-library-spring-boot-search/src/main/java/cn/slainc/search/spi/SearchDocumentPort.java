package cn.slainc.search.spi;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface SearchDocumentPort {

    SearchDocumentSnapshot upsert(SearchDocumentPayload payload);

    int remove(String sourceType, String sourceId);

    int reindexAll(Collection<String> sourceTypes);

    int removeTerms(Long documentId);

    int insertTerms(Collection<SearchTermRow> terms, int batchSize);

    List<SearchTermRow> findTerms(Collection<String> terms);

    List<SearchDocumentSnapshot> findEnabledDocuments(Collection<Long> documentIds, Collection<String> sourceTypes);

    long countEnabledDocuments();

    long countTerms();

    LocalDateTime lastIndexedAt();
}
