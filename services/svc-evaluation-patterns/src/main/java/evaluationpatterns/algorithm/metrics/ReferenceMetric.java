package evaluationpatterns.algorithm.metrics;

import evaluationpatterns.algorithm.CsvDataset;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Reference/Synonym Confidence metric (patterns.md section 7, G4).
 *
 * Measures what fraction of unique RHS values participate in a synonym group —
 * values that are near-duplicate forms of the same concept (e.g. "British" / "british").
 * A high value is evidence of a reference/lookup-table relationship.
 */
public class ReferenceMetric {

    private static final double SIMILARITY_THRESHOLD = 0.85;

    public double compute(CsvDataset data, String rhs) {
        if (!data.hasColumn(rhs)) return 0.0;

        Set<String> uniqueValues = new LinkedHashSet<>();
        for (String[] row : data.getRows()) {
            String val = data.get(row, rhs);
            if (val != null && !val.equals("\\N")) uniqueValues.add(val);
        }

        if (uniqueValues.size() < 2) return 0.0;

        // Skip numeric and date-like columns (similarity is meaningless there)
        if (isNumericColumn(uniqueValues) || isDateColumn(uniqueValues)) return 0.0;

        List<String> values = new ArrayList<>(uniqueValues);
        List<List<String>> groups = findSynonymGroups(values);
        int valuesInGroups = groups.stream().mapToInt(List::size).sum();
        return Math.min((double) valuesInGroups / uniqueValues.size(), 1.0);
    }

    private boolean isNumericColumn(Set<String> values) {
        long numeric = values.stream().filter(v -> {
            try { Double.parseDouble(v); return true; } catch (NumberFormatException e) { return false; }
        }).count();
        return !values.isEmpty() && (double) numeric / values.size() > 0.8;
    }

    private boolean isDateColumn(Set<String> values) {
        long dateLike = values.stream()
                .filter(v -> v.matches("\\d{4}-\\d{2}-\\d{2}.*"))
                .count();
        return !values.isEmpty() && (double) dateLike / values.size() > 0.8;
    }

    private List<List<String>> findSynonymGroups(List<String> values) {
        List<List<String>> groups = new ArrayList<>();
        Set<String> processed = new HashSet<>();

        for (int i = 0; i < values.size(); i++) {
            String v1 = values.get(i);
            if (processed.contains(v1)) continue;

            String n1 = normalize(v1);
            List<String> group = new ArrayList<>();
            group.add(v1);

            for (int j = i + 1; j < values.size(); j++) {
                String v2 = values.get(j);
                if (processed.contains(v2)) continue;
                String n2 = normalize(v2);
                if (n1.equals(n2) || similarity(v1, v2) >= SIMILARITY_THRESHOLD) {
                    group.add(v2);
                    processed.add(v2);
                }
            }

            if (group.size() > 1) {
                groups.add(group);
                processed.addAll(group);
            }
        }

        return groups;
    }

    private String normalize(String value) {
        String result = value.trim().toUpperCase();
        result = Normalizer.normalize(result, Normalizer.Form.NFD);
        result = result.replaceAll("\\p{InCombiningDiacriticalMarks}", "");
        result = result.replaceAll("\\s+", " ").trim();
        return result;
    }

    /**
     * Similarity ratio using LCS length: 2 * lcs / (len1 + len2).
     * Approximates Python's difflib.SequenceMatcher.ratio().
     */
    private double similarity(String s1, String s2) {
        String n1 = normalize(s1);
        String n2 = normalize(s2);
        if (n1.equals(n2)) return 1.0;
        if (n1.isEmpty() || n2.isEmpty()) return 0.0;

        // Limit length for performance on rare very-long strings
        if (n1.length() > 100) n1 = n1.substring(0, 100);
        if (n2.length() > 100) n2 = n2.substring(0, 100);

        int lcs = lcsLength(n1, n2);
        return (double) (2 * lcs) / (n1.length() + n2.length());
    }

    private int lcsLength(String s1, String s2) {
        int m = s1.length(), n = s2.length();
        int[] prev = new int[n + 1];
        int[] curr = new int[n + 1];
        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                curr[j] = s1.charAt(i - 1) == s2.charAt(j - 1)
                        ? prev[j - 1] + 1
                        : Math.max(prev[j], curr[j - 1]);
            }
            int[] tmp = prev; prev = curr; curr = tmp;
            java.util.Arrays.fill(curr, 0);
        }
        return prev[n];
    }
}
