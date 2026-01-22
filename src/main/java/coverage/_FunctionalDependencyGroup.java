package coverage;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntSet;

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
}
