package evaluationpatterns.algorithm.metrics;

import evaluationpatterns.algorithm.CsvDataset;

import java.util.List;

/**
 * Coverage metric (patterns.md section 5.4).
 */
public class CoverageMetric {

    private static final int MAX_ROWS = 2000;

    public double compute(CsvDataset data, List<String> lhs, String rhs) {
        List<String[]> allRows = data.getRows();
        // Sample if the dataset is large
        List<String[]> rows = allRows.size() > MAX_ROWS
                ? allRows.subList(0, MAX_ROWS)
                : allRows;

        int n = rows.size();
        if (n < 2) return 1.0;

        int pSize = lhs.size() + 1;
        int evidenceCount = 0;
        double sumWeight = 0.0;

        for (int i = 0; i < n; i++) {
            String[] ti = rows.get(i);
            for (int j = i + 1; j < n; j++) {
                String[] tj = rows.get(j);

                int k = 0;
                for (String col : lhs) {
                    String vi = data.get(ti, col);
                    String vj = data.get(tj, col);
                    // Nulls never match (mirrors Python NaN behaviour)
                    if (vi != null && vj != null && vi.equals(vj)) {
                        k++;
                    }
                }

                String ri = data.get(ti, rhs);
                String rj = data.get(tj, rhs);
                if (ri != null && rj != null && !ri.equals(rj)) {
                    k++;
                }

                // FD violation — skip this pair
                if (k == pSize) continue;

                evidenceCount++;
                sumWeight += (double) (k + 1) / pSize;
            }
        }

        return evidenceCount == 0 ? 0.0 : sumWeight / evidenceCount;
    }
}
