package succinctness.web;

import java.util.List;

/**
 * Response DTO for succinctness results.
 */
public class SuccinctnessResultDto {
    public List<Integer> values;
    public int attributeID;
    public double score;

    public SuccinctnessResultDto() {
    }

    public SuccinctnessResultDto(List<Integer> values, int attributeID, double score) {
        this.values = values;
        this.attributeID = attributeID;
        this.score = score;
    }
}
