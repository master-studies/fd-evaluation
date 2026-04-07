package cz.cuni.matfyz.algorithms.depminer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import cz.cuni.matfyz.algorithms.depminer.model._CSVTestCase;
import cz.cuni.matfyz.algorithms.depminer.model._FunctionalDependencyOutput;

/**
 * Ground-truth verification for FD discovery on a deterministic tiny table.
 */
class _FDDiscoveryGroundTruthTest {

    private static final String DATASETS_DIR = "datasets";
    private static final Pattern FD_INDICES_PATTERN = Pattern.compile("[\\[{](.*)[\\]}]\\s*-->\\s*(\\d+)");

    @BeforeEach
    void setUp() throws IOException {
        Files.createDirectories(Path.of(DATASETS_DIR));
    }

    private static String writeDatasetWithHeader(String... rows) throws IOException {
        String testFile = "fd-discovery-groundtruth-" + UUID.randomUUID() + ".csv";
        Files.write(Path.of(DATASETS_DIR, testFile), List.of(rows));
        return testFile;
    }

    private static String canonicalizeIndicesFd(String indicesFd) {
        Matcher matcher = FD_INDICES_PATTERN.matcher(indicesFd.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Unexpected FD indices format: " + indicesFd);
        }

        String lhsRaw = matcher.group(1).trim();
        String rhs = matcher.group(2).trim();

        if (lhsRaw.isEmpty()) {
            return "->" + rhs;
        }

        String lhsCanonical = Arrays.stream(lhsRaw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Integer::parseInt)
                .sorted()
                .map(String::valueOf)
                .collect(Collectors.joining(","));

        return lhsCanonical + "->" + rhs;
    }

    @Test
    void generatedDependencies_matchGroundTruthSetForTestTable() throws Exception {
        /*
         * Header: A,B,C (indices 0,1,2)
         * Rows are designed so that exactly A->B and B->A hold.
         */
        String testFile = writeDatasetWithHeader(
                "A,B,C",
                "1,x,p",
                "1,x,q",
                "2,y,p",
                "2,y,q"
        );

        _CSVTestCase input = new _CSVTestCase(testFile, false);
        DepMiner miner = new DepMiner(input, testFile);

        List<_FunctionalDependencyOutput> discovered = miner.execute();

        assertNotNull(discovered);

        Set<String> actualGroundTruthForm = discovered.stream()
                .map(_FunctionalDependencyOutput::getIndices)
                .map(_FDDiscoveryGroundTruthTest::canonicalizeIndicesFd)
                .collect(Collectors.toCollection(TreeSet::new));

        Set<String> expectedGroundTruthForm = new TreeSet<>(Set.of(
                "0->1",
                "1->0"
        ));

        assertEquals(expectedGroundTruthForm.size(), discovered.size(),
                "The discovered FD list size must exactly match the predefined ground-truth size");
        assertEquals(expectedGroundTruthForm, actualGroundTruthForm,
                "The discovered FD set must be identical to the predefined ground-truth set");
    }
}
