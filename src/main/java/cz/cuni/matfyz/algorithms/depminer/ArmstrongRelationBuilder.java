/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package cz.cuni.matfyz.algorithms.depminer;

import static cz.cuni.matfyz.algorithms.depminer.MainApp.FILENAME;
import cz.cuni.matfyz.algorithms.depminer.model._CSVTestCase;
import cz.cuni.matfyz.algorithms.depminer.model._MAX_SET;
import cz.cuni.matfyz.algorithms.depminer.util._BitSetUtil;
import it.unimi.dsi.fastutil.longs.LongList;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 *
 * @author pavel.koupil
 */
public class ArmstrongRelationBuilder {

	private class MyTuple implements Comparable<MyTuple> {

		public String value;

		public int index;

		private MyTuple(String content, int size) {
			this.value = content;
			this.index = size;
		}

		@Override
		public int compareTo(MyTuple o) {
			return value.compareTo(o.value);
		}
	}

	public ArmstrongRelationBuilder() {
	}

	public List<int[]> execute(List<_MAX_SET> maxSets) {
		int columnsCount = maxSets.size();
//		Set<Integer[]> AR = new TreeSet<>();
		Set<int[]> AR = new TreeSet<>(new Comparator<int[]>() {
			@Override
			public int compare(int[] a1, int[] a2) {
				// Compare arrays lexicographically
				int len = Math.min(a1.length, a2.length);
				for (int i = 0; i < len; i++) {
					int cmp = Integer.compare(a1[i], a2[i]);
					if (cmp != 0) {
						return cmp;
					}
				}
				return Integer.compare(a1.length, a2.length);
			}
		});
		for (_MAX_SET maxSet : maxSets) {
			for (int index = 0; index < maxSet.getCombinations().size(); ++index) {
				LongList longList = _BitSetUtil.convertToLongList(maxSet.getCombinations().get(index));

				int[] row = new int[columnsCount];

				longList.forEach(element -> {
					row[element.intValue()] = -1;
				});
//				StringBuilder builder = new StringBuilder();
//				List<Integer> rowList = new ArrayList<>();
				for (int i = 0; i < columnsCount; ++i) {
//					if (row[i] != null) {
					if (row[i] == -1) {
						row[i] = 0;
					} else {
						row[i] = 1;
					}

					if (i < columnsCount - 1) {
//						builder.append(row[i]).append(", ");
//						System.out.print(row[i] + ", ");
					} else {
//						builder.append(row[i]);
//						System.out.print(row[i]);
					}
				}
				AR.add(/*builder.toString()*/row);
//				System.out.println("");
			}
//			System.out.println("");

		}

		List<int[]> ARL = new LinkedList<>(AR);

		int[] row = new int[columnsCount];
		ARL.addFirst(row);

		int lineNumber = 1;
		for (int[] value : ARL) {

			boolean changed = false;
			for (int index = 0; index < value.length; ++index) {
				if (value[index] == 1) {
					value[index] = lineNumber;
					changed = true;
				}
			}
			if (changed) {
				++lineNumber;
			}
		}

		return ARL;

	}

	public List<List<String>> realworldAR(List<int[]> AR, _CSVTestCase input) throws Exception {
		input = new _CSVTestCase(FILENAME, false);	// TODO: REMOVE WHEN CSV_TEST_CASE IMPLEMENTATION IS FIXED
		int columnCount = AR.get(0).length;

		// Step 1: Calculate distinct values required for each column
		int[] distinct = calculateDistinctValues(AR, columnCount);

		// Step 2: Collect unique values for each column from CSV
		List<Set<MyTuple>> uniqueValues = collectUniqueValues(input, columnCount, distinct);

		List<List<String>> uniqueOrderedLists = orderUniqueValues(uniqueValues);

		// Step 3: Ensure each uniqueValues list has the required distinct values
		List<List<String>> uniqueLists = ensureDistinctValues(uniqueOrderedLists, distinct);

		// Step 4: Map AR indices to their corresponding values from uniqueLists
		return mapIndicesToValues(AR, uniqueLists, columnCount);
	}

	private List<List<String>> orderUniqueValues(List<Set<MyTuple>> uniqueValues) {
		List<List<String>> list = new ArrayList<>();

		for (Set<MyTuple> set : uniqueValues) {
			int size = set.size();
			String[] array = new String[size];
			for (MyTuple tuple : set) {
				array[tuple.index] = tuple.value;
			}
			List<String> l = List.of(array);
			list.add(l);
		}

		return list;
	}

	private int[] calculateDistinctValues(List<int[]> AR, int columnCount) {
		List<Set<Integer>> uniqueValuesSet = new ArrayList<>(columnCount);
		for (int i = 0; i < columnCount; i++) {
			uniqueValuesSet.add(new TreeSet<>());
		}

		for (int[] row : AR) {
			for (int col = 0; col < columnCount; col++) {
				uniqueValuesSet.get(col).add(row[col]);
			}
		}

		int[] distinct = new int[columnCount];
		for (int i = 0; i < columnCount; i++) {
			distinct[i] = uniqueValuesSet.get(i).size();
		}
		return distinct;
	}

	private List<Set<MyTuple>> collectUniqueValues(_CSVTestCase input, int columnCount, int[] distinct) throws Exception {
		List<Set<MyTuple>> uniqueValues = new ArrayList<>();
		for (int i = 0; i < columnCount; i++) {
			uniqueValues.add(new TreeSet<>());
		}

		while (input.hasNext()) {
			boolean allRequirementsMet = true;
			List<String> line = input.next();

			for (int col = 0; col < line.size(); col++) {
				String content = line.get(col);
				if (content == null) {
					content = "NULL";
				}

				Set<MyTuple> columnValues = uniqueValues.get(col);
				if (columnValues.size() < distinct[col]) {
					columnValues.add(new MyTuple(content, columnValues.size()));
				}

				if (columnValues.size() < distinct[col]) {
					allRequirementsMet = false;
				}
			}

			if (allRequirementsMet) {
				break;
			}
		}
		return uniqueValues;
	}

	private List<List<String>> ensureDistinctValues(List<List<String>> uniqueValues, int[] distinct) {
		List<List<String>> uniqueLists = new ArrayList<>();
		for (int i = 0; i < uniqueValues.size(); i++) {
			List<String> columnList = new ArrayList<>(uniqueValues.get(i));
			while (columnList.size() < distinct[i]) {
				columnList.add("Dummy#" + columnList.size());
			}
			uniqueLists.add(columnList);
		}
		return uniqueLists;
	}

	private List<List<String>> mapIndicesToValues(List<int[]> AR, List<List<String>> uniqueLists, int columnCount) {
		List<List<String>> result = new ArrayList<>();
		int[] lastValue = new int[columnCount];
		int[] currentIndex = new int[columnCount];

		for (int[] row : AR) {
			List<String> newRow = new ArrayList<>();
//			System.out.println("NOVA ROW");
			for (int col = 0; col < columnCount; col++) {
				int value = row[col];
//				System.out.print("\tINDEX HODNOTY: " + value + " -> ");
				if (value == 0) {
					newRow.add(uniqueLists.get(col).get(0));
//					System.out.println(uniqueLists.get(col).get(0));
					continue;
				}
				if (value > lastValue[col]) {
					currentIndex[col]++;
					lastValue[col] = value;
				}
				newRow.add(uniqueLists.get(col).get(currentIndex[col]));
//				System.out.println(uniqueLists.get(col).get(currentIndex[col]));

			}
			result.add(newRow);
//			System.out.println("");
		}
		return result;
	}

}
