package cn.slainc.search.analyzer;

import cn.slainc.search.analyzer.SearchAnalyzer.AnalyzedTerm;
import cn.slainc.search.config.SearchProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class SearchAnalyzerTest {

    private final SearchProperties properties = new SearchProperties();

    private final SearchAnalyzer analyzer = new SearchAnalyzer(properties);

    @Test
    void shouldSplitCjkIntoAdjacentBigrams() {
        assertThat(analyzer.analyze("中国人民")).containsExactly("中国", "国人", "人民");
    }

    @Test
    void shouldKeepASingleCjkCharacter() {
        assertThat(analyzer.analyze("的")).containsExactly("的");
    }

    @Test
    void shouldNotJoinCjkBigramsAcrossWhitespace() {
        assertThat(analyzer.analyze("中国 人民")).containsExactly("中国", "人民");
    }

    @Test
    void shouldTokenizeEnglishInLowerCase() {
        assertThat(analyzer.analyze("Spring Boot")).containsExactly("spring", "boot");
    }

    @Test
    void shouldKeepTheRelativePositionsOfPhraseTerms() {
        assertThat(analyzer.analyzeTerms("中国人民")).extracting(AnalyzedTerm::term, AnalyzedTerm::position)
                .containsExactly(tuple("中国", 0), tuple("国人", 1), tuple("人民", 2));
    }

    @Test
    void shouldFilterTheConfiguredStopWords() {
        SearchProperties custom = new SearchProperties();
        custom.setStopWords(List.of("The", "搜索"));
        SearchAnalyzer stopped = new SearchAnalyzer(custom);

        assertThat(stopped.analyze("the 搜索 引擎")).containsExactly("引擎");
        assertThat(stopped.isStopWord("THE")).isTrue();
        assertThat(stopped.isStopWord("引擎")).isFalse();
        assertThat(stopped.stopWords()).containsExactly("the", "搜索");
    }

    @Test
    void shouldReturnNoTermsForBlankText() {
        assertThat(analyzer.analyze(null)).isEmpty();
        assertThat(analyzer.analyze("   ")).isEmpty();
    }
}
