package cn.slainc.utils.string;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimilarityUtilsTest {

    @Test
    void levenshtein() {
        assertEquals(3, SimilarityUtils.levenshteinDistance("kitten", "sitting"));
        assertEquals(0, SimilarityUtils.levenshteinDistance("abc", "abc"));
        assertEquals(3, SimilarityUtils.levenshteinDistance("abc", ""));
        assertEquals(1.0d, SimilarityUtils.levenshteinSimilarity("abc", "abc"));
    }

    @Test
    void jaroWinkler() {
        double similarity = SimilarityUtils.jaroWinklerSimilarity("MARTHA", "MARHTA");
        assertTrue(similarity > 0.95d && similarity <= 1.0d);
        assertEquals(1.0d, SimilarityUtils.jaroWinklerSimilarity("abc", "abc"));
        assertEquals(0.0d, SimilarityUtils.jaroSimilarity("abc", "xyz"));
    }
}
