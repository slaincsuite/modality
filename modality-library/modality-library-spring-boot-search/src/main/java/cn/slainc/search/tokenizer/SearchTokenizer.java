package cn.slainc.search.tokenizer;

import cn.slainc.search.config.SearchProperties;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class SearchTokenizer {

    public static final int MAX_TERM_LENGTH = 64;

    private final Set<String> stopWords;

    private final int minTermLength;

    public SearchTokenizer(SearchProperties properties) {
        SearchProperties source = properties == null ? new SearchProperties() : properties;
        this.minTermLength = Math.min(Math.max(1, source.getMinTermLength()), MAX_TERM_LENGTH);
        this.stopWords = normalizeStopWords(source.getStopWords());
    }

    public List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<String> terms = new ArrayList<>();
        List<String> run = new ArrayList<>();
        Script current = Script.NONE;
        for (int index = 0; index < text.length(); ) {
            int codePoint = text.codePointAt(index);
            index += Character.charCount(codePoint);
            int lowered = Character.toLowerCase(codePoint);
            Script script = scriptOf(lowered);
            if (script != current) {
                emit(run, current, terms);
                run.clear();
                current = script;
            }
            if (script != Script.NONE) {
                run.add(new String(Character.toChars(lowered)));
            }
        }
        emit(run, current, terms);
        return terms;
    }

    public Set<String> tokenizeDistinct(String text) {
        return new LinkedHashSet<>(tokenize(text));
    }

    public boolean isStopWord(String term) {
        return term != null && stopWords.contains(term.toLowerCase(Locale.ROOT));
    }

    private void emit(List<String> run, Script script, List<String> terms) {
        if (script == Script.NONE || run.isEmpty()) {
            return;
        }
        if (script == Script.CJK) {
            if (run.size() == 1) {
                accept(run.get(0), terms);
                return;
            }
            for (int index = 0; index + 1 < run.size(); index++) {
                accept(run.get(index) + run.get(index + 1), terms);
            }
            return;
        }
        accept(String.join("", run), terms);
    }

    private void accept(String candidate, List<String> terms) {
        String term = normalize(candidate);
        if (term != null) {
            terms.add(term);
        }
    }

    private String normalize(String candidate) {
        if (candidate == null || candidate.isEmpty()) {
            return null;
        }
        String term = candidate.length() > MAX_TERM_LENGTH ? candidate.substring(0, MAX_TERM_LENGTH) : candidate;
        if (term.length() < minTermLength || stopWords.contains(term)) {
            return null;
        }
        return term;
    }

    private static Set<String> normalizeStopWords(List<String> stopWords) {
        Set<String> normalized = new LinkedHashSet<>();
        if (stopWords == null) {
            return normalized;
        }
        for (String word : stopWords) {
            if (word == null || word.isBlank()) {
                continue;
            }
            normalized.add(word.trim().toLowerCase(Locale.ROOT));
        }
        return normalized;
    }

    private static Script scriptOf(int codePoint) {
        Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
        if (script == Character.UnicodeScript.HAN || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA || script == Character.UnicodeScript.HANGUL) {
            return Script.CJK;
        }
        return Character.isLetterOrDigit(codePoint) ? Script.WORD : Script.NONE;
    }

    private enum Script {
        NONE, WORD, CJK
    }
}
