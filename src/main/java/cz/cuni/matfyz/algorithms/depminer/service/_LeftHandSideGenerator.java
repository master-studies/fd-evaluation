/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package cz.cuni.matfyz.algorithms.depminer.service;

import cz.cuni.matfyz.algorithms.depminer.model._CMAX_SET;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import org.apache.lucene.util.OpenBitSet;

/**
 *
 * @author pavel.koupil
 */
public class _LeftHandSideGenerator {

	public _LeftHandSideGenerator() {
	}

	/**
	 * Computes the LHS
	 *
	 * @param maximalSets The set of the complements of maximal sets (see Phase 2 for further information)
	 * @param nrOfAttributes The number attributes in the whole relation
	 * @return {@code Int2ObjectMap<List<OpenBitSet>>} (key: dependent attribute, value: set of all lefthand sides)
	 */
	public Int2ObjectMap<List<OpenBitSet>> execute(List<_CMAX_SET> maximalSets, int nrOfAttributes) {

		Int2ObjectMap<List<OpenBitSet>> lhs = new Int2ObjectOpenHashMap<>();

		/* 1: for all attributes A in R do */
		for (int attribute = 0; attribute < nrOfAttributes; attribute++) {
//			System.out.println("Attribute: " + attribute);
			/* 2: i:=1 */
			// int i = 1;

			/* 3: Li:={B | B in X, X in cmax(dep(r),A)} */
			Set<OpenBitSet> Li = new HashSet<>();
			_CMAX_SET correctSet = this.generateFirstLevelAndFindCorrectSet(maximalSets, attribute, Li);
//			System.out.println("Attribute: " + attribute + " after generate first level");

			List<List<OpenBitSet>> lhs_a = new LinkedList<>();

			/* 4: while Li != ø do */
//			int counter = 0;
			while (!Li.isEmpty()) {
//				++counter;
//				System.out.println("not empty " + counter + " :: " + Li.size());
				/*
                 * 5: LHSi[A]:={l in Li | l intersect X != ø, for all X in cmax(dep(r),A)}
				 */
				List<OpenBitSet> lhs_i = findLHS(Li, correctSet);

				/* 6: Li:=Li/LHSi[A] */
				Li.removeAll(lhs_i);

				/*
                 * 7: Li+1:={l' | |l'|=i+1 and for all l subset l' | |l|=i, l in Li}
				 */
 /*
				 * The generation of the next level is, as mentioned in the paper, done with the Apriori gen-function from the
				 * following paper: "Fast algorithms for mining association rules in large databases." - Rakesh Agrawal,
				 * Ramakrishnan Srikant
				 */
				Li = this.generateNextLevel(Li);

				/* 8: i:=i+1 */
				// i++;
				lhs_a.add(lhs_i);
			}

			/* 9: lhs(dep(r),A):= union LHSi[A] */
			if (!lhs.containsKey(attribute)) {
				lhs.put(attribute, new LinkedList<OpenBitSet>());
//				System.out.println("LHS_SIZE: " + lhs.size());
			}
			for (List<OpenBitSet> lhs_ia : lhs_a) {
				lhs.get(attribute).addAll(lhs_ia);
			}
		}

//		System.out.println("RETURNING LHS_SIZE: " + lhs.size());
		return lhs;
	}

	private List<OpenBitSet> findLHS(Set<OpenBitSet> Li, _CMAX_SET correctSet) {

		List<OpenBitSet> lhs_i = new LinkedList<>();
		for (OpenBitSet l : Li) {
			boolean isLHS = true;
			for (OpenBitSet x : correctSet.getCombinations()) {
				if (!l.intersects(x)) {
					isLHS = false;
					break;
				}
			}
			if (isLHS) {
				lhs_i.add(l);
			}
		}
		return lhs_i;
	}

	private _CMAX_SET generateFirstLevelAndFindCorrectSet(List<_CMAX_SET> maximalSets, int attribute, Set<OpenBitSet> Li) {

		_CMAX_SET correctSet = null;
		for (_CMAX_SET set : maximalSets) {
			if (!(set.getAttribute() == attribute)) {
				continue;
			}
			correctSet = set;
			for (OpenBitSet list : correctSet.getCombinations()) {

				OpenBitSet combination;
				int lastIndex = list.nextSetBit(0);
				while (lastIndex != -1) {
					combination = new OpenBitSet();
					combination.set(lastIndex);
					Li.add(combination);
					lastIndex = list.nextSetBit(lastIndex + 1);
				}
			}
			break;
		}
		return correctSet;
	}

	private Set<OpenBitSet> generateNextLevel(Set<OpenBitSet> li) {

		// Join-Step
		List<OpenBitSet> Ck = new LinkedList<>();
		for (OpenBitSet p : li) {
			for (OpenBitSet q : li) {
				if (!this.checkJoinCondition(p, q)) {
					continue;
				}
				OpenBitSet candidate = new OpenBitSet();
				candidate.or(p);
				candidate.or(q);
				Ck.add(candidate);
			}
		}

		// Pruning-Step
		Set<OpenBitSet> result = new HashSet<>();
		for (OpenBitSet c : Ck) {
			boolean prune = false;
			int lastIndex = c.nextSetBit(0);
			while (lastIndex != -1) {
				c.flip(lastIndex);
				if (!li.contains(c)) {
					prune = true;
					break;
				}
				c.flip(lastIndex);
				lastIndex = c.nextSetBit(lastIndex + 1);
			}

			if (!prune) {
				result.add(c);
			}
		}

		return result;

	}

	private boolean checkJoinCondition(OpenBitSet p, OpenBitSet q) {

		if (p.prevSetBit(p.length()) >= q.prevSetBit(p.length())) {
			return false;
		}
		for (int i = 0; i < p.prevSetBit(p.length()); i++) {
			if (p.getBit(i) != q.getBit(i)) {
				return false;
			}
		}
		return true;
	}

}
