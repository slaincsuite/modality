package cn.slainc.search.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class Bm25ScorerTest {

    private static final double K1 = 1.2D;

    private static final double B = 0.75D;

    private final Bm25Scorer scorer = new Bm25Scorer(K1, B);

    @Test
    void shouldComputeTheIdfWithTheBm25Formula() {
        assertThat(Bm25Scorer.idf(100L, 10L)).isCloseTo(2.2637D, within(0.0001D));
        assertThat(Bm25Scorer.idf(0L, 0L)).isCloseTo(Math.log(2D), within(1.0E-9D));
    }

    @Test
    void shouldClampTheDocumentFrequencyIntoTheCorpusRange() {
        assertThat(Bm25Scorer.idf(10L, 50L)).isEqualTo(Bm25Scorer.idf(10L, 10L));
        assertThat(Bm25Scorer.idf(10L, -3L)).isEqualTo(Bm25Scorer.idf(10L, 0L));
        assertThat(Bm25Scorer.idf(-5L, 0L)).isEqualTo(Bm25Scorer.idf(0L, 0L));
    }

    @Test
    void shouldApplyTheSaturationAndLengthNormalisation() {

        assertThat(scorer.score(1D, 5D, 5D, 2D)).isCloseTo(2D, within(1.0E-9D));

        assertThat(scorer.score(2D, 5D, 5D, 2D)).isCloseTo(2.75D, within(1.0E-9D));

        assertThat(scorer.score(1D, 10D, 5D, 2D)).isLessThan(scorer.score(1D, 5D, 5D, 2D));
    }

    @Test
    void shouldFallBackToTheDefaultParametersForInvalidOnes() {
        Bm25Scorer fallback = new Bm25Scorer(-1D, 2D);

        assertThat(fallback.k1()).isEqualTo(K1);
        assertThat(fallback.b()).isEqualTo(B);
    }

    @Test
    void shouldReturnZeroForEmptyInput() {
        assertThat(scorer.score(0D, 5D, 5D, 2D)).isZero();
        assertThat(scorer.score(1D, 5D, 5D, 0D)).isZero();
        assertThat(scorer.score(1D, 5D, 0D, 2D)).isEqualTo(scorer.score(1D, 5D, 1D, 2D));
    }
}
