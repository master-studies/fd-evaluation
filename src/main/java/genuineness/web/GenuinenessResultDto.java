package genuineness.web;

import java.util.List;

/**
 * Response DTO for succinctness results.
 */
public class GenuinenessResultDto {
    public List<Integer> values;
    public int attributeID;
    public double score;

    public GenuinenessResultDto() {
    }

    public GenuinenessResultDto(List<Integer> values, int attributeID, double score) {
        this.values = values;
        this.attributeID = attributeID;
        this.score = score;
    }
}
