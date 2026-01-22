package cz.cuni.matfyz.algorithms.depminer.model;

import java.io.Serializable;

/**
 * Simple DTO representing a functional dependency in two formats:
 *  - names: human-readable form using column names
 *  - indices: original form using column indices
 */
public class _FunctionalDependencyOutput implements Serializable {

    private static final long serialVersionUID = 1L;

    private String names;
    private String indices;

    public _FunctionalDependencyOutput() {
    }

    public _FunctionalDependencyOutput(String names, String indices) {
        this.names = names;
        this.indices = indices;
    }

    public String getNames() {
        return names;
    }

    public void setNames(String names) {
        this.names = names;
    }

    public String getIndices() {
        return indices;
    }

    public void setIndices(String indices) {
        this.indices = indices;
    }

    @Override
    public String toString() {
        return "_FunctionalDependencyOutput{" +
                "names='" + names + '\'' +
                ", indices='" + indices + '\'' +
                '}';
    }
}
