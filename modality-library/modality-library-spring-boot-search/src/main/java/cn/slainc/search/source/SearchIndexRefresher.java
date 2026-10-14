package cn.slainc.search.source;

import cn.slainc.search.spi.SearchDocumentPayload;
import cn.slainc.search.spi.SearchDocumentPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public class SearchIndexRefresher {

    private static final Logger LOGGER = LoggerFactory.getLogger(SearchIndexRefresher.class);

    private final SearchDocumentRegistry registry;

    private final ObjectProvider<SearchDocumentPort> portProvider;

    public SearchIndexRefresher(SearchDocumentRegistry registry, ObjectProvider<SearchDocumentPort> portProvider) {
        this.registry = registry;
        this.portProvider = portProvider;
    }

    public int refreshAll() {
        return refresh(List.of());
    }

    public int refresh(Collection<String> sourceTypes) {
        SearchDocumentPort port = port();
        if (port == null) {
            return 0;
        }
        return port.reindexAll(sourceTypes);
    }

    public int refreshChanged(Instant since) {
        SearchDocumentPort port = port();
        if (port == null || registry == null) {
            return 0;
        }
        Instant from = since == null ? Instant.EPOCH : since;
        int indexed = 0;
        for (SearchDocumentSource source : registry.sources()) {
            List<SearchDocumentPayload> payloads = source.loadChangedSince(from);
            if (payloads == null || payloads.isEmpty()) {
                continue;
            }
            for (SearchDocumentPayload payload : payloads) {
                if (payload == null) {
                    continue;
                }
                port.upsert(payload);
                indexed++;
            }
        }
        LOGGER.info("自 {} 起增量重建了 {} 个检索文档", from, indexed);
        return indexed;
    }

    private SearchDocumentPort port() {
        return portProvider == null ? null : portProvider.getIfAvailable();
    }
}
