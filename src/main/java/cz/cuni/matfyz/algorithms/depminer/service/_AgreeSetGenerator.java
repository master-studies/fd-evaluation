/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package cz.cuni.matfyz.algorithms.depminer.service;

import cz.cuni.matfyz.algorithms.depminer.model._AgreeSet;
import cz.cuni.matfyz.algorithms.depminer.model._StrippedPartition;
import cz.cuni.matfyz.algorithms.depminer.model._TupleEquivalenceClassRelation;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArraySet;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 *
 * @author pavel.koupil
 */
public class _AgreeSetGenerator {

    private static class ListComparator2 implements Comparator<LongList> {

        @Override
        public int compare(LongList l1, LongList l2) {

            if (l1.size() - l2.size() != 0) {
                return l2.size() - l1.size();
            }
            for (int i = 0; i < l1.size(); i++) {
                if (l1.getLong(i) == l2.getLong(i)) {
                    continue;
                }
                return (int) (l2.getLong(i) - l1.getLong(i));
            }
            return 0;
        }

    }

    private static final Boolean DEBUG = Boolean.FALSE;

    public _AgreeSetGenerator() {
    }

    public List<_AgreeSet> execute(List<_StrippedPartition> partitions) throws Exception {

        if (_AgreeSetGenerator.DEBUG) {
            long sum = 0;
            for (_StrippedPartition p : partitions) {
                System.out.println("-----");
                System.out.println("Atribut: " + p.getAttributeID());
                System.out.println("Pocet oddilu: " + p.getValues().size());
                sum += p.getValues().size();
            }
            System.out.println("-----");
            System.out.println("Celkem: " + sum);
            System.out.println("-----");
        }

        Set<LongList> maxSets;
//        if (this.chooseAlternative1) {
//            maxSets = this.computeMaximumSetsAlternative(partitions);
//        } else if (this.chooseAlternative2) {
        maxSets = this.computeMaximumSetsAlternative2(partitions);
//        } else {
//            maxSets = this.computeMaximumSets(partitions);
//        }

        Long2ObjectMap<_TupleEquivalenceClassRelation> relationships = calculateRelationships(partitions);
        Set<_AgreeSet> agreeSets = computeAgreeSets(relationships, maxSets, partitions);

        List<_AgreeSet> result = new LinkedList<>(agreeSets);

        return result;
    }

    public Set<LongList> computeMaximumSetsAlternative2(List<_StrippedPartition> partitions) throws Exception {

        if (_AgreeSetGenerator.DEBUG) {
            System.out.println("\tvýpočet maximálních oddílů");
        }
        long start = System.currentTimeMillis();

        Set<LongList> sortedPartitions = this.sortPartitions(partitions, new ListComparator2());

        if (_AgreeSetGenerator.DEBUG) {
            System.out.println("\tTime to sort: " + (System.currentTimeMillis() - start));
        }

        Iterator<LongList> it = sortedPartitions.iterator();
        long remainingPartitions = sortedPartitions.size();
        if (_AgreeSetGenerator.DEBUG) {
            System.out.println("\tNumber of Partitions: " + remainingPartitions);
        }

        Long2ObjectMap<LongSet> index = new Long2ObjectOpenHashMap<>();
        Set<LongList> max = new HashSet<>();

        long actuelIndex = 0;
        LongList actuelList;

        while (it.hasNext()) {
            actuelList = it.next();
            this.handlePartition(actuelList, actuelIndex, index, max);
            actuelIndex++;
        }

        long end = System.currentTimeMillis();
        if (_AgreeSetGenerator.DEBUG) {
            System.out.println("\tTime needed: " + (end - start));
        }

        index.clear();
        sortedPartitions.clear();

        return max;

    }

    private Set<LongList> sortPartitions(List<_StrippedPartition> partitions, Comparator<LongList> comparator) {

        Set<LongList> sortedPartitions = new TreeSet<>(comparator);
        for (_StrippedPartition p : partitions) {
            sortedPartitions.addAll(p.getValues());
        }
        return sortedPartitions;
    }

    private void handlePartition(LongList actuelList, long position, Long2ObjectMap<LongSet> index, Set<LongList> max) {

        if (!this.isSubset(actuelList, index)) {
            max.add(actuelList);
            for (long e : actuelList) {
                if (!index.containsKey(e)) {
                    index.put(e, new LongArraySet());
                }
                index.get(e).add(position);
            }
        }
    }

    public Long2ObjectMap<_TupleEquivalenceClassRelation> calculateRelationships(List<_StrippedPartition> partitions) {

        if (_AgreeSetGenerator.DEBUG) {
            System.out.println("\tstartet calculation of relationships");
        }
        Long2ObjectMap<_TupleEquivalenceClassRelation> relationships = new Long2ObjectOpenHashMap<>();
        for (_StrippedPartition p : partitions) {
            this.calculateRelationship(p, relationships);
        }

        return relationships;
    }

    public Set<_AgreeSet> computeAgreeSets(Long2ObjectMap<_TupleEquivalenceClassRelation> relationships, Set<LongList> maxSets, List<_StrippedPartition> partitions) throws Exception {

        if (_AgreeSetGenerator.DEBUG) {
            System.out.println("\tstartet calculation of agree sets");
            int bitsPerSet = (((int) (partitions.size() - 1) / 64) + 1) * 64;
            long setsNeeded = 0;
            for (LongList l : maxSets) {
                setsNeeded += l.size() * (l.size() - 1) / 2;
            }
            System.out.println("Approx. RAM needed to store all agree sets: " + bitsPerSet * setsNeeded / 8 / 1024 / 1024 + " MB");
        }

        partitions.clear();

        if (_AgreeSetGenerator.DEBUG) {
            System.out.println(maxSets.size());
        }
        int a = 0;

        Set<_AgreeSet> agreeSets = new HashSet<>();

        for (LongList maxEquiClass : maxSets) {
            if (_AgreeSetGenerator.DEBUG) {
                System.out.println(a++);
            }
            for (int i = 0; i < maxEquiClass.size() - 1; i++) {
                for (int j = i + 1; j < maxEquiClass.size(); j++) {
                    relationships.get(maxEquiClass.getLong(i)).intersectWithAndAddToAgreeSet(relationships.get(maxEquiClass.getLong(j)), agreeSets);
                }
            }
        }

        return agreeSets;

    }

    private void calculateRelationship(_StrippedPartition partitions, Long2ObjectMap<_TupleEquivalenceClassRelation> relationships) {

        int partitionNr = 0;
        for (LongList partition : partitions.getValues()) {
            if (_AgreeSetGenerator.DEBUG) {
                System.out.println(".");
            }
            for (long index : partition) {
                if (!relationships.containsKey(index)) {
                    relationships.put(index, new _TupleEquivalenceClassRelation());
                }
                relationships.get(index).addNewRelationship(partitions.getAttributeID(), partitionNr);
            }
            partitionNr++;
        }

    }

    private boolean isSubset(LongList actuelList, Map<Long, LongSet> index) {

        boolean first = true;
        LongSet positions = new LongArraySet();
        for (long e : actuelList) {
            if (!index.containsKey(e)) {
                return false;
            }
            if (first) {
                positions.addAll(index.get(e));
                first = false;
            } else {

                this.intersect(positions, index.get(e));
                // FIXME: Throws UnsupportedOperationExeption within fastUtil
                // positions.retainAll(index.get(e));
            }
            if (positions.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private void intersect(LongSet positions, LongSet indexSet) {

        LongSet toRemove = new LongArraySet();
        for (long l : positions) {
            if (!indexSet.contains(l)) {
                toRemove.add(l);
            }
        }
        positions.removeAll(toRemove);
    }

}
