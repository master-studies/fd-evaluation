/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package cz.cuni.matfyz.algorithms.depminer.model;

import cz.cuni.matfyz.algorithms.depminer.util._BitSetUtil;
import org.apache.lucene.util.OpenBitSet;

/**
 *
 * @author pavel.koupil
 */
public class _AgreeSet {

	protected OpenBitSet attributes = new OpenBitSet();

	public void add(int attribute) {

		this.attributes.set(attribute);
	}

	public OpenBitSet getAttributes() {

		return this.attributes.clone();

	}

	@Override
	public String toString() {

		return "ag(" + _BitSetUtil.convertToIntList(this.attributes).toString()
				+ ")";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result
				+ ((attributes == null) ? 0 : attributes.hashCode());
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
		_AgreeSet other = (_AgreeSet) obj;
		if (attributes == null) {
			if (other.attributes != null) {
				return false;
			}
		} else if (!attributes.equals(other.attributes)) {
			return false;
		}
		return true;
	}

}
