/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package cz.cuni.matfyz.algorithms.depminer.model;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.List;

/**
 *
 * @author pavel.koupil
 */
public class _FunctionalDependencyGroup {

	private int attribute = Integer.MIN_VALUE;
	private IntSet values = new IntArraySet();

	public _FunctionalDependencyGroup(int attributeID, IntList values) {

		this.attribute = attributeID;
		this.values.addAll(values);
	}

	public int getAttributeID() {

		return this.attribute;
	}

	public IntList getValues() {

		IntList returnValue = new IntArrayList();
		returnValue.addAll(this.values);
		return returnValue;

	}

	@Override
	public String toString() {

		return this.values + " --> " + this.attribute;
	}

	@Override
	public int hashCode() {

		final int prime = 31;
		int result = 1;
		result = prime * result + attribute;
		result = prime * result + ((values == null) ? 0 : values.hashCode());
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
		_FunctionalDependencyGroup other = (_FunctionalDependencyGroup) obj;
		if (attribute != other.attribute) {
			return false;
		}
		if (values == null) {
			if (other.values != null) {
				return false;
			}
		} else if (!values.equals(other.values)) {
			return false;
		}
		return true;
	}

	public _FunctionalDependency buildDependency(String tableIdentifier, List<String> columnNames) {

		_FunctionalDependency._ColumnIdentifier[] combination = new _FunctionalDependency._ColumnIdentifier[this.values.size()];
		int j = 0;
		for (int i : this.values) {
			combination[j] = new _FunctionalDependency._ColumnIdentifier(tableIdentifier, columnNames.get(i));
			j++;
		}
		_FunctionalDependency._ColumnCombination cc = new _FunctionalDependency._ColumnCombination(combination);
		_FunctionalDependency._ColumnIdentifier ci = new _FunctionalDependency._ColumnIdentifier(tableIdentifier, columnNames.get(this.attribute));
		_FunctionalDependency fd = new _FunctionalDependency(cc, ci);
		return fd;
	}

}
