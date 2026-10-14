package cn.slainc.search.support;

import cn.slainc.search.config.SearchProperties;
import cn.slainc.search.support.SearchQueryParser.ParsedQuery;
import cn.slainc.search.tokenizer.SearchTokenizer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SearchQueryParserTest {

    private final SearchQueryParser parser = new SearchQueryParser(new SearchTokenizer(new SearchProperties()));

    @Test
    void shouldTreatSpacesAsAnd() {
        ParsedQuery parsed = parser.parse("Java Spring");

        assertThat(parsed.mustTerms()).containsExactly("java", "spring");
        assertThat(parsed.hasCriteria()).isTrue();
        assertThat(parsed.lookupTerms()).containsExactly("java", "spring");
    }

    @Test
    void shouldParseQuotedPhrasesAndCollapseTheirWhitespace() {
        ParsedQuery parsed = parser.parse("\"Spring    Boot\"");

        assertThat(parsed.phrases()).containsExactly("spring boot");
        assertThat(parsed.mustTerms()).containsExactly("spring", "boot");
    }

    @Test
    void shouldParseExcludedTerms() {
        ParsedQuery parsed = parser.parse("java -spring");

        assertThat(parsed.mustTerms()).containsExactly("java");
        assertThat(parsed.excludedTerms()).containsExactly("spring");
        assertThat(parsed.lookupTerms()).containsExactly("java", "spring");
    }

    @Test
    void shouldParseExcludedPhrases() {
        ParsedQuery parsed = parser.parse("java -\"spring boot\"");

        assertThat(parsed.mustTerms()).containsExactly("java");
        assertThat(parsed.excludedPhrases()).containsExactly("spring boot");
        assertThat(parsed.excludedTerms()).isEmpty();
    }

    @Test
    void shouldTreatAQueryWithOnlyExclusionsAsNoCriteria() {
        assertThat(parser.parse("-java").hasCriteria()).isFalse();
        assertThat(parser.parse("  ").hasCriteria()).isFalse();
        assertThat(parser.parse(null).hasCriteria()).isFalse();
        assertThat(parser.parse("!!!").hasCriteria()).isFalse();
    }

    @Test
    void shouldDropStopWordsOnTheQuerySide() {
        SearchProperties properties = new SearchProperties();
        properties.setStopWords(List.of("the"));
        SearchQueryParser stopWordParser = new SearchQueryParser(new SearchTokenizer(properties));

        assertThat(stopWordParser.parse("the java").mustTerms()).containsExactly("java");
    }

    @Test
    void shouldTokenizeChineseIntoBigrams() {
        assertThat(parser.parse("中文搜索").mustTerms()).containsExactly("中文", "文搜", "搜索");
    }

    @Test
    void shouldPreferExclusionWhenATermIsBothRequiredAndExcluded() {
        assertThat(parser.parse("java -java").mustTerms()).isEmpty();
    }

    @Test
    void shouldCapTheNumberOfQueryTerms() {
        List<String> words = new ArrayList<>();
        for (int index = 0; index < SearchQueryParser.MAX_QUERY_TERMS + 8; index++) {
            words.add("w" + index);
        }

        assertThat(parser.parse(String.join(" ", words)).mustTerms()).hasSize(SearchQueryParser.MAX_QUERY_TERMS);
    }

    @Test
    void shouldKeepAnUnclosedQuoteAsAPhrase() {
        assertThat(parser.parse("\"spring boot").phrases()).containsExactly("spring boot");
    }
}
