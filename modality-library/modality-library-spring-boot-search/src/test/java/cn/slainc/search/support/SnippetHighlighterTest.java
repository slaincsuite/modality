package cn.slainc.search.support;

import cn.slainc.search.config.SearchProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SnippetHighlighterTest {

    private static SnippetHighlighter highlighter(int snippetLength) {
        SearchProperties properties = new SearchProperties();
        properties.setSnippetLength(snippetLength);
        return new SnippetHighlighter(properties);
    }

    @Test
    void shouldWrapTheMatchedTermWithEm() {
        assertThat(highlighter(160).snippet("轻量级搜索引擎实现", List.of("搜索")))
                .isEqualTo("轻量级<em>搜索</em>引擎实现");
    }

    @Test
    void shouldCenterTheWindowOnTheFirstMatch() {
        String text = "a".repeat(100) + "java" + "b".repeat(100);

        String snippet = highlighter(40).snippet(text, List.of("java"));

        assertThat(snippet).startsWith("…").endsWith("…").contains("<em>java</em>");
        assertThat(snippet.replace("<em>", "").replace("</em>", "")).hasSize(42);
    }

    @Test
    void shouldMergeAdjacentBigramMatches() {
        assertThat(highlighter(160).snippet("中文搜索引擎", List.of("中文", "文搜", "搜索")))
                .isEqualTo("<em>中文搜索</em>引擎");
    }

    @Test
    void shouldEscapeHtmlAndBlockScriptInjection() {
        String snippet = highlighter(160)
                .snippet("<script>alert('xss')</script> java 搜索", List.of("java"));

        assertThat(snippet).doesNotContain("<script>").contains("&lt;script&gt;")
                .contains("&#39;xss&#39;").contains("<em>java</em>");
    }

    @Test
    void shouldReturnTheHeadOfTheTextWhenNothingMatches() {
        assertThat(highlighter(5).snippet("abcdefghij", List.of("zzz"))).isEqualTo("abcde…");
    }

    @Test
    void shouldNotHighlightWhenHighlightIsDisabled() {
        assertThat(highlighter(160).snippet("java 搜索", List.of("java"), false)).isEqualTo("java 搜索");
    }

    @Test
    void shouldReturnAnEmptySnippetForAnEmptyText() {
        assertThat(highlighter(160).snippet(null, List.of("java"))).isEmpty();
        assertThat(highlighter(160).snippet("", List.of("java"))).isEmpty();
    }

    @Test
    void shouldEscapeWithoutHighlightTerms() {
        assertThat(highlighter(160).snippet("<b>bold</b>", List.of())).isEqualTo("&lt;b&gt;bold&lt;/b&gt;");
    }
}
