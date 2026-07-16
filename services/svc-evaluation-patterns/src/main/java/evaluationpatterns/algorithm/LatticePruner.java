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

        System.out.println("[LATTICE] Step 1 – Seeds evaluated:");
        status.forEach((lhs, st) -> System.out.printf("  {%s} -> %s%n",
                lhs.stream().sorted().collect(Collectors.joining(",")), st));

        // ── Step 2: compute grey nodes ────────────────────────────────────────
        // A grey node is a strict subset of a seed that is not itself a seed.
        Set<Set<String>> grey = new HashSet<>();
        for (Set<String> lhs : extracted) {
            buildProperSubsets(lhs, grey, extracted);
        }

        if (!grey.isEmpty()) {
            String greyStr = grey.stream()
                    .map(s -> "{" + s.stream().sorted().collect(Collectors.joining(",")) + "}")
                    .sorted().collect(Collectors.joining(", "));
            System.out.println("[LATTICE] Step 2 – Grey nodes (subsets of seeds): " + greyStr);
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
                    Set<String> trigger = parents.stream()
                            .filter(p -> status.get(p) == NodeStatus.GENUINE)
                            .findFirst().orElse(null);
                    System.out.printf("[LATTICE] Pruned  {%s} -> GENUINE  (via {%s})%n",
                            node.stream().sorted().collect(Collectors.joining(",")),
                            trigger != null ? trigger.stream().sorted().collect(Collectors.joining(",")) : "?");
                } else if (reachable) {
                    NodeStatus ns = evaluateFn.apply(node);
                    status.put(new HashSet<>(node), ns);
                    System.out.printf("[LATTICE] Eval    {%s} -> %s%n",
                            node.stream().sorted().collect(Collectors.joining(",")), ns);
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

        System.out.println("[LATTICE] Step 3 – Antichain (maximal fake LHSs, no fake superset):");
        collected.forEach(s -> System.out.println("  {" + s.stream().sorted().collect(Collectors.joining(",")) + "}"));
        return collected;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

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

    private static List<Set<String>> directParents(Set<String> node) {
        List<Set<String>> parents = new ArrayList<>(node.size());
        for (String attr : node) {
            Set<String> parent = new HashSet<>(node);
            parent.remove(attr);
            parents.add(parent);
        }
        return parents;
    }

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
