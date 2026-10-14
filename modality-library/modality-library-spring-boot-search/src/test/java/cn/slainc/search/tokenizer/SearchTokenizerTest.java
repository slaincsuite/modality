package cn.slainc.search.tokenizer;

import cn.slainc.search.config.SearchProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SearchTokenizerTest {

    private static SearchTokenizer tokenizer(List<String> stopWords, int minTermLength) {
        SearchProperties properties = new SearchProperties();
        properties.setStopWords(stopWords);
        properties.setMinTermLength(minTermLength);
        return new SearchTokenizer(properties);
    }

    @Test
    void shouldSplitPureChineseIntoBigrams() {
        assertThat(tokenizer(List.of(), 1).tokenize("中文搜索")).containsExactly("中文", "文搜", "搜索");
    }

    @Test
    void shouldEmitTheSingleCharacterForASingleChineseCharacter() {
        assertThat(tokenizer(List.of(), 1).tokenize("中")).containsExactly("中");
        assertThat(tokenizer(List.of(), 1).tokenize("好 中")).containsExactly("好", "中");
    }

    @Test
    void shouldSplitPureEnglishIntoLowercaseWords() {
        assertThat(tokenizer(List.of(), 1).tokenize("Hello World")).containsExactly("hello", "world");
    }

    @Test
    void shouldSplitMixedChineseEnglishAndDigits() {
        assertThat(tokenizer(List.of(), 1).tokenize("Java搜索21引擎"))
                .containsExactly("java", "搜索", "21", "引擎");
        assertThat(tokenizer(List.of(), 1).tokenize("使用 SpringBoot 3 构建搜索"))
                .containsExactly("使用", "springboot", "3", "构建", "建搜", "搜索");
    }

    @Test
    void shouldTreatPunctuationAsASeparator() {
        assertThat(tokenizer(List.of(), 1).tokenize("hello, world! 搜索-引擎"))
                .containsExactly("hello", "world", "搜索", "引擎");
        assertThat(tokenizer(List.of(), 1).tokenize("state-of-the-art"))
                .containsExactly("state", "of", "the", "art");
    }

    @Test
    void shouldFilterStopWordsCaseInsensitively() {
        SearchTokenizer tokenizer = tokenizer(List.of("The", "搜索"), 1);
        assertThat(tokenizer.tokenize("The 搜索 guide")).containsExactly("guide");
        assertThat(tokenizer.isStopWord("THE")).isTrue();
        assertThat(tokenizer.isStopWord("guide")).isFalse();
    }

    @Test
    void shouldDropTermsShorterThanTheMinimumLength() {
        assertThat(tokenizer(List.of(), 2).tokenize("a bb ccc")).containsExactly("bb", "ccc");
    }

    @Test
    void shouldTruncateLongTermsToTheColumnWidth() {
        List<String> terms = tokenizer(List.of(), 1).tokenize("x".repeat(90));

        assertThat(terms).hasSize(1);
        assertThat(terms.get(0)).hasSize(SearchTokenizer.MAX_TERM_LENGTH)
                .isEqualTo("x".repeat(SearchTokenizer.MAX_TERM_LENGTH));
    }

    @Test
    void shouldReturnNothingForBlankOrPunctuationOnlyInput() {
        SearchTokenizer tokenizer = tokenizer(List.of(), 1);

        assertThat(tokenizer.tokenize(null)).isEmpty();
        assertThat(tokenizer.tokenize("")).isEmpty();
        assertThat(tokenizer.tokenize("   ")).isEmpty();
        assertThat(tokenizer.tokenize("!!! ,,, ---")).isEmpty();
    }

    @Test
    void shouldKeepDuplicatesForTermFrequencyAndDeduplicateOnDemand() {
        assertThat(tokenizer(List.of(), 1).tokenize("搜索 搜索")).containsExactly("搜索", "搜索");
        assertThat(tokenizer(List.of(), 1).tokenizeDistinct("搜索 搜索")).containsExactly("搜索");
    }
}
