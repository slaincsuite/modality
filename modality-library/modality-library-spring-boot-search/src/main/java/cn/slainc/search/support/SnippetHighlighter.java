package cn.slainc.search.support;

import cn.slainc.search.config.SearchProperties;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class SnippetHighlighter {

    private static final char ELLIPSIS = '…';

    private static final int LEADING_RATIO = 4;

    private final SearchProperties properties;

    public SnippetHighlighter(SearchProperties properties) {
        this.properties = properties == null ? new SearchProperties() : properties;
    }

    public String snippet(String text, Collection<String> terms) {
        return snippet(text, terms, true);
    }

    public String snippet(String text, Collection<String> terms, boolean highlight) {
        String source = text == null ? "" : text;
        if (source.isEmpty()) {
            return "";
        }
        List<String> needles = needles(terms);
        int[] window = window(source, needles, Math.max(1, properties.getSnippetLength()));
        String fragment = source.substring(window[0], window[1]);
        String rendered = render(fragment, needles, highlight);
        StringBuilder snippet = new StringBuilder(rendered.length() + 2);
        if (window[0] > 0) {
            snippet.append(ELLIPSIS);
        }
        snippet.append(rendered);
        if (window[1] < source.length()) {
            snippet.append(ELLIPSIS);
        }
        return snippet.toString();
    }

    public static String escapeHtml(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        StringBuilder escaped = new StringBuilder(value.length() + 16);
        for (int index = 0; index < value.length(); index++) {
            char ch = value.charAt(index);
            switch (ch) {
                case '&' -> escaped.append("&amp;");
                case '<' -> escaped.append("&lt;");
                case '>' -> escaped.append("&gt;");
                case '"' -> escaped.append("&quot;");
                case '\'' -> escaped.append("&#39;");
                default -> escaped.append(ch);
            }
        }
        return escaped.toString();
    }

    private int[] window(String source, List<String> needles, int length) {
        if (source.length() <= length) {
            return new int[]{0, source.length()};
        }
        String lower = source.toLowerCase(Locale.ROOT);
        int first = -1;
        for (String needle : needles) {
            int index = lower.indexOf(needle);
            if (index >= 0 && (first < 0 || index < first)) {
                first = index;
            }
        }
        if (first < 0) {
            return new int[]{0, length};
        }
        int start = Math.max(0, first - length / LEADING_RATIO);
        if (start + length > source.length()) {
            start = source.length() - length;
        }
        return new int[]{start, start + length};
    }

    private String render(String fragment, List<String> needles, boolean highlight) {
        if (needles.isEmpty()) {
            return escapeHtml(fragment);
        }
        String lower = fragment.toLowerCase(Locale.ROOT);
        List<int[]> ranges = new ArrayList<>();
        for (String needle : needles) {
            int from = 0;
            while (from <= lower.length() - needle.length()) {
                int index = lower.indexOf(needle, from);
                if (index < 0) {
                    break;
                }
                ranges.add(new int[]{index, index + needle.length()});
                from = index + 1;
            }
        }
        if (ranges.isEmpty()) {
            return escapeHtml(fragment);
        }
        ranges.sort(Comparator.comparingInt(range -> range[0]));
        List<int[]> merged = new ArrayList<>();
        for (int[] range : ranges) {
            if (!merged.isEmpty() && range[0] <= merged.get(merged.size() - 1)[1]) {
                int[] last = merged.get(merged.size() - 1);
                last[1] = Math.max(last[1], range[1]);
            } else {
                merged.add(new int[]{range[0], range[1]});
            }
        }
        StringBuilder rendered = new StringBuilder(fragment.length() + 16);
        int cursor = 0;
        for (int[] range : merged) {
            if (range[0] > cursor) {
                rendered.append(escapeHtml(fragment.substring(cursor, range[0])));
            }
            String matched = fragment.substring(range[0], range[1]);
            if (highlight) {
                rendered.append("<em>").append(escapeHtml(matched)).append("</em>");
            } else {
                rendered.append(escapeHtml(matched));
            }
            cursor = range[1];
        }
        if (cursor < fragment.length()) {
            rendered.append(escapeHtml(fragment.substring(cursor)));
        }
        return rendered.toString();
    }

    private static List<String> needles(Collection<String> terms) {
        Set<String> needles = new LinkedHashSet<>();
        if (terms == null) {
            return List.of();
        }
        for (String term : terms) {
            if (term == null || term.isBlank()) {
                continue;
            }
            needles.add(term.trim().toLowerCase(Locale.ROOT));
        }
        return List.copyOf(needles);
    }
}
