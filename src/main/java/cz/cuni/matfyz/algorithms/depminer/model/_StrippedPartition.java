/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package cz.cuni.matfyz.algorithms.depminer.model;

import it.unimi.dsi.fastutil.longs.LongList;
import java.util.LinkedList;
import java.util.List;
import org.apache.lucene.util.OpenBitSet;

/**
 *
 * @author pavel.koupil
 */
public class _StrippedPartition {

	private final int attribute;
	private final List<LongList> value = new LinkedList<>();
	private boolean finalized = false;

	public _StrippedPartition(int attribute) {

		this.attribute = attribute;
	}

	public void addElement(LongList element) {

		if (finalized) {
			return;
		}
		this.value.add(element);
	}

	public void markFinalized() {

		this.finalized = true;
	}

	public int getAttributeID() {

		return this.attribute;
	}

	public List<LongList> getValues() {

		return this.value;

	}

	public List<OpenBitSet> getValuesAsBitSet() {

		List<OpenBitSet> result = new LinkedList<>();
		for (LongList list : this.value) {
			OpenBitSet set = new OpenBitSet();
			for (long i : list) {
				set.set(i);
			}
			result.add(set);
		}
		return result;
	}

	@Override
	public String toString() {

		String s = "sp(";
		for (LongList il : this.value) {
			s += il.toString() + "-";
		}
		return s + ")";
	}

	public _StrippedPartition copy() {
		System.out.println("PROBIHA COPY V STRIPPED PARTITION!");
		_StrippedPartition copy = new _StrippedPartition(this.attribute);
		for (LongList l : this.value) {
			copy.value.add(l);
		}
		copy.finalized = this.finalized;
		return copy;
	}

}
