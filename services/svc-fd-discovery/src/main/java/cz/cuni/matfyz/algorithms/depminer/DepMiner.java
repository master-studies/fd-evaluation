/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package cz.cuni.matfyz.algorithms.depminer;

import cz.cuni.matfyz.algorithms.depminer.model._AgreeSet;
import cz.cuni.matfyz.algorithms.depminer.model._CMAX_SET;
import cz.cuni.matfyz.algorithms.depminer.model._CSVTestCase;
import cz.cuni.matfyz.algorithms.depminer.model._FunctionalDependency;
import cz.cuni.matfyz.algorithms.depminer.model._FunctionalDependencyGroup;
import cz.cuni.matfyz.algorithms.depminer.model._MAX_SET;
import cz.cuni.matfyz.algorithms.depminer.model._StrippedPartition;
import cz.cuni.matfyz.algorithms.depminer.service._AgreeSetGenerator;
import cz.cuni.matfyz.algorithms.depminer.service._CMAX_SET_Generator;
import cz.cuni.matfyz.algorithms.depminer.service._FunctionalDependencyGenerator;
import cz.cuni.matfyz.algorithms.depminer.service._LeftHandSideGenerator;
import cz.cuni.matfyz.algorithms.depminer.service._StrippedPartitionGenerator;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.apache.lucene.util.OpenBitSet;
import cz.cuni.matfyz.algorithms.depminer.model._FunctionalDependencyOutput;

/**
 *
 * @author pavel.koupil
 */
public class DepMiner {

//	private final int numberOfThreads;
    private final _CSVTestCase input;

    private _CMAX_SET_Generator setGenerator;

    private static String FILENAME;

    Int2ObjectMap<List<OpenBitSet>> lhss;

    _FunctionalDependencyGenerator xxx;

    private List<int[]> lastAbstractAR = null;
    private List<String> lastColumnNames = null;

    public List<int[]> getLastAbstractAR() { return lastAbstractAR; }
    public List<String> getLastColumnNames() { return lastColumnNames; }

    public DepMiner(/*int numberOfThreads,*/_CSVTestCase input, String filename) {
//		this.numberOfThreads = numberOfThreads;
        this.input = input;
        FILENAME = filename;
    }

    public List<_FunctionalDependencyOutput> execute() throws Exception {
        _StrippedPartitionGenerator spg = new _StrippedPartitionGenerator();
        List<_StrippedPartition> strippedPartitions = spg.execute(input);

        System.out.println("----- STRIPPED PARTITIONS -----");
        System.out.println("size: " + strippedPartitions.size());
//		for (int index = 0; index < strippedPartitions.size(); ++index) {
//			System.out.println(strippedPartitions.get(index));
//		}
        System.out.println("");

        int length = input.numberOfColumns();

        List<_AgreeSet> agreeSets = new _AgreeSetGenerator().execute(strippedPartitions);
        System.out.println("----- AGREE SET -----");
        System.out.println("size: " + agreeSets.size());
//		for (int index = 0; index < agreeSets.size(); ++index) {
//			System.out.println(agreeSets.get(index));
//		}
        System.out.println("");

        setGenerator = new _CMAX_SET_Generator(agreeSets, length);
        List<_MAX_SET> maxSets = setGenerator.generateMaxSet();
        System.out.println("----- MAXIMAL SETS -----");
        System.out.println("size: " + maxSets.size());
        for (int index = 0; index < maxSets.size(); ++index) {
            System.out.println(maxSets.get(index));
        }
        System.out.println("");
        List<_CMAX_SET> cmaxSets = setGenerator.generateCMAX_SETs();
        System.out.println("----- COMPLEMENTS OF MAXIMAL SETS -----");
        System.out.println("size: " + cmaxSets.size());
        for (int index = 0; index < cmaxSets.size(); ++index) {
            System.out.println(cmaxSets.get(index));
        }
        System.out.println("");

        lhss = new _LeftHandSideGenerator().execute(cmaxSets, length);
        xxx = new _FunctionalDependencyGenerator(input, input.relationName(), input.columnNames(), lhss);
        List<_FunctionalDependencyGroup> result = xxx.execute();
        System.out.println("----- FUNCTIONAL DEPENDENCIES -----");
        System.out.println("size: " + result.size());
        Map<Integer, List<_FunctionalDependencyGroup>> fds = new TreeMap<>();
        for (int index = 0; index < length; ++index) {
            List<_FunctionalDependencyGroup> list = new ArrayList<>();
            fds.put(index, list);
        }
        for (int index = 0; index < result.size(); ++index) {
            _FunctionalDependencyGroup fd = result.get(index);
            var list = fds.get(fd.getAttributeID());
            list.add(fd);
        }

        List<_FunctionalDependencyOutput> FdsResult = new ArrayList<>();

        fds.forEach((key, list) -> {
            System.out.println("Attribute: " + key + " Size: " + list.size());
            for (int index = 0; index < list.size(); ++index) {
                // I only want to reaturn column names, however you can pass input.relationName() as table identifier if needed
                _FunctionalDependency fd = list.get(index).buildDependency("", input.columnNames());
                String names = fd.toString();
                String indices = list.get(index).toString();
                FdsResult.add(new _FunctionalDependencyOutput(names, indices));
                System.out.println(fd);
            }
        });
        System.out.println("");

        // BUILDING ARMSTRONG RELATION!
        ArmstrongRelationBuilder builder = new ArmstrongRelationBuilder();
        List<int[]> AR = builder.execute(maxSets);

        this.lastAbstractAR = AR;
        this.lastColumnNames = input.columnNames();

        System.out.println("----- ABSTRACT ARMSTRONG RELATION -----");
        System.out.println("Columns: " + String.join(", ", input.columnNames()));
        for (int[] value : AR) {
            StringBuilder b = new StringBuilder();
            for (int index = 0; index < value.length; ++index) {
                b.append(value[index]);
                if (index != value.length - 1) {
                    b.append(", ");
                }
            }
            System.out.println(b);
        }
        System.out.println("SIZE: " + AR.size());

        var AR_RL = builder.realworldAR(AR, input, FILENAME);

        System.out.println("----- REAL-WORLD ARMSTRONG RELATION -----");
        System.out.println(String.join(", ", input.columnNames()));
        for (var line : AR_RL) {
            System.out.println(String.join(", ", line));
        }
        System.out.println("SIZE: " + AR_RL.size());

        String outputFilePath = "armstrong.csv";
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFilePath))) {
            for (var line : AR_RL) {
                for (int index = 0; index < line.size(); ++index) {
                    writer.write(line.get(index));
                    if (index < line.size() - 1) {
                        writer.write(", ");
                    }
                }
                writer.newLine(); // Start a new line after each row
            }
            System.out.println("Data saved to " + outputFilePath);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return FdsResult;
    }

    public void demo() throws Exception {
        int length = input.numberOfColumns();
        List<_MAX_SET> maxSets = setGenerator.generateMaxSet();
        int COLUMN_INDEX = 6;
//				setGenerator.targetFD(COLUMN_INDEX, 0, 1, 2, 3, 5);		// vyhodi 0,1,3

//				setGenerator.targetFD(COLUMN_INDEX, 0, 1, 2, 4);		// vyhodi 0,1,4
//				setGenerator.targetFD(COLUMN_INDEX, 0, 1, 4, 5);		// vyhodi 0,1,4
//		setGenerator.targetFD(COLUMN_INDEX, 0, 2, 3, 4);		// vyhodi 0,2,3,4
//				setGenerator.targetFD(COLUMN_INDEX, 0, 2, 4, 5);		// vyhodi 0,2,4,5
//				setGenerator.targetFD(COLUMN_INDEX, 0, 2, 3, 4, 5);		// vyhodi 0,2,4,5 a 0,2,3,4
        setGenerator.targetFD(COLUMN_INDEX, 0, 1, 2, 3, 5);		// vyhodi 0,1,3 a 0,1,4
        setGenerator.targetFD(COLUMN_INDEX, 0, 1, 3, 4, 5);		// vyhodi 0,1,3 a 0,1,4
        setGenerator.targetFD(COLUMN_INDEX, 0, 1, 2, 4);		// vyhodi 0,1,3 a 0,1,4
        System.out.println("----- MAXIMAL SETS -----");
        System.out.println("size: " + maxSets.size());
        for (int index = 0; index < maxSets.size(); ++index) {
            System.out.println(maxSets.get(index));
        }
        List<_CMAX_SET> cmaxSets = setGenerator.generateCMAX_SETs();
//			System.out.println("----- COMPLEMENTS OF MAXIMAL SETS -----");
//			System.out.println("size: " + cmaxSets.size());
//			for (int index = 0; index < cmaxSets.size(); ++index) {
//				System.out.println(cmaxSets.get(index));
//			}
//			System.out.println("");
        lhss = new _LeftHandSideGenerator().execute(cmaxSets, length);
        xxx = new _FunctionalDependencyGenerator(input, input.relationName(), input.columnNames(), lhss);
        List<_FunctionalDependencyGroup> result = xxx.execute();
        System.out.println("----- FUNCTIONAL DEPENDENCIES -----");
        System.out.println("size: " + result.size());
        Map<Integer, List<_FunctionalDependencyGroup>> fds = new TreeMap<>();
        for (int index = 0; index < length; ++index) {
            List<_FunctionalDependencyGroup> list = new ArrayList<>();
            fds.put(index, list);
        }
        for (int index = 0; index < result.size(); ++index) {
            _FunctionalDependencyGroup fd = result.get(index);
            var list = fds.get(fd.getAttributeID());
            list.add(fd);
        }


        fds.forEach((key, list) -> {
            System.out.println("Attribute: " + key + " Size: " + list.size());
            for (int index = 0; index < list.size(); ++index) {
                System.out.println(list.get(index));
            }
//				System.out.println("");
        });
        System.out.println("");

        // BUILDING ARMSTRONG RELATION!
        ArmstrongRelationBuilder builder = new ArmstrongRelationBuilder();
        List<int[]> AR = builder.execute(maxSets);

//		int index = 1;
        for (int[] value : AR) {
//			if (index == 1) {
//				String firstRow = value.replace("1", String.valueOf(0));
//				System.out.println(firstRow);
//			}
//			value = value.replace("1", "" + index++);
            StringBuilder b = new StringBuilder();
            for (int index = 0; index < value.length; ++index) {
                b.append(value[index]);
                if (index != value.length - 1) {
                    b.append(", ");
                }
            }
            System.out.println(b);
        }
        System.out.println("SIZE: " + AR.size());

        var AR_RL = builder.realworldAR(AR, input, FILENAME);

        for (var line : AR_RL) {
            for (int index = 0; index < line.size(); ++index) {
                System.out.print(line.get(index));
                if (index < line.size() - 1) {
                    System.out.print(", ");
                } else {
                    System.out.println("");
                }
            }
        }

    }

    public void demo2() throws Exception {
        int length = input.numberOfColumns();
        List<_MAX_SET> maxSets = setGenerator.generateMaxSet();
//		setGenerator.targetFD(1, 0);		// vyhodi 0 -> 1
//		setGenerator.targetFD(1, 2);		// vyhodi 2 -> 1
//		setGenerator.targetFD(1, 4);		// vyhodi 2 -> 4
//		setGenerator.targetFD(2, 0);		// vyhodi 0 -> 2
//		setGenerator.targetFD(3, 0);		// vyhodi 0 -> 3
//		setGenerator.targetFD(4, 0);		// vyhodi 0 -> 4

//		setGenerator.targetFD(0, 3, 4);		// vyhodi 2 -> 1
//		setGenerator.targetFD(0, 2, 3);		// vyhodi 2 -> 1
//		setGenerator.targetFD(0, 2, 4);		// vyhodi 2 -> 1
//		setGenerator.targetFD(2, 3, 4);		// vyhodi 2 -> 1
//		setGenerator.targetFD(3, 2, 4);		// vyhodi 2 -> 1
//		setGenerator.targetFD(4, 2, 3);		// vyhodi 2 -> 1
//		setGenerator.targetFD(0, 1, 2, 3);
//		setGenerator.targetFD(0, 1, 2, 4);
//		setGenerator.targetFD(0, 2, 3, 4);
//		setGenerator.targetFD(0, 1, 3, 4);
//		setGenerator.targetFD(4, 1, 2, 3);
//		setGenerator.targetFD(3, 1, 2, 4);
//		setGenerator.targetFD(1, 2, 3, 4);
//		setGenerator.targetFD(2, 1, 3, 4);
        setGenerator.targetFD(4, 2, 3);
//		setGenerator.targetFD(0, 1, 2, 4);
//		setGenerator.targetFD(0, 2, 3, 4);
//		setGenerator.targetFD(0, 1, 3, 4);
        setGenerator.targetFD(1, 2, 3, 4);
        setGenerator.targetFD(2, 1, 3, 4);
        setGenerator.targetFD(3, 1, 2, 4);
        setGenerator.targetFD(0, 1, 2, 3, 4);

//		setGenerator.targetFD(COLUMN_INDEX, 0, 1, 2, 4);		// vyhodi 0,1,3 a 0,1,4
        System.out.println("----- MAXIMAL SETS -----");
        System.out.println("size: " + maxSets.size());
        for (int index = 0; index < maxSets.size(); ++index) {
            System.out.println(maxSets.get(index));
        }
        List<_CMAX_SET> cmaxSets = setGenerator.generateCMAX_SETs();
//			System.out.println("----- COMPLEMENTS OF MAXIMAL SETS -----");
//			System.out.println("size: " + cmaxSets.size());
//			for (int index = 0; index < cmaxSets.size(); ++index) {
//				System.out.println(cmaxSets.get(index));
//			}
//			System.out.println("");
        lhss = new _LeftHandSideGenerator().execute(cmaxSets, length);
        xxx = new _FunctionalDependencyGenerator(input, input.relationName(), input.columnNames(), lhss);
        List<_FunctionalDependencyGroup> result = xxx.execute();
        System.out.println("----- FUNCTIONAL DEPENDENCIES -----");
        System.out.println("size: " + result.size());
        Map<Integer, List<_FunctionalDependencyGroup>> fds = new TreeMap<>();
        for (int index = 0; index < length; ++index) {
            List<_FunctionalDependencyGroup> list = new ArrayList<>();
            fds.put(index, list);
        }
        for (int index = 0; index < result.size(); ++index) {
            _FunctionalDependencyGroup fd = result.get(index);
            var list = fds.get(fd.getAttributeID());
            list.add(fd);
        }

        fds.forEach((key, list) -> {
            System.out.println("Attribute: " + key + " Size: " + list.size());
            for (int index = 0; index < list.size(); ++index) {
                System.out.println(list.get(index));
            }
//				System.out.println("");
        });
        System.out.println("");

        // BUILDING ARMSTRONG RELATION!
        ArmstrongRelationBuilder builder = new ArmstrongRelationBuilder();
        List<int[]> AR = builder.execute(maxSets);

//		int index = 1;
        for (int[] value : AR) {
//			if (index == 1) {
//				String firstRow = value.replace("1", String.valueOf(0));
//				System.out.println(firstRow);
//			}
//			value = value.replace("1", "" + index++);
            StringBuilder b = new StringBuilder();
            for (int index = 0; index < value.length; ++index) {
                b.append(value[index]);
                if (index != value.length - 1) {
                    b.append(", ");
                }
            }
            System.out.println(b);
        }
        System.out.println("SIZE: " + AR.size());

        var AR_RL = builder.realworldAR(AR, input, FILENAME);

        for (var line : AR_RL) {
            for (int index = 0; index < line.size(); ++index) {
                System.out.print(line.get(index));
                if (index < line.size() - 1) {
                    System.out.print(", ");
                } else {
                    System.out.println("");
                }
            }
        }

    }

}
