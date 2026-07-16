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
     */
    public List<RhsAntichain> process(CsvDataset dataset, List<FdEntry> fds) {
        return process(dataset, fds, null, null);
    }

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
                // Attribute universe: union of LHS attributes from seed FDs only.
                // The lattice is built over columns that appear in the extracted FDs,
                // not over every column in the dataset.
                List<String> attributes = seedFds.stream()
                        .flatMap(Set::stream)
                        .distinct()
                        .sorted()
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

            String riskLevel = FdScorer.computeRiskLevel(dr, hasCont, lhs.size(), coverage);
            System.out.printf("  %-35s -> %-20s g=1.00 DR=%.2f ECS=%.2f cov=%.2f cont=%d t=%.2f r=%.2f risk=%-8s score=%+d [%s]%n",
                    "{" + String.join(",", lhs) + "}", rhs,
                    dr, avgEcs, coverage, hasCont ? 1 : 0, tConf, rConf,
                    riskLevel, result.score(), result.decision());

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

    private FdEntry parseNamesString(String names) {

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
