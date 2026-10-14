package cn.slainc.search.support;

import cn.slainc.core.result.PageResult;
import cn.slainc.search.analyzer.SearchAnalyzer;
import cn.slainc.search.analyzer.SearchAnalyzer.AnalyzedTerm;
import cn.slainc.search.spi.SearchEngine;
import cn.slainc.search.spi.SearchHit;
import cn.slainc.search.spi.SearchQuery;
import cn.slainc.search.spi.SearchStats;
import cn.slainc.search.config.SearchProperties;
import cn.slainc.search.enums.SearchField;
import cn.slainc.search.index.LuceneIndexStore;
import cn.slainc.search.support.SearchQueryParser;
import cn.slainc.search.support.SearchQueryParser.QueryToken;
import cn.slainc.search.support.SearchTenantResolver;
import cn.slainc.search.support.SnippetHighlighter;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.*;
import org.apache.lucene.search.highlight.*;
import org.apache.lucene.util.BytesRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.function.Supplier;

public class LuceneSearchEngine implements SearchEngine {

    private static final Logger LOGGER = LoggerFactory.getLogger(LuceneSearchEngine.class);

    private static final String[] FIELDS = {LuceneIndexStore.FIELD_TITLE, LuceneIndexStore.FIELD_SUMMARY,
            LuceneIndexStore.FIELD_CONTENT, LuceneIndexStore.FIELD_TAGS};

    private static final float[] WEIGHTS = {SearchField.TITLE.weight(), SearchField.SUMMARY.weight(),
            SearchField.CONTENT.weight(), SearchField.TAGS.weight()};

    private static final String[] SNIPPET_FIELDS = {LuceneIndexStore.FIELD_CONTENT, LuceneIndexStore.FIELD_SUMMARY,
            LuceneIndexStore.FIELD_TITLE};

    private final LuceneIndexStore indexStore;

    private final SearchQueryParser queryParser;

    private final SearchProperties properties;

    private final Supplier<Long> tenantSupplier;

    public LuceneSearchEngine(LuceneIndexStore indexStore, SearchQueryParser queryParser,
                              SearchProperties properties) {
        this(indexStore, queryParser, properties, new SearchTenantResolver());
    }

    public LuceneSearchEngine(LuceneIndexStore indexStore, SearchQueryParser queryParser, SearchProperties properties,
                              Supplier<Long> tenantSupplier) {
        this.properties = properties == null ? new SearchProperties() : properties;
        this.indexStore = indexStore;
        this.queryParser = queryParser == null ? new SearchQueryParser(null) : queryParser;
        this.tenantSupplier = tenantSupplier;
    }

    @Override
    public PageResult<SearchHit> search(SearchQuery query) {
        SearchQuery request = normalize(query);
        long page = request.page();
        long size = request.size();
        if (indexStore == null) {
            return PageResult.empty(page, size);
        }
        QueryPlan plan = plan(request.keyword());
        if (!plan.hasPositiveClause()) {
            return PageResult.empty(page, size);
        }
        Query highlightQuery = positiveQuery(plan);
        Query root = rootQuery(plan, highlightQuery, request.sourceTypes(), tenantId());
        long offset = (page - 1L) * size;
        PageResult<SearchHit> result = indexStore.search(searcher -> search(searcher, root, highlightQuery, plan,
                request, offset, size));
        return result == null ? PageResult.empty(page, size) : result;
    }

    @Override
    public SearchStats stats() {
        if (indexStore == null) {
            return SearchStats.empty();
        }
        SearchStats stats = indexStore.stats();
        return stats == null ? SearchStats.empty() : stats;
    }

    private PageResult<SearchHit> search(IndexSearcher searcher, Query root, Query highlightQuery, QueryPlan plan,
                                         SearchQuery request, long offset, long size) throws IOException {
        long total = searcher.count(root);
        if (total == 0L || offset >= total) {
            return PageResult.of(List.of(), total, request.page(), size);
        }
        int limit = (int) Math.min(total, offset + size);
        TopDocs topDocs = searcher.search(root, limit);
        Highlighter highlighter = request.highlight() ? highlighter(highlightQuery) : null;
        StoredFields storedFields = searcher.storedFields();
        List<String> matchedTerms = plan.matchedTerms();
        List<SearchHit> hits = new ArrayList<>(Math.max(1, topDocs.scoreDocs.length - (int) offset));
        for (int index = (int) offset; index < topDocs.scoreDocs.length; index++) {
            ScoreDoc scoreDoc = topDocs.scoreDocs[index];
            Document document = storedFields.document(scoreDoc.doc);
            hits.add(new SearchHit(documentId(document), value(document, LuceneIndexStore.FIELD_SOURCE_TYPE),
                    value(document, LuceneIndexStore.FIELD_SOURCE_ID), value(document, LuceneIndexStore.FIELD_TITLE),
                    value(document, LuceneIndexStore.FIELD_SUMMARY), snippet(document, highlighter, request.highlight()),
                    scoreDoc.score, matchedTerms));
        }
        return PageResult.of(hits, total, request.page(), size);
    }

    private SearchQuery normalize(SearchQuery query) {
        SearchQuery source = query == null ? new SearchQuery(null, Set.of(), 1, 0, true) : query;
        int page = Math.max(1, source.page());
        int size = source.size() > 0 ? source.size() : properties.getDefaultPageSize();
        size = Math.min(Math.max(1, size), Math.max(1, properties.getMaxPageSize()));
        return new SearchQuery(source.keyword(), source.sourceTypes(), page, size, source.highlight());
    }

    private QueryPlan plan(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return QueryPlan.empty();
        }
        Set<String> mustTerms = new LinkedHashSet<>();
        Set<String> excludedTerms = new LinkedHashSet<>();
        List<List<AnalyzedTerm>> phrases = new ArrayList<>();
        List<List<AnalyzedTerm>> excludedPhrases = new ArrayList<>();
        for (QueryToken token : queryParser.tokenize(keyword)) {
            String text = token.phrase()
                    ? SearchQueryParser.collapseWhitespace(token.text().toLowerCase(Locale.ROOT))
                    : token.text();
            List<AnalyzedTerm> terms = analyzer().analyzeTerms(text);
            if (terms.isEmpty()) {
                continue;
            }
            if (token.phrase()) {
                if (token.excluded()) {
                    excludedPhrases.add(terms);
                } else {
                    phrases.add(terms);
                }
            } else if (token.excluded()) {
                for (AnalyzedTerm term : terms) {
                    excludedTerms.add(term.term());
                }
            } else {
                for (AnalyzedTerm term : terms) {
                    mustTerms.add(term.term());
                }
            }
        }
        mustTerms.removeAll(excludedTerms);
        return new QueryPlan(limit(mustTerms), limitPhrases(phrases), limit(excludedTerms), limitPhrases(excludedPhrases));
    }

    private Query positiveQuery(QueryPlan plan) {
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        for (String term : plan.mustTerms()) {
            builder.add(multiFieldTerm(term), Occur.MUST);
        }
        for (List<AnalyzedTerm> phrase : plan.phrases()) {
            builder.add(multiFieldPhrase(phrase), Occur.MUST);
        }
        return builder.build();
    }

    private Query rootQuery(QueryPlan plan, Query positiveQuery, Set<String> sourceTypes, Long tenantId) {
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        builder.add(positiveQuery, Occur.MUST);
        for (String term : plan.excludedTerms()) {
            builder.add(multiFieldTerm(term), Occur.MUST_NOT);
        }
        for (List<AnalyzedTerm> phrase : plan.excludedPhrases()) {
            builder.add(multiFieldPhrase(phrase), Occur.MUST_NOT);
        }
        if (sourceTypes != null && !sourceTypes.isEmpty()) {
            builder.add(sourceTypeFilter(sourceTypes), Occur.FILTER);
        }
        if (tenantId != null) {
            builder.add(new TermQuery(new Term(LuceneIndexStore.FIELD_TENANT_ID, tenantId.toString())), Occur.FILTER);
        }
        return builder.build();
    }

    private Query multiFieldTerm(String term) {
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        for (int index = 0; index < FIELDS.length; index++) {
            builder.add(new BoostQuery(new TermQuery(new Term(FIELDS[index], term)), WEIGHTS[index]), Occur.SHOULD);
        }
        builder.setMinimumNumberShouldMatch(1);
        return builder.build();
    }

    private Query multiFieldPhrase(List<AnalyzedTerm> terms) {
        if (terms.size() == 1) {
            return multiFieldTerm(terms.get(0).term());
        }
        int base = terms.get(0).position();
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        for (int index = 0; index < FIELDS.length; index++) {
            PhraseQuery.Builder phrase = new PhraseQuery.Builder();
            for (AnalyzedTerm term : terms) {
                phrase.add(new Term(FIELDS[index], term.term()), term.position() - base);
            }
            builder.add(new BoostQuery(phrase.build(), WEIGHTS[index]), Occur.SHOULD);
        }
        builder.setMinimumNumberShouldMatch(1);
        return builder.build();
    }

    private Query sourceTypeFilter(Set<String> sourceTypes) {
        List<BytesRef> terms = new ArrayList<>(sourceTypes.size());
        for (String sourceType : sourceTypes) {
            if (sourceType != null && !sourceType.isBlank()) {
                terms.add(new BytesRef(sourceType.trim()));
            }
        }
        terms.sort(Comparator.naturalOrder());
        return new TermInSetQuery(LuceneIndexStore.FIELD_SOURCE_TYPE, terms);
    }

    private String snippet(Document document, Highlighter highlighter, boolean highlight) {
        if (highlight && highlighter != null) {
            for (String field : SNIPPET_FIELDS) {
                String text = value(document, field);
                if (text == null || text.isBlank()) {
                    continue;
                }
                try {
                    String fragment = highlighter.getBestFragment(analyzer(), field, text);
                    if (fragment != null) {
                        return fragment;
                    }
                } catch (IOException | InvalidTokenOffsetsException e) {
                    LOGGER.debug("字段 {} 高亮失败: {}", field, e.getMessage());
                }
            }
        }
        String text = firstText(document, LuceneIndexStore.FIELD_CONTENT, LuceneIndexStore.FIELD_SUMMARY,
                LuceneIndexStore.FIELD_TITLE);
        return SnippetHighlighter.escapeHtml(truncate(text));
    }

    private Highlighter highlighter(Query query) {
        Highlighter highlighter = new Highlighter(new SimpleHTMLFormatter("<em>", "</em>"), new SimpleHTMLEncoder(),
                new QueryScorer(query));
        highlighter.setTextFragmenter(new SimpleFragmenter(Math.max(1, properties.getSnippetLength())));
        highlighter.setMaxDocCharsToAnalyze(Math.max(Highlighter.DEFAULT_MAX_CHARS_TO_ANALYZE,
                properties.getMaxContentLength()));
        return highlighter;
    }

    private String truncate(String text) {
        if (text == null) {
            return "";
        }
        int maxLength = Math.max(1, properties.getSnippetLength());
        return text.length() > maxLength ? text.substring(0, maxLength) : text;
    }

    private SearchAnalyzer analyzer() {
        return indexStore.analyzer();
    }

    private Long tenantId() {
        if (tenantSupplier == null) {
            return null;
        }
        try {
            return tenantSupplier.get();
        } catch (RuntimeException e) {
            LOGGER.debug("读取租户 ID 失败: {}", e.getMessage());
            return null;
        }
    }

    private static String firstText(Document document, String... fields) {
        for (String field : fields) {
            String value = value(document, field);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String value(Document document, String field) {
        String value = document.get(field);
        return value == null ? "" : value;
    }

    private static Long documentId(Document document) {
        IndexableField field = document.getField(LuceneIndexStore.FIELD_ID);
        if (field == null || field.numericValue() == null) {
            return null;
        }
        return field.numericValue().longValue();
    }

    private static List<String> limit(Set<String> terms) {
        return terms.stream().limit(SearchQueryParser.MAX_QUERY_TERMS).toList();
    }

    private static List<List<AnalyzedTerm>> limitPhrases(List<List<AnalyzedTerm>> phrases) {
        return phrases.stream().limit(SearchQueryParser.MAX_QUERY_TERMS).toList();
    }

    private record QueryPlan(List<String> mustTerms, List<List<AnalyzedTerm>> phrases, List<String> excludedTerms,
                             List<List<AnalyzedTerm>> excludedPhrases) {

        static QueryPlan empty() {
            return new QueryPlan(List.of(), List.of(), List.of(), List.of());
        }

        boolean hasPositiveClause() {
            return !mustTerms.isEmpty() || !phrases.isEmpty();
        }

        List<String> matchedTerms() {
            Set<String> terms = new LinkedHashSet<>(mustTerms);
            for (List<AnalyzedTerm> phrase : phrases) {
                for (AnalyzedTerm term : phrase) {
                    terms.add(term.term());
                }
            }
            return List.copyOf(terms);
        }
    }
}
