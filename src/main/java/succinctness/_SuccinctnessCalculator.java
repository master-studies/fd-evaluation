package succinctness;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import it.unimi.dsi.fastutil.ints.IntList;

public class _SuccinctnessCalculator {

    public final List<_SuccinctnessResult> RESULTS;

    private static final int operatorCount = 2, tupleVarCount = 2;

    public _SuccinctnessCalculator() {
        this.RESULTS = new ArrayList<>();
    }
    
    // Compute length for a single FD
    private int computeLength(IntList determinant, Integer dependent) {
        // FD: X -> Y (determinant -> dependent)
        // DC: ¬(t_α.X = t_β.X ∧ t_α.Y ≠ t_β.Y)
        int determinantSize = determinant.size(); // |X|
        int attributeCount = determinantSize + 1; // |X| + 1 for Y
        return attributeCount + tupleVarCount + operatorCount; // Len(φ)
    }

    public List<Double> computeMetrics(List<_FunctionalDependencyGroup> fds) throws IOException {
        // Calculate lengths and normalized succinctness for each FD
        for (_FunctionalDependencyGroup fd : fds) {
            int length = computeLength(fd.getValues(), fd.getAttributeID());
            RESULTS.add(new _SuccinctnessResult(fd, length));
        }

        // Normalize succinctness
        int minLength = RESULTS.stream()
                .mapToInt(r -> r.length)
                .min()
                .orElse(1);

        LinkedList<Double> normalizedScore = new LinkedList<>();
                
        for (_SuccinctnessResult result : RESULTS) {
            double normalizedSuccinctness = (double) minLength / result.length;
            normalizedScore.add(normalizedSuccinctness);
        }

        return normalizedScore;
    }
}
