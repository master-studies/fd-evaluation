package evaluationpatterns.algorithm.metrics;

import evaluationpatterns.algorithm.CsvDataset;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Continuous Attributes metric (patterns.md section 5.5).
 *
 * Returns true when any LHS column is a continuous or measurement-type attribute,
 * detected via two independent signals:
 *   1. Name signal — column name contains a known measurement keyword
 *   2. Data signal — column has float-like values with high uniqueness ratio (> 0.9)
 */
public class SpuriousnessMetric {

    private static final Set<String> CONTINUOUS_KEYWORDS = Set.of(
            "latitude", "longitude", "height", "weight", "price", "time", "score",
            "rate", "ratio", "distance", "age", "speed", "temp", "temperature",
            "elevation", "altitude", "amount", "lat", "lng", "lon", "long"
    );

    public boolean hasContinuous(CsvDataset data, List<String> lhs) {
        for (String col : lhs) {
            if (!data.hasColumn(col)) continue;
            if (isContinuousByName(col) || isContinuousByData(data, col)) {
                return true;
            }
        }
        return false;
    }

    private boolean isContinuousByName(String col) {
        String lower = col.toLowerCase();
        return CONTINUOUS_KEYWORDS.stream().anyMatch(lower::contains);
    }

    private boolean isContinuousByData(CsvDataset data, String col) {
        int nonNull = 0;
        int numeric = 0;
        Set<String> distinct = new HashSet<>();

        for (String[] row : data.getRows()) {
            String val = data.get(row, col);
            if (val == null) continue;
            nonNull++;
            distinct.add(val);
            try {
                Double.parseDouble(val);
                numeric++;
            } catch (NumberFormatException ignored) {
            }
        }

        if (nonNull == 0 || data.size() == 0) return false;

        double numericFrac = (double) numeric / nonNull;
        double distinctnessRatio = (double) distinct.size() / data.size();

        // Numeric-dominant AND almost every value is unique → continuous measurement
        return numericFrac > 0.8 && distinctnessRatio > 0.9;
    }
}
