package cn.slainc.search.spi;

import java.util.List;

public record SearchHit(Long documentId, String sourceType, String sourceId, String title, String summary,
                        String snippet, double score, List<String> matchedTerms) {

    public SearchHit {
        matchedTerms = matchedTerms == null ? List.of() : List.copyOf(matchedTerms);
    }
}
