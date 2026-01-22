package entropy;

import java.util.*;
import java.util.stream.Collectors;
import java.util.Arrays;

public class Computation {

    private final int[][] table;
    private final int rows;
    private final int cols;
    private final int size;
    private final List<FunctionalDependency> funcDeps = new ArrayList<>();
    private boolean showProcess;
    private final boolean identifyOnes;
    private final boolean considerSubtables;
    private final int randomisation;
    private long processedCount = 0;
    private static int toCompute;
    private static int processed = -1;

    Computation(int[][] table, boolean identifyOnes, boolean considerSubtables, int randomisation) {
        this.table = table;
        rows = table.length;
        cols = rows == 0 ? 0 : table[0].length;
        size = rows * cols;
        this.identifyOnes = identifyOnes;
        this.considerSubtables = considerSubtables;
        this.randomisation = randomisation;
    }

    void enableProcessedCount() {
        showProcess = true;
    }

    private void addFuncDep(FunctionalDependency fd) {
        funcDeps.add(fd);
    }

    void addFuncDepWithCheck(FunctionalDependency fd) {
        String errorStr = String.format("attribute index out of bounds: in fd %s, number of attributes: %d", fd, cols);

        if (fd.getSimpleRightSide() >= cols) {
            throw new IllegalArgumentException(errorStr);
        }

        for (int leftAttr : fd.getLeftSide()) {
            if (leftAttr >= cols) {
                throw new IllegalArgumentException(errorStr);
            }
        }

        if (funcDepIsViolated(table, fd)) {
            throw new IllegalArgumentException(String.format("fd \"%s\" not fulfilled", fd));
        }

        addFuncDep(fd);
    }

    String getFdsString() {
        int remaining = funcDeps.size();

        if (remaining == 0) {
            return "none";
        }

        StringBuilder builder = new StringBuilder();

        for (FunctionalDependency fd : funcDeps) {
            builder.append(fd.toString()).append(--remaining > 0 ? ", " : "");
        }

        return builder.toString();
    }

    boolean coversOtherFuncDep(FunctionalDependency fd) {
        for (FunctionalDependency funcDep : funcDeps) {
            if (fd.covers(funcDep)) {
                return true;
            }
        }

        return false;
    }

    void addTransitiveClosure() {
        List<FunctionalDependency> combinedFds;
        boolean terminate = false;

        while (!terminate) {
            combinedFds = getCombinedFds();

            if (!deriveFd(combinedFds)) {
                terminate = true;
            }
        }
    }

    private boolean deriveFd(List<FunctionalDependency> combinedFds) {
        for (FunctionalDependency fd2 : combinedFds) {
            for (FunctionalDependency fd1 : combinedFds) {
                if (fd1.getRightSide().containsAll(fd2.getLeftSide())) {
                    FunctionalDependency derivedFd = new FunctionalDependency(fd1.getLeftSide(), fd2.getRightSide());

                    if (!derivedFd.isTrivial()) {
                        if (addSimpleFuncDeps(derivedFd.getSimpleFunctionalDependencies())) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    private boolean addSimpleFuncDeps(List<FunctionalDependency> simpleFds) {
        boolean added = false;

        for (FunctionalDependency fd : simpleFds) {
            if (!(fd.isTrivial() || coversOtherFuncDep(fd))) {
                addFuncDep(fd);
                added = true;
            }
        }

        return added;
    }

    private List<FunctionalDependency> getCombinedFds() {
        Map<String, List<Integer>> fdMap = new HashMap<>();

        for (FunctionalDependency fd : funcDeps) {
            String left = fd.leftSideToString();

            if (fdMap.containsKey(left)) {
                List<Integer> right = new ArrayList<>(fdMap.get(left));
                right.add(fd.getSimpleRightSide());
                fdMap.put(left, right);
            } else {
                fdMap.put(left, List.of(fd.getSimpleRightSide()));
            }
        }

        List<FunctionalDependency> combinedFds = new ArrayList<>();

        for (Map.Entry<String, List<Integer>> entry : fdMap.entrySet()) {
            Set<Integer> leftSide = Arrays.stream(entry.getKey().split(",")).mapToInt(x -> Integer.parseInt(x) - 1).boxed().collect(Collectors.toSet());
            combinedFds.add(new FunctionalDependency(leftSide, Set.copyOf(entry.getValue())));
        }

        return combinedFds;
    }

    double[][] getInformationContentMatrix() {
        int rows = table.length;

        if (rows == 0) {
            return new double[0][];
        }

        if (considerSubtables) {
            boolean[] isFdsRightSide = new boolean[cols];

            for (FunctionalDependency funcDep : funcDeps) {
                isFdsRightSide[funcDep.getSimpleRightSide()] = true;
            }

            int[] redundantRows = getRedundantRows(isFdsRightSide);
            int[] redundantCols = getRedundantCols();
            Computation subtableComputation = getSubtableComputation(redundantRows, redundantCols);
            return embedSubtableComputation(subtableComputation.getInformationContentMatrix(), redundantRows, redundantCols);
        }

        double[][] matrix = new double[rows][cols];
        toCompute = size;

        if (identifyOnes) {
            boolean[] isFdsRightSide = new boolean[cols];

            for (FunctionalDependency funcDep : funcDeps) {
                isFdsRightSide[funcDep.getSimpleRightSide()] = true;
            }

            for (int i = 0; i < size; i++) {
                if (isOne(i, isFdsRightSide)) {
                    matrix[i / cols][i % cols] = 1;
                    toCompute--;
                }
            }
        }
        
        for (int i = 0; i < size; i++) {
            if (matrix[i / cols][i % cols] == 1) {
                continue;
            }

            matrix[i / cols][i % cols] = randomisation > 0 ? informationContentRandomised(i) : informationContent(i);

            if (showProcess) {
                printProcessedRatio(randomisation > 0 ? randomisation : Math.pow(2, (size - 1)));

                if (i == size - 1) {
                    System.out.println();
                }
            }
        }

        return matrix;
    }

    private double informationContent(int position) {
        return informationContentRec(position, new boolean[]{});
    }

    private double informationContentRandomised(int position) {
        boolean[] arr = new boolean[size - 1];
        double sum = 0;
        Random random = new Random();

        for (int i = 0; i < randomisation; i++) {
            for (int j = 0; j < arr.length; j++) {
                arr[j] = random.nextBoolean();
            }
            sum += informationContentRec(position, arr);
        }

        return sum / randomisation;
    }

    private double informationContentRec(int position, boolean[] arr) {
        if (arr.length == size - 1) {
            double result = entropy(position, arr);

            if (showProcess) {
                printProcessedRatio(randomisation > 0 ? randomisation : (Math.pow(2, size - 1)));
            }

            return result;
        }

        return (informationContentRec(position, concat(true, arr)) + informationContentRec(position, concat(false, arr))) / 2;
    }

    private double entropy(int position, boolean[] hasValue) {
        int[][] tableTmp = createTable(position, hasValue);
        tableTmp[position / cols][position % cols] = getMaxEntry(tableTmp) + 1;
        return checkFuncDeps(tableTmp) ? 1 : 0;
    }

    private boolean checkFuncDeps(int[][] table) {
        for (FunctionalDependency funcDep : funcDeps) {
            if (funcDepIsViolated(table, funcDep)) {
                return false;
            }
        }

        return true;
    }

    private boolean funcDepIsViolated(int[][] table, FunctionalDependency funcDep) {
        Map<String, Integer> relevantCols = new HashMap<>();

        for (int row = 0; row < rows; row++) {
            int[] leftValues = getLeftValues(row, funcDep, table);

            if (arrayContainsInt(leftValues, 0)) {
                continue;
            }

            int rightValue = getRightValue(row, funcDep, table);

            if (rightValue == 0) {
                continue;
            }

            String leftValuesStr = Arrays.toString(leftValues);

            if (relevantCols.containsKey(leftValuesStr)) {
                if (relevantCols.get(leftValuesStr) != rightValue) {
                    return true;
                }
            } else {
                relevantCols.put(leftValuesStr, rightValue);
            }
        }

        return false;
    }

    private int[][] createTable(int position, boolean[] hasValue) {
        int[][] tableTmp = new int[rows][cols];
        int boolPos = 0;

        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                if (boolPos < position) {
                    tableTmp[i][j] = hasValue[boolPos] ? table[i][j] : 0;
                } else if (boolPos > position) {
                    tableTmp[i][j] = hasValue[boolPos - 1] ? table[i][j] : 0;
                }

                boolPos++;
            }
        }

        return tableTmp;
    }

    private Computation getSubtableComputation(int[] rowsToDelete, int[] colsToDelete) {
        int[][] newTable = new int[rows - rowsToDelete.length][cols - colsToDelete.length];
        int iOld = 0;

        for (int i = 0; i < newTable.length; i++) {
            while (arrayContainsInt(rowsToDelete, iOld)) {
                iOld++;
            }

            int jOld = 0;

            for (int j = 0; j < newTable[i].length; j++) {
                while (arrayContainsInt(colsToDelete, jOld)) {
                    jOld++;
                }

                newTable[i][j] = table[iOld][jOld++];
            }

            iOld++;
        }

        Computation computation = new Computation(newTable, identifyOnes, false, randomisation);

        if (showProcess) {
            computation.enableProcessedCount();
        }

        for (FunctionalDependency funcDep : funcDeps) {
            computation.addFuncDep(funcDep.convertToSubtable(colsToDelete));
        }

        return computation;
    }

    private double[][] embedSubtableComputation(double[][] subtable, int[] deletedRows, int[] deletedCols) {
        double[][] entropies = new double[rows][cols];
        int subtableRow = 0;

        for (int i = 0; i < rows; i++) {
            if (arrayContainsInt(deletedRows, i)) {
                for (int j = 0; j < cols; j++) {
                    entropies[i][j] = 1;
                }
            } else {
                int subtableCol = 0;

                for (int j = 0; j < cols; j++) {
                    if (arrayContainsInt(deletedCols, j)) {
                        entropies[i][j] = 1;
                    } else {
                        entropies[i][j] = subtable[subtableRow][subtableCol++];
                    }
                }

                subtableRow++;
            }
        }

        return entropies;
    }

    private int[] getRedundantRows(boolean[] isFdsRightSide) {
        List<Integer> redundantRowsList = new ArrayList<>();

        for (int i = 0; i < rows; i++) {
            if (rowIsOne(i, isFdsRightSide)) {
                redundantRowsList.add(i);
            }
        }

        int[] redundantRows = new int[redundantRowsList.size()];

        for (int i = 0; i < redundantRows.length; i++) {
            redundantRows[i] = redundantRowsList.get(i);
        }

        return redundantRows;
    }

    private boolean rowIsOne(int row, boolean[] isFdsRightSide) {
        int firstPos = row * cols;

        for (int i = firstPos; i < firstPos + cols; i++) {
            if (!isOne(i, isFdsRightSide)) {
                return false;
            }
        }

        return true;
    }

    private boolean isOne(int position, boolean[] isFdsRightSide) {
        int row = position / cols;
        int col = position % cols;

        if (!isFdsRightSide[col]) {
            return true;
        }

        for (FunctionalDependency funcDep : funcDeps) {
            if (funcDep.getSimpleRightSide() == col) {
                int[] leftSideValues = getLeftValues(row, funcDep);

                for (int i = 0; i < rows; i++) {
                    if (i == row) {
                        continue;
                    }

                    if (Arrays.equals(leftSideValues, getLeftValues(i, funcDep))) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    private int[] getLeftValues(int row, FunctionalDependency fd) {
        return getLeftValues(row, fd, table);
    }

    private int[] getLeftValues(int row, FunctionalDependency fd, int[][] table) {
        int[] leftAttrs = fd.getLeftSideArray();
        int l = leftAttrs.length;
        int[] values = new int[l];

        for (int i = 0; i < l; i++) {
            values[i] = table[row][leftAttrs[i]];
        }

        return values;
    }

    private int getRightValue(int row, FunctionalDependency fd, int[][] table) {
        return table[row][fd.getSimpleRightSide()];
    }

    private int[] getRedundantCols() {
        Set<Integer> relevantCols = new HashSet<>();

        for (FunctionalDependency funcDep : funcDeps) {
            relevantCols.addAll(funcDep.getAttributeIndices());
        }

        int[] redundantCols = new int[cols - relevantCols.size()];
        int j = 0;

        for (int i = 0; i < cols; i++) {
            if (!relevantCols.contains(i)) {
                redundantCols[j++] = i;
            }
        }

        return redundantCols;
    }

    private static boolean arrayContainsInt(int[] arr, int val) {
        for (int a : arr) {
            if (a == val) {
                return true;
            }
        }

        return false;
    }

    private static int getMaxEntry(int[][] table) {
        int max = -1;

        for (int[] row : table) {
            for (int cell : row) {
                max = Math.max(max, cell);
            }
        }

        return max;
    }

    private static boolean[] concat(boolean elem, boolean[] arr) {
        boolean[] result = new boolean[arr.length + 1];
        result[0] = elem;
        System.arraycopy(arr, 0, result, 1, arr.length);
        return result;
    }

    private void printProcessedRatio(double iterations) {
        processedCount++;
        int processed_new = (int) (processedCount * 100 / (iterations * toCompute));

        if (processed_new > processed) {
            System.out.print("\033[2K\033[1G");
            System.out.print("Processed: " + (processed = processed_new) + "%");
        }
    }

}
