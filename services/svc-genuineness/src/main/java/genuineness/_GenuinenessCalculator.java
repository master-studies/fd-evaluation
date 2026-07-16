package genuineness;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import it.unimi.dsi.fastutil.ints.IntList;

public class _GenuinenessCalculator {

    public _GenuinenessCalculator() {
    }

    // Compute genuineness per-tuple for a single FD
    private double computeGenuineness(IntList determinant, Integer dependent, String fileName) throws IOException {
        _CSVDataset input = new _CSVDataset(fileName, false);
        List<String[]> data = input.readData();

        Map<String, Integer> countX = new HashMap<>();
        Map<String, Integer> countXA = new HashMap<>();

        for (String[] row : data) {
            String keyX = determinant.stream()
                    .map(i -> row[i])
                    .collect(Collectors.joining("|"));

            String keyXA = keyX + "|" + row[dependent];

            countX.merge(keyX, 1, Integer::sum);
            countXA.merge(keyXA, 1, Integer::sum);
        }

        // For each distinct X value, keep the count of its dominant A value
        Map<String, Integer> maxAgreements = new HashMap<>();

        for (Map.Entry<String, Integer> entry : countXA.entrySet()) {
            String keyXA = entry.getKey();
            String keyX = keyXA.substring(0, keyXA.lastIndexOf('|'));

            maxAgreements.merge(keyX, entry.getValue(), Math::max);
        }

        long agree = maxAgreements.values().stream()
                .mapToLong(Integer::longValue)
                .sum();
        long total = countX.values().stream()
                .mapToLong(Integer::longValue)
                .sum();

        return total == 0 ? 0.0 : (double) agree / total;
    }

    public List<Double> computeMetrics(List<_FunctionalDependencyGroup> fds, String fileName) throws IOException {
        long start = System.currentTimeMillis();
        LinkedList<Double> genuinenessScores = new LinkedList<>();

        // Calculate genuinenessPT for all FDs
        for (_FunctionalDependencyGroup fd : fds) {
            double genuinenessPT = computeGenuineness(fd.getValues(), fd.getAttributeID(), fileName);
            genuinenessScores.add(genuinenessPT);
        }

        System.out.println("[GENUINENESS] computeMetrics took " + (System.currentTimeMillis() - start) + " ms");
        return genuinenessScores;
    }

}
