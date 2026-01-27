package coverage.web;

import java.util.List;

/**
 * Response DTO for succinctness results.
 */
public class CoverageResultDto {
    public List<Integer> values;
    public int attributeID;
    public double score;

    public CoverageResultDto() {
    }

    public CoverageResultDto(List<Integer> values, int attributeID, double score) {
        this.values = values;
        this.attributeID = attributeID;
        this.score = score;
    }
}
