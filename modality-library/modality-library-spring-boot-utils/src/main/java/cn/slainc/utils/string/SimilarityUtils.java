package cn.slainc.utils.string;

public final class SimilarityUtils {

    private SimilarityUtils() {
    }

    public static int levenshteinDistance(String left, String right) {
        if (left == null || right == null) {
            return left == right ? 0 : Math.max(length(left), length(right));
        }
        int n = left.length();
        int m = right.length();
        if (n == 0) {
            return m;
        }
        if (m == 0) {
            return n;
        }
        int[] previous = new int[m + 1];
        int[] current = new int[m + 1];
        for (int j = 0; j <= m; j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= n; i++) {
            current[0] = i;
            char leftChar = left.charAt(i - 1);
            for (int j = 1; j <= m; j++) {
                int cost = leftChar == right.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[m];
    }

    public static double levenshteinSimilarity(String left, String right) {
        if (left == null || right == null) {
            return left == right ? 1.0d : 0.0d;
        }
        int maxLength = Math.max(left.length(), right.length());
        if (maxLength == 0) {
            return 1.0d;
        }
        return 1.0d - (double) levenshteinDistance(left, right) / maxLength;
    }

    public static double jaroSimilarity(String left, String right) {
        if (left == null || right == null) {
            return left == right ? 1.0d : 0.0d;
        }
        int n = left.length();
        int m = right.length();
        if (n == 0 && m == 0) {
            return 1.0d;
        }
        if (n == 0 || m == 0) {
            return 0.0d;
        }
        int window = Math.max(n, m) / 2 - 1;
        if (window < 0) {
            window = 0;
        }
        boolean[] leftMatched = new boolean[n];
        boolean[] rightMatched = new boolean[m];
        int matches = 0;
        for (int i = 0; i < n; i++) {
            int start = Math.max(0, i - window);
            int end = Math.min(i + window + 1, m);
            for (int j = start; j < end; j++) {
                if (rightMatched[j] || left.charAt(i) != right.charAt(j)) {
                    continue;
                }
                leftMatched[i] = true;
                rightMatched[j] = true;
                matches++;
                break;
            }
        }
        if (matches == 0) {
            return 0.0d;
        }
        int transpositions = 0;
        int k = 0;
        for (int i = 0; i < n; i++) {
            if (!leftMatched[i]) {
                continue;
            }
            while (!rightMatched[k]) {
                k++;
            }
            if (left.charAt(i) != right.charAt(k)) {
                transpositions++;
            }
            k++;
        }
        double matched = matches;
        return (matched / n + matched / m + (matched - transpositions / 2.0d) / matched) / 3.0d;
    }

    public static double jaroWinklerSimilarity(String left, String right) {
        double jaro = jaroSimilarity(left, right);
        if (jaro < 0.7d || left == null || right == null) {
            return jaro;
        }
        int prefix = 0;
        int maxPrefix = Math.min(4, Math.min(left.length(), right.length()));
        while (prefix < maxPrefix && left.charAt(prefix) == right.charAt(prefix)) {
            prefix++;
        }
        return jaro + prefix * 0.1d * (1.0d - jaro);
    }

    private static int length(String text) {
        return text == null ? 0 : text.length();
    }
}
