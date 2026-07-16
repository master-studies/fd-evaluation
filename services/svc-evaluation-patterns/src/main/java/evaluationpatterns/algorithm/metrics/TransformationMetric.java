package evaluationpatterns.algorithm.metrics;

import evaluationpatterns.algorithm.CsvDataset;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Transformation Confidence metric (patterns.md section 7 / G3).
 *
 * Detects string/format transformations between LHS and RHS:
 *   abbreviation, case conversion, initials, character replacement, concatenation.
 */
public class TransformationMetric {

    private static final double MIN_VALID_FRACTION = 0.3;
    private static final double MATCH_THRESHOLD = 0.80;

    public double compute(CsvDataset data, List<String> lhs, String rhs) {
        if (lhs.isEmpty() || !data.hasColumn(rhs)) return 0.0;
        for (String col : lhs) {
            if (!data.hasColumn(col)) return 0.0;
        }

        if (lhs.size() == 1) {
            List<String> lhsVals = getColumnValues(data, lhs.get(0));
            List<String> rhsVals = getColumnValues(data, rhs);
            double fwd = detectConfidence(lhsVals, rhsVals);
            double rev = detectConfidence(rhsVals, lhsVals);
            return Math.max(fwd, rev);
        }

        return analyzeMultiColumn(data, lhs, rhs);
    }

    private double analyzeMultiColumn(CsvDataset data, List<String> lhs, String rhs) {
        double best = 0.0;

        // Check concatenation with common delimiters
        for (String delim : new String[]{"_", "-", " ", "", "|", ","}) {
            double conf = checkConcatenation(data, lhs, rhs, delim);
            if (conf > best) best = conf;
        }
        if (best >= MATCH_THRESHOLD) return best;

        // Check if any single LHS column transforms to/from RHS
        List<String> rhsVals = getColumnValues(data, rhs);
        for (String col : lhs) {
            List<String> colVals = getColumnValues(data, col);
            double fwd = detectConfidence(colVals, rhsVals);
            double rev = detectConfidence(rhsVals, colVals);
            best = Math.max(best, Math.max(fwd, rev));
        }

        return best;
    }

    private double checkConcatenation(CsvDataset data, List<String> lhs, String rhs, String delim) {
        int matches = 0;
        int valid = 0;
        for (String[] row : data.getRows()) {
            StringBuilder sb = new StringBuilder();
            boolean anyNull = false;
            for (int i = 0; i < lhs.size(); i++) {
                String val = data.get(row, lhs.get(i));
                if (val == null) { anyNull = true; break; }
                if (i > 0) sb.append(delim);
                sb.append(val);
            }
            if (anyNull) continue;
            valid++;
            String rhsVal = data.get(row, rhs);
            if (rhsVal != null && rhsVal.equals(sb.toString())) matches++;
        }
        return valid == 0 ? 0.0 : (double) matches / valid;
    }

    /**
     * Checks a series of single-column transformation patterns between src and tgt values.
     * Returns the highest matching confidence found, or 0.0 if no pattern reaches threshold.
     */
    private double detectConfidence(List<String> srcVals, List<String> tgtVals) {
        // Filter rows where both sides are non-null
        List<String> src = new ArrayList<>();
        List<String> tgt = new ArrayList<>();
        int total = Math.min(srcVals.size(), tgtVals.size());
        for (int i = 0; i < total; i++) {
            if (srcVals.get(i) != null && tgtVals.get(i) != null) {
                src.add(srcVals.get(i));
                tgt.add(tgtVals.get(i));
            }
        }

        if (src.isEmpty() || src.size() < total * MIN_VALID_FRACTION) return 0.0;
        int n = src.size();

        // 1. Abbreviation: tgt is first N chars of src (uppercase)
        for (int abbrevLen : new int[]{3, 4, 2, 5}) {
            int matches = 0;
            for (int i = 0; i < n; i++) {
                if (src.get(i).length() >= abbrevLen) {
                    String abbrev = src.get(i).substring(0, abbrevLen).toUpperCase();
                    if (abbrev.equals(tgt.get(i).toUpperCase())) matches++;
                }
            }
            if (matches > n * MATCH_THRESHOLD) return (double) matches / n;
        }

        // 2. Case conversion (normalised: accents stripped, separators unified)
        int caseMatches = 0;
        for (int i = 0; i < n; i++) {
            String sn = normalizeForCase(src.get(i));
            String tn = normalizeForCase(tgt.get(i));
            if (sn.equals(tn) && !src.get(i).equals(tgt.get(i))) caseMatches++;
        }
        if (caseMatches > n * MATCH_THRESHOLD) return (double) caseMatches / n;

        // 3. Initials: tgt is the first character of src
        int initialMatches = 0;
        for (int i = 0; i < n; i++) {
            if (!src.get(i).isEmpty() &&
                    String.valueOf(src.get(i).charAt(0)).toUpperCase()
                          .equals(tgt.get(i).toUpperCase())) {
                initialMatches++;
            }
        }
        if (initialMatches > n * 0.85) return (double) initialMatches / n;

        // 4. Character replacement: _ and - → space
        int replMatches = 0;
        for (int i = 0; i < n; i++) {
            String transformed = src.get(i).replace('_', ' ').replace('-', ' ');
            if (transformed.equals(tgt.get(i))) replMatches++;
        }
        if (replMatches > n * MATCH_THRESHOLD) return (double) replMatches / n;

        // 5. Word extraction: every token of tgt appears among the tokens of src
        //    after normalisation (e.g. albert_park ⊆ "Albert Park Grand Prix
        //    Circuit"). Token-level matching, not substring, so numeric fragments
        //    and short tokens cannot match accidentally.
        int extractMatches = 0;
        for (int i = 0; i < n; i++) {
            Set<String> srcTokens =
                    new HashSet<>(Arrays.asList(normalizeForCase(src.get(i)).split("_")));
            String[] tgtTokens = normalizeForCase(tgt.get(i)).split("_");
            boolean contained = tgtTokens.length > 0 && !tgtTokens[0].isEmpty();
            for (String t : tgtTokens) {
                if (t.length() < 2 || !srcTokens.contains(t)) { contained = false; break; }
            }
            if (contained) extractMatches++;
        }
        if (extractMatches > n * MATCH_THRESHOLD) return (double) extractMatches / n;

        return 0.0;
    }

    private List<String> getColumnValues(CsvDataset data, String col) {
        List<String> vals = new ArrayList<>(data.size());
        for (String[] row : data.getRows()) {
            vals.add(data.get(row, col));
        }
        return vals;
    }

    /** Lowercase, strip accents, unify separators, remove non-alphanumeric. */
    private String normalizeForCase(String s) {
        String result = s.toLowerCase();
        result = Normalizer.normalize(result, Normalizer.Form.NFD);
        result = result.replaceAll("\\p{InCombiningDiacriticalMarks}", "");
        result = result.replace(' ', '_').replace('-', '_');
        result = result.replaceAll("[^a-z0-9_]", "");
        result = result.replaceAll("_+", "_").replaceAll("^_|_$", "");
        return result;
    }
}
