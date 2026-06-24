package evaluationpatterns.algorithm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import evaluationpatterns.algorithm.metrics.CoverageMetric;
import evaluationpatterns.algorithm.metrics.GenuinenessMetric;
import evaluationpatterns.algorithm.metrics.ReferenceMetric;
import evaluationpatterns.algorithm.metrics.SpuriousnessMetric;
import evaluationpatterns.algorithm.metrics.TransformationMetric;
import evaluationpatterns.algorithm.metrics.UniquenessMetrics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Main orchestrator for the evaluation-patterns pipeline.
 *
 * Port of run_lattice.py + make_data_eval_fn from lattice_pruner.py.
 *
 * For each unique RHS found in the provided FDs:
 *   1. Compute reference confidence (RHS-only metric, computed once per RHS).
 *   2. Run the lattice pruner using data-driven evaluation.
 *   3. Collect the maximal antichain of fake LHS sets.
 *
 * Evaluation pipeline per node (from make_data_eval_fn):
 *   1. Genuineness gate  — non-exact FDs (< 1.0) are treated as FAKE.
 *   2. Compute DR, AvgECS, Coverage, HasContinuous, TransformationConfidence.
 *   3. classify_fd() → decision.
 *   4. GENUINE / LIKELY_GENUINE / PROBABLY_GENUINE → genuine.
 *   5. SUSPICIOUS → invoke QuestionCallback if provided; else treat as FAKE.
 *   6. Everything else → FAKE.
 */
public class EvaluationPatternsProcessor {

    private static final Logger LOGGER =
            Logger.getLogger(EvaluationPatternsProcessor.class.getName());

    private static final double GENUINENESS_THRESHOLD = 1.0 - 1e-9;

    private final GenuinenessMetric genuinenessMetric = new GenuinenessMetric();
    private final CoverageMetric coverageMetric = new CoverageMetric();
    private final UniquenessMetrics uniquenessMetrics = new UniquenessMetrics();
    private final SpuriousnessMetric spuriousnessMetric = new SpuriousnessMetric();
    private final TransformationMetric transformationMetric = new TransformationMetric();
    private final ReferenceMetric referenceMetric = new ReferenceMetric();

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * A functional dependency entry with column-name LHS and RHS.
     * LHS is stored as a Set for lattice operations; convert to sorted List for metrics.
     */
    public record FdEntry(Set<String> lhs, String rhs) {}

    /**
     * The antichain result for one RHS: the RHS column name and the list of
     * maximal fake LHS sets formatted as "{col1,col2}" strings.
     */
    public record RhsAntichain(String rhs, List<String> antichainLhs) {}

    /**
     * Called when the lattice encounters a SUSPICIOUS FD that requires user judgement.
     * Returns true if the user considers the FD genuine, false if fake.
     */
    @FunctionalInterface
    public interface QuestionCallback {
        boolean ask(List<String> lhs, String rhs);
    }

    /**
     * Called as each RHS column starts and finishes processing.
     * onRhsCompleted receives the fake-antichain strings computed for that RHS.
     */
    public interface ProgressCallback {
        void onRhsStarted(String rhs);
        void onRhsCompleted(String rhs, List<String> antichainLhs);
    }

    /**
     * Run the full evaluation pipeline for all RHS columns in the provided FDs.
     * Convenience overload with no callbacks (automated mode: SUSPICIOUS → FAKE).
     */
    public List<RhsAntichain> process(CsvDataset dataset, List<FdEntry> fds) {
        return process(dataset, fds, null, null);
    }

    /**
     * Run the full evaluation pipeline for all RHS columns in the provided FDs.
     *
     * @param dataset          The loaded CSV dataset.
     * @param fds              FD entries parsed from the FD-Discovery result.
     * @param progressCallback Called when each RHS starts and finishes (may be null).
     * @param questionCallback Called when a SUSPICIOUS FD needs user input (may be null → FAKE).
     * @return One RhsAntichain per RHS that has FDs.
     */
    public List<RhsAntichain> process(CsvDataset dataset, List<FdEntry> fds,
            ProgressCallback progressCallback, QuestionCallback questionCallback) {

        // Group FDs by RHS, preserving dataset column order
        Map<String, List<Set<String>>> byRhs = new LinkedHashMap<>();
        for (FdEntry fd : fds) {
            byRhs.computeIfAbsent(fd.rhs(), k -> new ArrayList<>()).add(fd.lhs());
        }

        List<RhsAntichain> results = new ArrayList<>();

        for (Map.Entry<String, List<Set<String>>> entry : byRhs.entrySet()) {
            String rhs = entry.getKey();
            List<Set<String>> seedFds = entry.getValue();

            if (!dataset.hasColumn(rhs)) {
                LOGGER.warning("RHS column not found in dataset: " + rhs);
                continue;
            }

            if (progressCallback != null) progressCallback.onRhsStarted(rhs);

            try {
                // All attributes except the current RHS form the attribute universe
                List<String> attributes = dataset.getColumns().stream()
                        .filter(col -> !col.equals(rhs))
                        .collect(Collectors.toList());

                if (attributes.isEmpty()) {
                    results.add(new RhsAntichain(rhs, Collections.emptyList()));
                    if (progressCallback != null) progressCallback.onRhsCompleted(rhs, Collections.emptyList());
                    continue;
                }

                // Reference confidence depends only on RHS — compute once and reuse
                double rConf = referenceMetric.compute(dataset, rhs);

                // Build the evaluate function for this RHS
                LatticePruner.LatticeResult latticeResult = LatticePruner.pruneLattice(
                        attributes,
                        seedFds,
                        lhs -> evaluateNode(dataset, lhs, rhs, rConf, questionCallback));

                List<Set<String>> fakeAntichain = LatticePruner.collectFakeLhs(latticeResult);

                List<String> antichainStrings = fakeAntichain.stream()
                        .map(lhsSet -> "{" + lhsSet.stream().sorted()
                                .collect(Collectors.joining(",")) + "}")
                        .collect(Collectors.toList());

                results.add(new RhsAntichain(rhs, antichainStrings));

                LOGGER.info("[EvalPatterns] RHS=" + rhs
                        + " seeds=" + seedFds.size()
                        + " fakeAntichain=" + antichainStrings.size());

                if (progressCallback != null) progressCallback.onRhsCompleted(rhs, antichainStrings);

            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error processing RHS=" + rhs, e);
                results.add(new RhsAntichain(rhs, Collections.emptyList()));
                if (progressCallback != null) progressCallback.onRhsCompleted(rhs, Collections.emptyList());
            }
        }

        return results;
    }

    /**
     * Parse FD entries from the JSON result stored by the FD-Discovery service.
     *
     * Expected format: JSON array of {"names": "[col1, col2] -> col3", "indices": "..."} objects.
     * The names field is parsed; indices are ignored (evaluation works on column names).
     */
    public List<FdEntry> parseFds(String fdDiscoveryResultJson) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode jsonArray = mapper.readTree(fdDiscoveryResultJson);

        List<FdEntry> fds = new ArrayList<>();
        for (JsonNode item : jsonArray) {
            if (!item.has("names")) continue;
            String names = item.get("names").asText();
            FdEntry fd = parseNamesString(names);
            if (fd != null) {
                fds.add(fd);
            }
        }
        return fds;
    }

    /**
     * Format the per-RHS antichain results as a CSV string.
     *
     * Output format (one row per fake LHS):
     *   RHS,FakeLhs
     *   driverRef,"{surname}"
     *   name,"{lat,lng}"
     */
    public String buildOutputCsv(List<RhsAntichain> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("RHS,FakeLhs\n");
        for (RhsAntichain r : results) {
            for (String lhs : r.antichainLhs()) {
                sb.append(csvField(r.rhs()))
                  .append(',')
                  .append(csvField(lhs))
                  .append('\n');
            }
        }
        return sb.toString();
    }

    // ── Node evaluation ───────────────────────────────────────────────────────

    /**
     * Evaluate a single lattice node (LHS → rhs) using all data metrics.
     *
     * Returns GENUINE for decisions {GENUINE, LIKELY_GENUINE, PROBABLY_GENUINE}.
     * For SUSPICIOUS: invokes questionCallback if provided; otherwise returns FAKE.
     * All other decisions → FAKE.
     */
    private LatticePruner.NodeStatus evaluateNode(
            CsvDataset data, Set<String> lhsSet, String rhs, double rConf,
            QuestionCallback questionCallback) {

        // Use a sorted list for consistent key ordering in metric computations
        List<String> lhs = lhsSet.stream().sorted().collect(Collectors.toList());

        try {
            // Genuineness gate: non-exact FDs are treated as fake immediately
            double g = genuinenessMetric.compute(data, lhs, rhs);
            if (g < GENUINENESS_THRESHOLD) {
                return LatticePruner.NodeStatus.FAKE;
            }

            double dr       = uniquenessMetrics.distinctnessRatio(data, lhs);
            double avgEcs   = uniquenessMetrics.avgEquivalenceClassSize(data, lhs);
            double coverage = coverageMetric.compute(data, lhs, rhs);
            boolean hasCont = spuriousnessMetric.hasContinuous(data, lhs);
            double tConf    = transformationMetric.compute(data, lhs, rhs);

            FdScorer.ClassificationResult result = FdScorer.classifyFd(
                    dr, avgEcs, hasCont, lhs.size(), coverage, rhs, lhs, tConf, rConf);

            return switch (result.decision()) {
                case GENUINE, LIKELY_GENUINE, PROBABLY_GENUINE ->
                        LatticePruner.NodeStatus.GENUINE;
                case SUSPICIOUS -> {
                    if (questionCallback != null) {
                        boolean isGenuine = questionCallback.ask(lhs, rhs);
                        yield isGenuine
                                ? LatticePruner.NodeStatus.GENUINE
                                : LatticePruner.NodeStatus.FAKE;
                    }
                    yield LatticePruner.NodeStatus.FAKE;
                }
                default -> LatticePruner.NodeStatus.FAKE;
            };

        } catch (Exception e) {
            LOGGER.log(Level.WARNING,
                    "Evaluation error: lhs=" + lhs + " rhs=" + rhs, e);
            return LatticePruner.NodeStatus.FAKE; // fail-safe
        }
    }

    // ── Parsing helpers ───────────────────────────────────────────────────────

    /**
     * Parse a names string like "[col1, col2] -> col3" into an FdEntry.
     * Also handles "{col1} -> col3", "-->" and no-space variants like "[col]->col3".
     */
    private FdEntry parseNamesString(String names) {
        // Normalise: collapse any whitespace around -> / --> to a single " -> "
        // so "[dob]->driverId" and "[col] --> rhs" both become "[dob] -> driverId"
        String normalised = names.replaceAll("\\s*-->\\s*", " -> ")
                                 .replaceAll("\\s*->\\s*", " -> ");

        String[] parts = normalised.split(" -> ", 2);
        if (parts.length != 2) {
            LOGGER.warning("Cannot parse FD names string: " + names);
            return null;
        }

        String lhsPart = parts[0].trim().replaceAll("[\\[\\]{}]", "").trim();
        String rhs     = parts[1].trim().replaceAll("[\\[\\]{}]", "").trim();

        Set<String> lhs = new HashSet<>();
        for (String attr : lhsPart.split(",")) {
            String trimmed = attr.trim();
            if (!trimmed.isEmpty()) lhs.add(trimmed);
        }

        if (lhs.isEmpty() || rhs.isEmpty()) return null;
        return new FdEntry(lhs, rhs);
    }

    private String csvField(String field) {
        if (field.contains(",") || field.contains("\"") || field.contains("\n")) {
            return "\"" + field.replace("\"", "\"\"") + "\"";
        }
        return field;
    }
}
