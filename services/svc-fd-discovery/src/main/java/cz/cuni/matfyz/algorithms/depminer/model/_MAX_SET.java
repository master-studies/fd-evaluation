/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package cz.cuni.matfyz.algorithms.depminer.model;

import cz.cuni.matfyz.algorithms.depminer.util._BitSetUtil;
import java.util.LinkedList;
import java.util.List;
import org.apache.lucene.util.OpenBitSet;

/**
 *
 * @author pavel.koupil
 */
public class _MAX_SET extends _CMAX_SET {

	private boolean finalized;

	public _MAX_SET(int attribute) {
		super(attribute);
		this.finalized = false;
	}

	@Override
	public String toString() {

		String s = "max(" + this.attribute + ": ";
		for (OpenBitSet set : this.columnCombinations) {
			s += _BitSetUtil.convertToLongList(set);
		}
		return s + ")";
	}

	@Override
	public void finalize_RENAME_THIS() {

		if (!this.finalized) {
			this.checkContentForOnlySuperSets();
		}
		this.finalized = true;

	}

	private void checkContentForOnlySuperSets() {

		List<OpenBitSet> superSets = new LinkedList<OpenBitSet>();
		List<OpenBitSet> toDelete = new LinkedList<OpenBitSet>();
		boolean toAdd = true;

		for (OpenBitSet set : this.columnCombinations) {
			for (OpenBitSet superSet : superSets) {
				if (this.checkIfSetIsSuperSetOf(set, superSet)) {
					toDelete.add(superSet);
				}
				if (toAdd) {
					toAdd = !this.checkIfSetIsSuperSetOf(superSet, set);
				}
			}
			superSets.removeAll(toDelete);
			if (toAdd) {
				superSets.add(set);
			} else {
				toAdd = true;
			}
			toDelete.clear();
		}

		this.columnCombinations = superSets;
	}

	private boolean checkIfSetIsSuperSetOf(OpenBitSet set, OpenBitSet set2) {
		OpenBitSet setCopy = set.clone();
		setCopy.intersect(set2);
		return setCopy.equals(set2);
	}

}
