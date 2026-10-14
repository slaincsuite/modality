package cn.slainc.search.support;

public class Bm25Scorer {

    private static final double DEFAULT_K1 = 1.2D;

    private static final double DEFAULT_B = 0.75D;

    private final double k1;

    private final double b;

    public Bm25Scorer(double k1, double b) {
        this.k1 = k1 < 0D ? DEFAULT_K1 : k1;
        this.b = b < 0D || b > 1D ? DEFAULT_B : b;
    }

    public double k1() {
        return k1;
    }

    public double b() {
        return b;
    }

    public double score(double termFrequency, double documentLength, double averageDocumentLength, double idf) {
        if (termFrequency <= 0D || idf <= 0D) {
            return 0D;
        }
        double average = averageDocumentLength > 0D ? averageDocumentLength : 1D;
        double denominator = termFrequency + k1 * (1D - b + b * documentLength / average);
        if (denominator <= 0D) {
            return 0D;
        }
        return idf * termFrequency * (k1 + 1D) / denominator;
    }

    public static double idf(long totalDocuments, long documentFrequency) {
        long total = Math.max(0L, totalDocuments);
        long frequency = Math.max(0L, Math.min(documentFrequency, total));
        return Math.log(1D + (total - frequency + 0.5D) / (frequency + 0.5D));
    }
}
