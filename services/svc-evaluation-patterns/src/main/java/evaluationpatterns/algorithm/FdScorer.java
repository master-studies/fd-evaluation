package evaluationpatterns.algorithm;

import java.util.List;

/**
 * FakeRiskScore computation and FD classification (patterns.md sections 8–9).
 */
public final class FdScorer {

    private FdScorer() {}

    public enum Decision {
        GENUINE,          // hard accept
        FAKE,             // hard reject
        LIKELY_GENUINE,   // score <= 0
        PROBABLY_GENUINE, // score 1–24
        SUSPICIOUS,       // score 25–49
        LIKELY_FAKE,      // score >= 50
        NOT_EXACT         // genuineness < 1.0, filtered before scoring
    }

    public record ClassificationResult(int score, Decision decision) {}

    // ── Risk level ───────────────────────────────────────────────────────────

    /**
     * Summarise LHS risk factors into LOW / MEDIUM / HIGH / CRITICAL.
     *
     *   DR >= 0.98      +2   (almost key-like)
     *   DR >= 0.90      +1   (highly unique)
     *   hasContinuous   +1
     *   lhsSize >= 4    +2
     *   lhsSize >= 3    +1   (only when < 4)
     *   coverage < 0.50 +1
     *
     *   Total → CRITICAL (>=4), HIGH (>=2), MEDIUM (>=1), LOW (0)
     */
    public static String computeRiskLevel(double dr, boolean hasCont, int lhsSize, double coverage) {
        int risk = 0;
        if (dr >= 0.98)      risk += 2;
        else if (dr >= 0.90) risk += 1;
        if (hasCont)         risk += 1;
        if (lhsSize >= 4)    risk += 2;
        else if (lhsSize >= 3) risk += 1;
        if (coverage < 0.50) risk += 1;

        if (risk >= 4) return "CRITICAL";
        if (risk >= 2) return "HIGH";
        if (risk >= 1) return "MEDIUM";
        return "LOW";
    }

    // ── Score ────────────────────────────────────────────────────────────────

    public static int computeFakeRiskScore(
            double dr, double avgEcs, boolean hasCont, int lhsSize, double coverage,
            String rhs, List<String> lhs,
            double tConf, double rConf, String riskLevel) {

        int score = 0;
        score += FdPenaltyRules.penalizeKeyLikeLhs(dr, avgEcs);
        score += FdPenaltyRules.penalizeContinuousLhs(hasCont, dr);
        score += FdPenaltyRules.penalizeLhsSize(lhsSize);
        score += FdPenaltyRules.penalizeLowRepeatedEvidence(avgEcs);
        score += FdPenaltyRules.penalizeWeakCoverage(coverage);
        score += FdPenaltyRules.penalizeIdentifierLikeRhs(rhs, lhs);
        score += FdPenaltyRules.bonusRepeatedEvidence(avgEcs);
        score += FdPenaltyRules.bonusLowDistinctness(dr);
        score += FdPenaltyRules.bonusTransformationEvidence(tConf);
        score += FdPenaltyRules.bonusReferenceEvidence(rConf);
        score += FdPenaltyRules.adjustSpuriousnessRisk(riskLevel);
        return score;
    }

    // ── Label ────────────────────────────────────────────────────────────────

    /** Map a raw FakeRiskScore to a Decision label (section 8 table). */
    public static Decision classifyScore(int score) {
        if (score <= 0)  return Decision.LIKELY_GENUINE;
        if (score <= 24) return Decision.PROBABLY_GENUINE;
        if (score <= 49) return Decision.SUSPICIOUS;
        return Decision.LIKELY_FAKE;
    }

    // ── Hard overrides (section 9) ───────────────────────────────────────────

    /**
     * Section 9.1 — strong genuine evidence overrides the score.
     *
     *   Condition A (metric-based):
     *     DR <= 0.70 AND ECS >= 2.0 AND Coverage >= 0.70 AND Risk in {LOW, MEDIUM}
     *   Condition B (evidence-based):
     *     TransformationConfidence >= 0.85 OR ReferenceConfidence >= 0.85
     */
    public static boolean isHardAccept(double dr, double avgEcs, double coverage,
                                        String riskLevel, double tConf, double rConf) {
        if (dr <= 0.70 && avgEcs >= 2.0 && coverage >= 0.70
                && (riskLevel.equals("LOW") || riskLevel.equals("MEDIUM"))) {
            return true;
        }
        return tConf >= 0.85 || rConf >= 0.85;
    }

    /**
     * Section 9.2 — strong fake evidence overrides the score.
     *
     *   Rule 1: DR >= 0.98 AND ECS <= 1.03 AND trans < 0.80 AND ref < 0.80
     *   Rule 2: hasContinuous AND DR >= 0.90 AND trans < 0.80
     */
    public static boolean isHardReject(double dr, double avgEcs, boolean hasCont,
                                        double tConf, double rConf) {
        if (dr >= 0.98 && avgEcs <= 1.03 && tConf < 0.80 && rConf < 0.80) return true;
        if (hasCont && dr >= 0.90 && tConf < 0.80) return true;
        return false;
    }

    // ── Full pipeline ─────────────────────────────────────────────────────────

    /**
     * Full classification for exact FDs (genuineness = 1.0).
     * Non-exact FDs must be filtered before calling this method.
     */
    public static ClassificationResult classifyFd(
            double dr, double avgEcs, boolean hasCont, int lhsSize, double coverage,
            String rhs, List<String> lhs, double tConf, double rConf) {

        String riskLevel = computeRiskLevel(dr, hasCont, lhsSize, coverage);
        int score = computeFakeRiskScore(dr, avgEcs, hasCont, lhsSize, coverage,
                rhs, lhs, tConf, rConf, riskLevel);

        if (isHardAccept(dr, avgEcs, coverage, riskLevel, tConf, rConf)) {
            return new ClassificationResult(score, Decision.GENUINE);
        }
        if (isHardReject(dr, avgEcs, hasCont, tConf, rConf)) {
            return new ClassificationResult(score, Decision.FAKE);
        }
        return new ClassificationResult(score, classifyScore(score));
    }
}
