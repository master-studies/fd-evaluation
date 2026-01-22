package entropy;

import java.util.*;

public class FunctionalDependency {

    private final Set<Integer> leftSide;
    private final int[] leftSideArray;
    private final Set<Integer> rightSide;
    private final int simpleRightSide;

    FunctionalDependency(Set<Integer> leftSide, Set<Integer> rightSide) {
        this.leftSide = leftSide;
        List<Integer> leftSideList = new ArrayList<>(leftSide);
        leftSideList.sort(null);
        this.leftSideArray = leftSideList.stream().mapToInt(Integer::intValue).toArray();
        this.rightSide = rightSide;
        this.simpleRightSide = rightSide.size() == 1 ? rightSide.iterator().next() : -1;
    }

    Set<Integer> getLeftSide() {
        return Set.copyOf(leftSide);
    }

    Set<Integer> getRightSide() {
        return Set.copyOf(rightSide);
    }

    boolean isTrivial() {
        return leftSide.containsAll(rightSide);
    }

    boolean covers(FunctionalDependency other) {
        return rightSide.containsAll(other.rightSide)
                && other.rightSide.containsAll(rightSide)
                && leftSide.containsAll(other.leftSide);
    }

    int[] getLeftSideArray() {
        return Arrays.copyOf(leftSideArray, leftSideArray.length);
    }

    int getSimpleRightSide() {
        return simpleRightSide;
    }

    List<FunctionalDependency> getSimpleFunctionalDependencies() {
        List<FunctionalDependency> simpleFds = new ArrayList<>();

        for (int i : rightSide) {
            simpleFds.add(new FunctionalDependency(Set.copyOf(leftSide), Set.of(i)));
        }

        return simpleFds;
    }

    Set<Integer> getAttributeIndices() {
        Set<Integer> attr = new HashSet<>();
        attr.addAll(leftSide);
        attr.addAll(rightSide);
        return attr;
    }

    FunctionalDependency convertToSubtable(int[] colsToDelete) {
        Set<Integer> left = new HashSet<>();
        Set<Integer> right = new HashSet<>();

        for (int x : leftSide) {
            int xNew = x;

            for (int col : colsToDelete) {
                if (col < x) {
                    xNew--;
                }
            }

            left.add(xNew);
        }

        for (int x : rightSide) {
            int xNew = x;

            for (int col : colsToDelete) {
                if (col < x) {
                    xNew--;
                }
            }

            right.add(xNew);
        }

        return new FunctionalDependency(left, right);
    }

    String leftSideToString() {
        StringBuilder builder = new StringBuilder();
        List<Integer> left = new ArrayList<>(leftSide);
        left.sort(null);
        builder.append(left.get(0) + 1);

        for (int i = 1; i < left.size(); i++) {
            builder.append(",").append(left.get(i) + 1);
        }

        return builder.toString();
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        List<Integer> left = new ArrayList<>(leftSide);
        List<Integer> right = new ArrayList<>(rightSide);
        left.sort(null);
        right.sort(null);
        builder.append(left.get(0) + 1);

        for (int i = 1; i < left.size(); i++) {
            builder.append(",").append(left.get(i) + 1);
        }

        builder.append("->");
        builder.append(right.get(0) + 1);

        for (int i = 1; i < right.size(); i++) {
            builder.append(",").append(right.get(i) + 1);
        }

        return builder.toString();
    }

}
