package evaluationpatterns.algorithm;

import java.util.List;
import java.util.Set;

/**
 * Penalty and bonus rules for fake/coincidental FD detection (patterns.md sections 6–7).
 *
 * Each method takes raw metric values and returns integer fake-risk points (all based on intuition).
 * Positive values increase FakeRiskScore (more suspicious); negative values reduce it.
 * The caller sums all results into a final FakeRiskScore.
 */
public final class FdPenaltyRules {

    private static final Set<String> IDENTIFIER_KEYWORDS =
            Set.of("id", "code", "ref", "key", "number", "identifier");

    private FdPenaltyRules() {}

    public static boolean isIdentifierLike(String name) {
        String lower = name.toLowerCase();
        return IDENTIFIER_KEYWORDS.stream().anyMatch(lower::contains);
    }

    // ── Penalty rules ────────────────────────────────────────────────────────

    /** F1: +40 if DR >= 0.98 AND AvgECS <= 1.03 (key-like LHS). */
    public static int penalizeKeyLikeLhs(double dr, double avgEcs) {
        return (dr >= 0.98 && avgEcs <= 1.03) ? 40 : 0;
    }

    /** F2: +10 if has continuous; +25 more if DR >= 0.90. Max +35. */
    public static int penalizeContinuousLhs(boolean hasContinuous, double dr) {
        if (!hasContinuous) return 0;
        return dr >= 0.90 ? 35 : 10;
    }

    /** F3: +10 if size >= 3; +20 more if size >= 4. Max +30. */
    public static int penalizeLhsSize(int lhsSize) {
        int points = 0;
        if (lhsSize >= 3) points += 10;
        if (lhsSize >= 4) points += 20;
        return points;
    }

    /** F4: tiered penalty for low AvgECS (little repeated evidence). */
    public static int penalizeLowRepeatedEvidence(double avgEcs) {
        if (avgEcs <= 1.05) return 35;
        if (avgEcs <= 1.25) return 20;
        if (avgEcs <= 1.50) return 10;
        return 0;
    }

    /** F5: tiered penalty for low coverage. */
    public static int penalizeWeakCoverage(double coverage) {
        if (coverage < 0.50) return 20;
        if (coverage < 0.70) return 10;
        return 0;
    }

    /**
     * F6: +10 if RHS looks like an identifier but no LHS column is an identifier.
     * No penalty when LHS already contains an identifier-like column (key→key mapping).
     */
    public static int penalizeIdentifierLikeRhs(String rhs, List<String> lhs) {
        if (!isIdentifierLike(rhs)) return 0;
        if (lhs.stream().anyMatch(FdPenaltyRules::isIdentifierLike)) return 0;
        return 10;
    }

    // ── Bonus rules ──────────────────────────────────────────────────────────

    /** G1: bonus for high AvgECS (strong repeated evidence). */
    public static int bonusRepeatedEvidence(double avgEcs) {
        if (avgEcs >= 5.0) return -35;
        if (avgEcs >= 2.0) return -20;
        return 0;
    }

    /** G2: bonus for low-to-medium LHS distinctness. */
    public static int bonusLowDistinctness(double dr) {
        if (dr <= 0.50) return -25;
        if (dr <= 0.70) return -15;
        if (dr <= 0.85) return -5;
        return 0;
    }

    /** G3: bonus for string/format transformation evidence. */
    public static int bonusTransformationEvidence(double transformationConfidence) {
        if (transformationConfidence >= 0.95) return -40;
        if (transformationConfidence >= 0.80) return -25;
        return 0;
    }

    /** G4: bonus for reference, synonym, or ontology evidence. */
    public static int bonusReferenceEvidence(double referenceConfidence) {
        if (referenceConfidence >= 0.95) return -35;
        if (referenceConfidence >= 0.80) return -20;
        return 0;
    }

    /** G5: adjustment based on overall LHS spuriousness risk level. */
    public static int adjustSpuriousnessRisk(String riskLevel) {
        return switch (riskLevel.toUpperCase()) {
            case "LOW"      -> -10;
            case "MEDIUM"   ->   0;
            case "HIGH"     ->  20;
            case "CRITICAL" ->  35;
            default         ->   0;
        };
    }
}
