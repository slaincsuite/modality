package cn.slainc.search.spi;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public record SearchQuery(String keyword, Set<String> sourceTypes, int page, int size, boolean highlight) {

    public SearchQuery {

        sourceTypes = sourceTypes == null || sourceTypes.isEmpty()
                ? Set.of()
                : Collections.unmodifiableSet(new LinkedHashSet<>(sourceTypes));
    }

    public static SearchQuery of(String keyword, int page, int size) {
        return new SearchQuery(keyword, Set.of(), page, size, true);
    }
}
