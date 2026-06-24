package cz.cuni.matfyz.algorithms.depminer.model;

import java.util.List;

/**
 * Persisted alongside FD results in the ArmstrongData DB column.
 * Stores the abstract Armstrong relation (integer-encoded) and the ordered
 * column names so the negative-examples endpoint can reconstruct and extend it
 * without re-running DepMiner.
 */
public class ArmstrongStorageDto {

    private List<String> columnNames;
    private List<int[]> abstractAR;

    public ArmstrongStorageDto() {}

    public ArmstrongStorageDto(List<String> columnNames, List<int[]> abstractAR) {
        this.columnNames = columnNames;
        this.abstractAR = abstractAR;
    }

    public List<String> getColumnNames() { return columnNames; }
    public void setColumnNames(List<String> columnNames) { this.columnNames = columnNames; }

    public List<int[]> getAbstractAR() { return abstractAR; }
    public void setAbstractAR(List<int[]> abstractAR) { this.abstractAR = abstractAR; }
}
