package cn.slainc.search.analyzer;

import cn.slainc.search.config.SearchProperties;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.StopFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.cjk.CJKBigramFilter;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class SearchAnalyzer extends Analyzer {

    public static final String FIELD_NAME = "text";

    private static final Logger LOGGER = LoggerFactory.getLogger(SearchAnalyzer.class);

    private final Set<String> stopWords;

    private final CharArraySet stopWordSet;

    public SearchAnalyzer(SearchProperties properties) {
        SearchProperties source = properties == null ? new SearchProperties() : properties;
        this.stopWords = normalize(source.getStopWords());
        this.stopWordSet = new CharArraySet(this.stopWords, true);
    }

    @Override
    protected TokenStreamComponents createComponents(String fieldName) {
        StandardTokenizer source = new StandardTokenizer();
        TokenStream stream = new CJKBigramFilter(new LowerCaseFilter(source));
        if (!stopWordSet.isEmpty()) {
            stream = new StopFilter(stream, stopWordSet);
        }
        return new TokenStreamComponents(source, stream);
    }

    public List<String> analyze(String text) {
        List<AnalyzedTerm> terms = analyzeTerms(text);
        List<String> result = new ArrayList<>(terms.size());
        for (AnalyzedTerm term : terms) {
            result.add(term.term());
        }
        return result;
    }

    public List<AnalyzedTerm> analyzeTerms(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<AnalyzedTerm> terms = new ArrayList<>();
        try (TokenStream stream = tokenStream(FIELD_NAME, text)) {
            CharTermAttribute term = stream.addAttribute(CharTermAttribute.class);
            PositionIncrementAttribute position = stream.addAttribute(PositionIncrementAttribute.class);
            int current = -1;
            stream.reset();
            while (stream.incrementToken()) {
                current += Math.max(1, position.getPositionIncrement());
                terms.add(new AnalyzedTerm(term.toString(), current));
            }
            stream.end();
        } catch (IOException e) {
            LOGGER.warn("检索分词失败: {}", e.getMessage());
        }
        return terms;
    }

    public boolean isStopWord(String term) {
        return term != null && stopWords.contains(term.toLowerCase(Locale.ROOT));
    }

    public Set<String> stopWords() {
        return stopWords;
    }

    private static Set<String> normalize(List<String> stopWords) {
        Set<String> normalized = new LinkedHashSet<>();
        if (stopWords == null) {
            return normalized;
        }
        for (String word : stopWords) {
            if (word != null && !word.isBlank()) {
                normalized.add(word.trim().toLowerCase(Locale.ROOT));
            }
        }
        return normalized;
    }

    public record AnalyzedTerm(String term, int position) {
    }
}
