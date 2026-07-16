package coverage;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import it.unimi.dsi.fastutil.ints.IntList;

public class _CoverageCalculator {


    public _CoverageCalculator() {
    }

 
    // Compute coverage for a single FD 
    private double computeCoverage(IntList determinant, Integer dependent, String fileName) throws IOException {
        _CSVDataset input = new _CSVDataset(fileName, false);
        List<String[]> data = input.readData();
        int n = data.size();
        // if fewer than 2 tuples, define coverage = 1.0
        if (n < 2) return 1.0;

        int pSize = determinant.size() + 1;   // N: total #predicates |X| + 1 
        long evidenceCount = 0;               // # of non‑violating pairs
        double sumWeight = 0.0;               // weighted evidence sum

        for (int i = 0; i < n; i++) {
            String[] ti = data.get(i);
            for (int j = i + 1; j < n; j++) {
                String[] tj = data.get(j);

                // count how many of φ.Pres this pair satisfies
                int k = 0;
                // 1) equalities on each A∈X
                for (int idx = 0; idx < determinant.size(); idx++) {
                    int attr = determinant.getInt(idx);
                    String vi = ti[attr], vj = tj[attr];
                    if (vi != null && vi.equals(vj)) {
                        k++;
                    }
                }
                // 2) inequality on Y
                String yi = ti[dependent], yj = tj[dependent];
                if (yi != null && yj != null && !yi.equals(yj)) {
                    k++;
                }

                // it's a violation 
                if (k == pSize) {
                    continue;
                }

                // it's a k‑evidence
                evidenceCount++;
                sumWeight += (double)(k + 1) / pSize;
            }
        }

        if (evidenceCount == 0) return 0.0;
        return sumWeight / evidenceCount;
    }

    public List<Double> computeMetrics(List<_FunctionalDependencyGroup> fds, String fileName) throws IOException {
        long start = System.currentTimeMillis();
        LinkedList<Double> coverageScores = new LinkedList<>();

        // Calculate coverage for each FD
        for (_FunctionalDependencyGroup fd : fds) {
            double coverage = computeCoverage(fd.getValues(), fd.getAttributeID(), fileName);
            coverageScores.add(coverage);
        }

        System.out.println("[COVERAGE] computeMetrics took " + (System.currentTimeMillis() - start) + " ms");
        return coverageScores;
    }
}
