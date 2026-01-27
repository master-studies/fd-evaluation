package genuineness.web;

import java.util.List;

/**
 * Request DTO for a functional dependency group.
 * JSON structure: { "values": [1,2,3] ,"attributeID": 5}
 */
public class FunctionalDependencyGroupDto {
    public List<Integer> values;
    public int attributeID;

    public FunctionalDependencyGroupDto() {
    }

    public FunctionalDependencyGroupDto(List<Integer> values, int attributeID) {
        this.values = values;
        this.attributeID = attributeID;
    }
}