package cn.slainc.search.support;

import cn.slainc.search.tokenizer.SearchTokenizer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public class SearchQueryParser {

    public static final int MAX_QUERY_TERMS = 32;

    private static final Pattern WHITESPACE = Pattern.compile("[\\s\\u3000]+");

    private final SearchTokenizer tokenizer;

    public SearchQueryParser(SearchTokenizer tokenizer) {
        this.tokenizer = tokenizer == null ? new SearchTokenizer(null) : tokenizer;
    }

    public ParsedQuery parse(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return ParsedQuery.empty();
        }
        Set<String> phrases = new LinkedHashSet<>();
        Set<String> excludedPhrases = new LinkedHashSet<>();
        Set<String> mustTerms = new LinkedHashSet<>();
        Set<String> excludedTerms = new LinkedHashSet<>();
        for (QueryToken token : tokenize(keyword)) {
            if (token.phrase()) {
                String phrase = collapseWhitespace(token.text().toLowerCase(Locale.ROOT));
                if (phrase.isEmpty()) {
                    continue;
                }
                if (token.excluded()) {
                    excludedPhrases.add(phrase);
                } else {
                    phrases.add(phrase);
                    mustTerms.addAll(tokenizer.tokenizeDistinct(phrase));
                }
                continue;
            }
            Set<String> terms = tokenizer.tokenizeDistinct(token.text());
            if (token.excluded()) {
                excludedTerms.addAll(terms);
            } else {
                mustTerms.addAll(terms);
            }
        }
        mustTerms.removeAll(excludedTerms);
        return new ParsedQuery(limit(phrases), limit(excludedPhrases), limit(mustTerms), limit(excludedTerms));
    }

    public static String collapseWhitespace(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return WHITESPACE.matcher(value).replaceAll(" ").trim();
    }

    public List<QueryToken> tokenize(String keyword) {
        List<QueryToken> tokens = new ArrayList<>();
        if (keyword == null) {
            return tokens;
        }
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        boolean excluded = false;
        for (int index = 0; index < keyword.length(); index++) {
            char ch = keyword.charAt(index);
            if (ch == '"') {
                if (quoted) {
                    tokens.add(new QueryToken(current.toString(), true, excluded));
                    current.setLength(0);
                    quoted = false;
                    excluded = false;
                } else {
                    quoted = true;
                }
                continue;
            }
            if (!quoted && Character.isWhitespace(ch)) {
                if (current.length() > 0) {
                    tokens.add(new QueryToken(current.toString(), false, excluded));
                    current.setLength(0);
                }
                excluded = false;
                continue;
            }
            if (!quoted && ch == '-' && current.length() == 0 && !excluded) {
                excluded = true;
                continue;
            }
            current.append(ch);
        }
        if (current.length() > 0) {
            tokens.add(new QueryToken(current.toString(), quoted, excluded));
        }
        return tokens;
    }

    private static List<String> limit(Collection<String> values) {
        return values.stream().limit(MAX_QUERY_TERMS).toList();
    }

    public record QueryToken(String text, boolean phrase, boolean excluded) {
    }

    public record ParsedQuery(List<String> phrases, List<String> excludedPhrases, List<String> mustTerms,
                              List<String> excludedTerms) {

        public ParsedQuery {
            phrases = phrases == null ? List.of() : List.copyOf(phrases);
            excludedPhrases = excludedPhrases == null ? List.of() : List.copyOf(excludedPhrases);
            mustTerms = mustTerms == null ? List.of() : List.copyOf(mustTerms);
            excludedTerms = excludedTerms == null ? List.of() : List.copyOf(excludedTerms);
        }

        public static ParsedQuery empty() {
            return new ParsedQuery(List.of(), List.of(), List.of(), List.of());
        }

        public boolean hasCriteria() {
            return !mustTerms.isEmpty();
        }

        public List<String> lookupTerms() {
            Set<String> terms = new LinkedHashSet<>(mustTerms);
            terms.addAll(excludedTerms);
            return List.copyOf(terms);
        }
    }
}
