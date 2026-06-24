package evaluationpatterns.algorithm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * FD lattice traversal and fake-LHS antichain extraction (patterns.md Algorithm 2).
 *
 * Port of lattice_pruner.py (prune_lattice, collect_fake_lhs).
 *
 * Traversal strategy (bottom-up, level by level):
 *   1. Evaluate all seed nodes (extracted FDs) using the supplied evaluate function.
 *   2. Mark strict subsets of seeds that are not themselves seeds as GREY
 *      (known unknowns — the miner didn't find them as minimal FDs).
 *   3. For each subsequent level:
 *      - If any parent is GENUINE → inherit GENUINE (Armstrong augmentation).
 *      - If any parent is FAKE or GREY → evaluate this node.
 *      - Otherwise → unreachable, skip.
 *
 * In automated (non-interactive) mode SUSPICIOUS decisions are treated as FAKE.
 */
public final class LatticePruner {

    public enum NodeStatus { GENUINE, FAKE, GREY }

    public static class LatticeResult {
        public final Map<Set<String>, NodeStatus> status;
        public final Set<Set<String>> grey;
        public final int startLevel;
        public final List<String> attributes;

        LatticeResult(Map<Set<String>, NodeStatus> status, Set<Set<String>> grey,
                      int startLevel, List<String> attributes) {
            this.status = Collections.unmodifiableMap(status);
            this.grey = Collections.unmodifiableSet(grey);
            this.startLevel = startLevel;
            this.attributes = Collections.unmodifiableList(attributes);
        }
    }

    private LatticePruner() {}

    /**
     * Prune the lattice and return evaluation results.
     *
     * @param attributes  All dataset columns except the RHS (the attribute universe).
     * @param extractedFds Seed LHS sets found by the FD miner (minimal FDs that hold).
     * @param evaluateFn  Function mapping an LHS set to GENUINE or FAKE.
     *                    SUSPICIOUS is resolved to FAKE internally (no interactive Q&A).
     */
    public static LatticeResult pruneLattice(
            List<String> attributes,
            List<Set<String>> extractedFds,
            Function<Set<String>, NodeStatus> evaluateFn) {

        if (extractedFds.isEmpty()) {
            return new LatticeResult(new HashMap<>(), new HashSet<>(), 0,
                    new ArrayList<>(new java.util.TreeSet<>(attributes)));
        }

        List<String> attrs = new ArrayList<>(new java.util.TreeSet<>(attributes));
        int n = attrs.size();
        Map<Set<String>, NodeStatus> status = new LinkedHashMap<>();

        // ── Step 1: evaluate seed nodes ──────────────────────────────────────
        Set<Set<String>> extracted = new HashSet<>();
        for (Set<String> lhs : extractedFds) {
            extracted.add(new HashSet<>(lhs));
        }

        List<Set<String>> sortedSeeds = new ArrayList<>(extracted);
        sortedSeeds.sort(Comparator.<Set<String>>comparingInt(s -> s.size())
                .thenComparing(s -> s.stream().sorted().collect(Collectors.joining(","))));

        for (Set<String> lhs : sortedSeeds) {
            status.put(new HashSet<>(lhs), evaluateFn.apply(lhs));
        }

        // ── Step 2: compute grey nodes ────────────────────────────────────────
        // A grey node is a strict subset of a seed that is not itself a seed.
        Set<Set<String>> grey = new HashSet<>();
        for (Set<String> lhs : extracted) {
            buildProperSubsets(lhs, grey, extracted);
        }

        int startLevel = extracted.stream().mapToInt(Set::size).min().orElse(1);

        // ── Step 3: traverse level by level ──────────────────────────────────
        for (int level = startLevel + 1; level <= n; level++) {
            for (Set<String> node : combinations(attrs, level)) {
                if (status.containsKey(node)) continue; // already evaluated as seed

                List<Set<String>> parents = directParents(node);

                boolean genuineParent = parents.stream()
                        .anyMatch(p -> status.get(p) == NodeStatus.GENUINE);
                boolean reachable = parents.stream()
                        .anyMatch(p -> status.get(p) == NodeStatus.FAKE
                                || grey.contains(p));

                if (genuineParent) {
                    status.put(new HashSet<>(node), NodeStatus.GENUINE);
                } else if (reachable) {
                    status.put(new HashSet<>(node), evaluateFn.apply(node));
                }
                // else: unreachable — skip
            }
        }

        return new LatticeResult(status, grey, startLevel, attrs);
    }

    /**
     * Extract the maximal antichain of fake LHS sets from lattice results.
     *
     * An element is in the antichain if no already-collected (larger) fake set
     * is a superset of it. Iterates from the largest level down so larger fake
     * sets take priority, producing the set of fake LHSs with no fake superset.
     */
    public static List<Set<String>> collectFakeLhs(LatticeResult result) {
        List<Set<String>> fakeNodes = result.status.entrySet().stream()
                .filter(e -> e.getValue() == NodeStatus.FAKE)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        if (fakeNodes.isEmpty()) return Collections.emptyList();

        int maxLevel = fakeNodes.stream().mapToInt(Set::size).max().orElse(0);
        List<Set<String>> collected = new ArrayList<>();

        for (int level = maxLevel; level >= result.startLevel; level--) {
            final int lvl = level;
            List<Set<String>> candidates = fakeNodes.stream()
                    .filter(s -> s.size() == lvl)
                    .sorted(Comparator.comparing(
                            s -> s.stream().sorted().collect(Collectors.joining(","))))
                    .collect(Collectors.toList());

            for (Set<String> node : candidates) {
                // Include only if no already-collected node is a superset
                boolean dominated = collected.stream().anyMatch(c -> c.containsAll(node));
                if (!dominated) {
                    collected.add(node);
                }
            }
        }

        return collected;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Add all proper non-empty subsets of lhs that are not in extracted to grey. */
    private static void buildProperSubsets(Set<String> lhs, Set<Set<String>> grey,
                                            Set<Set<String>> extracted) {
        List<String> items = new ArrayList<>(lhs);
        for (int size = 1; size < lhs.size(); size++) {
            for (Set<String> subset : combinations(items, size)) {
                if (!extracted.contains(subset)) {
                    grey.add(subset);
                }
            }
        }
    }

    /** Returns all direct parents (one element removed) of a lattice node. */
    private static List<Set<String>> directParents(Set<String> node) {
        List<Set<String>> parents = new ArrayList<>(node.size());
        for (String attr : node) {
            Set<String> parent = new HashSet<>(node);
            parent.remove(attr);
            parents.add(parent);
        }
        return parents;
    }

    /** Generate all size-k subsets of items as HashSet instances. */
    static List<Set<String>> combinations(List<String> items, int size) {
        List<Set<String>> result = new ArrayList<>();
        combineHelper(items, size, 0, new ArrayList<>(), result);
        return result;
    }

    private static void combineHelper(List<String> items, int size, int start,
                                       List<String> current, List<Set<String>> result) {
        if (current.size() == size) {
            result.add(new HashSet<>(current));
            return;
        }
        for (int i = start; i < items.size(); i++) {
            current.add(items.get(i));
            combineHelper(items, size, i + 1, current, result);
            current.remove(current.size() - 1);
        }
    }
}
