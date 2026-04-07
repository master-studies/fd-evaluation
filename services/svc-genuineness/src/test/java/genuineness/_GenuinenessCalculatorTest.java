package genuineness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;

/**
 * Unit tests for _GenuinenessCalculator focused on per-value mode fractions.
 */
class _GenuinenessCalculatorTest {

    private static final String DATASETS_DIR = "datasets";

    private _GenuinenessCalculator calculator;

    @BeforeEach
    void setUp() throws IOException {
        calculator = new _GenuinenessCalculator();
        Files.createDirectories(Path.of(DATASETS_DIR));
    }

    private static _FunctionalDependencyGroup fd(int rhs, int... lhsValues) {
        IntList lhs = new IntArrayList();
        for (int value : lhsValues) {
            lhs.add(value);
        }
        return new _FunctionalDependencyGroup(rhs, lhs);
    }

    private static String writeDataset(String... rows) throws IOException {
        String testFile = "genuineness-calculator-test-" + UUID.randomUUID() + ".csv";
        Files.write(Path.of(DATASETS_DIR, testFile), List.of(rows));
        return testFile;
    }

    @Test
    void returnsOneForOneToOneMappings() throws IOException {
        // Determinant column 0 uniquely determines dependent column 1.
        String testFile = writeDataset(
                "a,1",
                "b,2",
                "c,3",
                "d,4"
        );

        List<Double> scores = calculator.computeMetrics(List.of(fd(1, 0)), testFile);

        assertNotNull(scores);
        assertEquals(1, scores.size());
        assertEquals(1.0, scores.get(0), 1e-9,
                "Genuineness should be 1.0 for strict 1:1 mappings");
    }

    @Test
    void returnsModeFractionForMixedDistribution() throws IOException {
        /*
         * FD: column 0 -> column 1
         * For X='a', Y values are [1,1,2] so mode fraction is 2/3 (~0.66).
         * With a single X-group, genuineness equals that fraction.
         */
        String testFile = writeDataset(
                "a,1",
                "a,1",
                "a,2"
        );

        List<Double> scores = calculator.computeMetrics(List.of(fd(1, 0)), testFile);

        assertNotNull(scores);
        assertEquals(1, scores.size());
        assertEquals(2.0 / 3.0, scores.get(0), 1e-9,
                "Genuineness should equal the statistical mode fraction for mixed distributions");
    }
}
