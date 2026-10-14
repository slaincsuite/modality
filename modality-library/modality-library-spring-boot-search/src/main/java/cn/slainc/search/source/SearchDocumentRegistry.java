package cn.slainc.search.source;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class SearchDocumentRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(SearchDocumentRegistry.class);

    private final Map<String, SearchDocumentSource> sources;

    public SearchDocumentRegistry(Collection<SearchDocumentSource> sources) {
        Map<String, SearchDocumentSource> registered = new LinkedHashMap<>();
        if (sources != null) {
            for (SearchDocumentSource source : sources) {
                if (source == null || source.sourceType() == null || source.sourceType().isBlank()) {
                    continue;
                }
                String sourceType = source.sourceType().trim();
                if (registered.putIfAbsent(sourceType, source) != null) {
                    LOGGER.warn("重复的检索文档源类型 {}，先注册者生效", sourceType);
                }
            }
        }
        this.sources = Collections.unmodifiableMap(registered);
    }

    public Set<String> sourceTypes() {
        return sources.keySet();
    }

    public List<SearchDocumentSource> sources() {
        return List.copyOf(sources.values());
    }

    public SearchDocumentSource find(String sourceType) {
        return sourceType == null || sourceType.isBlank() ? null : sources.get(sourceType.trim());
    }

    public List<SearchDocumentSource> find(Collection<String> sourceTypes) {
        if (sourceTypes == null || sourceTypes.isEmpty()) {
            return sources();
        }
        return sourceTypes.stream().map(this::find).filter(Objects::nonNull).distinct().toList();
    }
}
