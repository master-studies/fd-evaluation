package succinctness;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;

/**
 * Unit tests for _SuccinctnessCalculator.
 *
 * Formula recap:
 *   length(FD) = |LHS| + 1 (for RHS) + 2 (tupleVarCount) + 2 (operatorCount)
 *              = |LHS| + 5
 *   normalizedScore = minLength / length(FD)
 *
 * Therefore the FD with the smallest LHS always scores 1.0, and all others
 * score strictly below 1.0 proportional to their distance from that minimum.
 */
class _SuccinctnessCalculatorTest {

    private _SuccinctnessCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new _SuccinctnessCalculator();
    }

    private static _FunctionalDependencyGroup fd(int rhs, int... lhsValues) {
        IntList lhs = new IntArrayList();
        for (int v : lhsValues) lhs.add(v);
        return new _FunctionalDependencyGroup(rhs, lhs);
    }

    // -----------------------------------------------------------------------
    // Tests
    // -----------------------------------------------------------------------

    /**
     * A single FD is its own minimum, so its normalized score must be exactly 1.0.
     * This covers the trivial base case and ensures the calculator produces a
     * non-empty result without throwing.
     */
    @Test
    void singleFd_normalizedScoreIsOne() throws IOException {
        List<Double> scores = calculator.computeMetrics(List.of(fd(5, 1, 2)));

        assertEquals(1, scores.size());
        assertEquals(1.0, scores.get(0), 1e-9, "A single FD must score 1.0 after normalization");
    }

    /**
     * When every FD has the same LHS size, every length is identical.
     * minLength / length = 1.0, so all scores must be 1.0.
     */
    @Test
    void equalLhsSizes_allScoresAreOne() throws IOException {
        List<_FunctionalDependencyGroup> fds = List.of(
                fd(1, 10, 20),   // |LHS|=2  → length 7
                fd(2, 30, 40),   // |LHS|=2  → length 7
                fd(3, 50, 60)    // |LHS|=2  → length 7
        );

        List<Double> scores = calculator.computeMetrics(fds);

        assertEquals(3, scores.size());
        for (double score : scores) {
            assertEquals(1.0, score, 1e-9, "All equal-length FDs must score 1.0");
        }
    }

    /**
     * When LHS sizes vary, the calculator should:
     *   - give 1.0 to the shortest FD
     *   - give minLen/len to all others
     * Concrete values (tupleVarCount=2, operatorCount=2):
     *   |LHS|=1 → length 6  → score 6/6 = 1.0
     *   |LHS|=2 → length 7  → score 6/7 ≈ 0.857
     *   |LHS|=3 → length 8  → score 6/8 = 0.75
     */
    @Test
    void varyingLhsSizes_correctNormalization() throws IOException {
        List<_FunctionalDependencyGroup> fds = List.of(
                fd(99, 1),          // |LHS|=1 → minLength=6
                fd(99, 1, 2),       // |LHS|=2 → length 7
                fd(99, 1, 2, 3)     // |LHS|=3 → length 8
        );

        List<Double> scores = calculator.computeMetrics(fds);

        assertEquals(3, scores.size());
        assertEquals(1.0,          scores.get(0), 1e-9, "Shortest FD (|LHS|=1) must score 1.0");
        assertEquals(6.0 / 7.0,    scores.get(1), 1e-9, "|LHS|=2 must score minLen/len");
        assertEquals(6.0 / 8.0,    scores.get(2), 1e-9, "|LHS|=3 must score minLen/len");
    }

    /**
     * Scores are returned in the same insertion order as the input list.
     * The second FD has the smallest LHS here, so it is the 1.0 score.
     */
    @Test
    void orderIsPreserved_minIsNotFirst() throws IOException {
        List<_FunctionalDependencyGroup> fds = List.of(
                fd(99, 1, 2, 3),    // |LHS|=3 → length 8 → score 6/8
                fd(99, 1)           // |LHS|=1 → length 6 → score 1.0
        );

        List<Double> scores = calculator.computeMetrics(fds);

        assertEquals(2, scores.size());
        assertEquals(6.0 / 8.0, scores.get(0), 1e-9, "First entry (longer FD) must score < 1");
        assertEquals(1.0,       scores.get(1), 1e-9, "Second entry (shortest FD) must score 1.0");
    }

    /**
     * An empty input list must return an empty list without throwing.
     * The stream .min().orElse(1) ensures graceful fallback, but there are
     * no results to normalize anyway.
     */
    @Test
    void emptyInput_returnsEmptyList() throws IOException {
        List<Double> scores = calculator.computeMetrics(List.of());

        assertNotNull(scores);
        assertTrue(scores.isEmpty(), "Empty input must produce empty scores");
    }

    /**
     * A very large LHS (e.g. 100 attributes) must still produce a score > 0
     * and ≤ 1.0. This guards against division errors or overflow.
     */
    @Test
    void largeLhs_scoreIsBetweenZeroAndOne() throws IOException {
        IntList bigLhs = new IntArrayList();
        for (int i = 0; i < 100; i++) bigLhs.add(i);
        _FunctionalDependencyGroup bigFd = new _FunctionalDependencyGroup(200, bigLhs);

        List<Double> scores = calculator.computeMetrics(List.of(fd(99, 1), bigFd));

        assertEquals(2, scores.size());
        assertTrue(scores.get(1) > 0.0 && scores.get(1) < 1.0,
                "Large-LHS FD must have score in (0, 1)");
    }

    /**
     * After computeMetrics the internal RESULTS list must match the number
     * of FDs provided. This confirms the mutable accumulation is correct.
     */
    @Test
    void resultsAccumulator_containsAllFds() throws IOException {
        List<_FunctionalDependencyGroup> fds = List.of(fd(1, 10), fd(2, 20, 30));

        calculator.computeMetrics(fds);

        assertEquals(2, calculator.RESULTS.size());
    }
}
