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
public class _CMAX_SET {

	protected int attribute;
	protected List<OpenBitSet> columnCombinations;
	private boolean finalized;

	public _CMAX_SET(int attribute) {

		this.attribute = attribute;
		this.columnCombinations = new LinkedList<>();
		this.finalized = false;
	}

	public void addCombination(OpenBitSet combination) {

		this.columnCombinations.add(combination);
	}

	public List<OpenBitSet> getCombinations() {

		return this.columnCombinations;
	}

	public int getAttribute() {

		return this.attribute;
	}

	@Override
	public String toString() {

		String s = "cmax(" + this.attribute + ": ";
		for (OpenBitSet set : this.columnCombinations) {
			s += _BitSetUtil.convertToLongList(set);
		}
		return s + ")";
	}

	public void finalize_RENAME_THIS() {

		this.finalized = true;
	}

	@Override
	public int hashCode() {

		final int prime = 31;
		int result = 1;
		result = prime * result + attribute;
		result = prime * result + ((columnCombinations == null) ? 0 : columnCombinations.hashCode());
		result = prime * result + (finalized ? 1231 : 1237);
		return result;
	}

	@Override
	public boolean equals(Object obj) {

		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		_CMAX_SET other = (_CMAX_SET) obj;
		if (attribute != other.attribute) {
			return false;
		}
		if (columnCombinations == null) {
			if (other.columnCombinations != null) {
				return false;
			}
		} else if (!columnCombinations.equals(other.columnCombinations)) {
			return false;
		}
		if (finalized != other.finalized) {
			return false;
		}
		return true;
	}

}
