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

    // Compute genuineness per-value for a single FD 
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

        Map<String, Double> likelihoods = new HashMap<>();

        for (Map.Entry<String, Integer> entry : countXA.entrySet()) {
            String keyXA = entry.getKey();
            String keyX = keyXA.substring(0, keyXA.lastIndexOf('|'));

            int countForXA = entry.getValue();
            int countForX = countX.getOrDefault(keyX, 1);

            double likCandidate = (double) countForXA / countForX;

            likelihoods.merge(keyX, likCandidate, Math::max);
        }

        double genuineness = likelihoods.values().stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);

        return genuineness;
    }

    public List<Double> computeMetrics(List<_FunctionalDependencyGroup> fds, String fileName) throws IOException {
        LinkedList<Double> genuinenessScores = new LinkedList<>();

        // Calculate genuinenessPV for all FDs
        for (_FunctionalDependencyGroup fd : fds) {
            double genuinenessPV = computeGenuineness(fd.getValues(), fd.getAttributeID(), fileName);
            genuinenessScores.add(genuinenessPV);
        }

        return genuinenessScores;
    }

}
