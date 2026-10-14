package cn.slainc.search.index;

import cn.slainc.search.config.SearchProperties;
import cn.slainc.search.enums.SearchField;
import cn.slainc.search.spi.SearchDocumentPort;
import cn.slainc.search.spi.SearchDocumentSnapshot;
import cn.slainc.search.spi.SearchTermRow;
import cn.slainc.search.tokenizer.SearchTokenizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SearchIndexer {

    private static final Logger LOGGER = LoggerFactory.getLogger(SearchIndexer.class);

    private final ObjectProvider<SearchDocumentPort> portProvider;

    private final SearchTokenizer tokenizer;

    private final SearchProperties properties;

    private final LuceneIndexStore indexStore;

    public SearchIndexer(ObjectProvider<SearchDocumentPort> portProvider, SearchTokenizer tokenizer,
                         SearchProperties properties) {
        this(portProvider, tokenizer, properties, null);
    }

    public SearchIndexer(ObjectProvider<SearchDocumentPort> portProvider, SearchTokenizer tokenizer,
                         SearchProperties properties, LuceneIndexStore indexStore) {
        this.properties = properties == null ? new SearchProperties() : properties;
        this.tokenizer = tokenizer == null ? new SearchTokenizer(this.properties) : tokenizer;
        this.portProvider = portProvider;
        this.indexStore = indexStore;
    }

    public List<SearchTermRow> buildTerms(SearchDocumentSnapshot document) {
        if (document == null) {
            return List.of();
        }
        List<SearchTermRow> terms = new ArrayList<>();
        append(terms, document, SearchField.TITLE, document.title());
        append(terms, document, SearchField.SUMMARY, document.summary());
        append(terms, document, SearchField.CONTENT, truncate(document.content()));
        append(terms, document, SearchField.TAGS, document.tags());
        return terms;
    }

    public int reindex(SearchDocumentSnapshot document) {
        if (document == null || document.id() == null) {
            LOGGER.warn("跳过缺少 ID 的检索文档索引构建");
            return 0;
        }
        if (indexStore != null) {
            return reindexLucene(document);
        }
        int removed = removeByDocumentId(document.id());
        List<SearchTermRow> terms = buildTerms(document);
        SearchDocumentPort port = port();
        if (terms.isEmpty() || port == null) {
            return 0;
        }
        port.insertTerms(terms, batchSize());
        LOGGER.debug("已为检索文档 {} 重建 {} 个词项行，移除 {} 个过期行", document.id(), terms.size(), removed);
        return terms.size();
    }

    public int removeByDocumentId(Long documentId) {
        if (documentId == null) {
            return 0;
        }
        if (indexStore != null) {
            try {
                return indexStore.delete(documentId);
            } catch (IOException e) {
                LOGGER.warn("删除 Lucene 文档 {} 失败: {}", documentId, e.getMessage());
                return 0;
            }
        }
        SearchDocumentPort port = port();
        if (port == null) {
            return 0;
        }
        return port.removeTerms(documentId);
    }

    public boolean usesLuceneIndex() {
        return indexStore != null;
    }

    private int reindexLucene(SearchDocumentSnapshot document) {
        if (!Boolean.TRUE.equals(document.enabled())) {
            removeByDocumentId(document.id());
            LOGGER.debug("检索文档 {} 已禁用，已从 Lucene 索引移除", document.id());
            return 0;
        }
        try {
            indexStore.upsert(document);
        } catch (IOException e) {
            LOGGER.warn("写入 Lucene 文档 {} 失败: {}", document.id(), e.getMessage());
            return 0;
        }
        LOGGER.debug("已重建 Lucene 文档 {}", document.id());
        return 1;
    }

    private void append(List<SearchTermRow> terms, SearchDocumentSnapshot document, SearchField field,
                        String text) {
        Map<String, Integer> frequencies = new LinkedHashMap<>();
        for (String term : tokenizer.tokenize(text)) {
            frequencies.merge(term, 1, Integer::sum);
        }
        for (Map.Entry<String, Integer> entry : frequencies.entrySet()) {
            terms.add(new SearchTermRow(document.id(), entry.getKey(), field.name(),
                    field.weight() * entry.getValue(), document.tenantId()));
        }
    }

    private String truncate(String content) {
        if (content == null) {
            return null;
        }
        int maxLength = Math.max(1, properties.getMaxContentLength());
        return content.length() > maxLength ? content.substring(0, maxLength) : content;
    }

    private int batchSize() {
        return Math.max(1, properties.getReindexBatchSize());
    }

    private SearchDocumentPort port() {
        return portProvider == null ? null : portProvider.getIfAvailable();
    }
}
