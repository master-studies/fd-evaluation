/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package cz.cuni.matfyz.algorithms.depminer.service;

import cz.cuni.matfyz.algorithms.depminer.model._AgreeSet;
import cz.cuni.matfyz.algorithms.depminer.model._CMAX_SET;
import cz.cuni.matfyz.algorithms.depminer.model._MAX_SET;
import java.util.LinkedList;
import java.util.List;
import org.apache.lucene.util.OpenBitSet;

/**
 *
 * @author pavel.koupil
 */
public class _CMAX_SET_Generator {

    private List<_MAX_SET> maxSet;
    private List<_CMAX_SET> cmaxSet;

    private List<_AgreeSet> agreeSets;
    private int numberOfAttributes;

    public _CMAX_SET_Generator(List<_AgreeSet> agreeSets, int numberOfAttributes) {
        this.agreeSets = agreeSets;
        this.numberOfAttributes = numberOfAttributes;

    }

    public void targetFD(int columnIndex, int... bits) {
        var _maxSet = maxSet.get(columnIndex);
        _AgreeSet s = new _AgreeSet();
        for (int index = 0; index < bits.length; ++index) {
            s.add(bits[index]);
        }
        _maxSet.addCombination(s.getAttributes());
//		_maxSet.finalize_RENAME_THIS();
    }

//	public List<_CMAX_SET> execute() throws Exception {
//
//		this.generateMaxSet();
//
//		System.out.println("----- MAXIMAL SETS -----");
//		System.out.println("size: " + maxSet.size());
//		for (int index = 0; index < maxSet.size(); ++index) {
//			System.out.println(maxSet.get(index));
    ////			if (index == 6) {
////				System.out.println("6!");
//////				targetFD(maxSet.get(index), 0, 1, 2, 3, 7, 9, 10); // 0,1,3
////
//////				// tohle vyhodi: 0,1,5 0,1,4 0,1,3 0,1,6
//////				targetFD(maxSet.get(index), 1, 2, 3, 4, 5, 6, 7, 9, 10);	// 0
//////				targetFD(maxSet.get(index), 0, 1, 3, 4, 5, 6, 7, 9, 10);	// 2
//////				targetFD(maxSet.get(index), 0, 1, 2, 3, 4, 6, 7, 9, 10);	// 5
//////				targetFD(maxSet.get(index), 0, 1, 2, 4, 5, 6, 9, 10);	// 7, 3
////				// tohle vyhodi: 0,1,5 0,1,4 0,1,3 0,1,6
//////				targetFD(maxSet.get(index), 0, 1, 2, 3, 5);		// vyhodi 0,1,3
//////				targetFD(maxSet.get(index), 0, 1, 2, 4);		// vyhodi 0,1,4
//////				targetFD(maxSet.get(index), 0, 1, 4, 5);		// vyhodi 0,1,4
//////				targetFD(maxSet.get(index), 0, 2, 3, 4);		// vyhodi 0,2,3,4
//////				targetFD(maxSet.get(index), 0, 2, 4, 5);		// vyhodi 0,2,4,5
//////				targetFD(maxSet.get(index), 0, 2, 3, 4, 5);		// vyhodi 0,2,4,5 a 0,2,3,4
//////				targetFD(maxSet.get(index), 0, 1, 2, 3, 5);		// vyhodi 0,1,3 a 0,1,4
//////				targetFD(maxSet.get(index), 0, 1, 3, 4, 5);		// vyhodi 0,1,3 a 0,1,4
//////				targetFD(maxSet.get(index), 0, 1, 2, 4);		// vyhodi 0,1,3 a 0,1,4
//////
////				targetFD(maxSet.get(index), 0, 1, 2, 3, 4);		// vyhodi 0,1,3 a 0,1,4
////				maxSet.get(index).finalize_RENAME_THIS();
////			}
////
////			System.out.println(maxSet.get(index));
//		}
//
//		System.out.println("");
//
//		this.generateCMAX_SETs();
//
//		return this.cmaxSet;
//
//	}

	public List<_MAX_SET> generateMaxSet() throws Exception {

        this.maxSet = new LinkedList<>();
        for (int i = 0; i < this.numberOfAttributes; ++i) {
//			System.out.println("A_index: " + i);
            executeMax_Set_Task(i);
        }

        return maxSet;
    }

    private void executeMax_Set_Task(int currentJob) {

        _MAX_SET result = new _MAX_SET(currentJob);
        for (_AgreeSet a : this.agreeSets) {
            OpenBitSet content = a.getAttributes();
            if (content.get(currentJob)) {
                continue;
            }
            result.addCombination(content);
        }
        result.finalize_RENAME_THIS();
        this.maxSet.add(result);
    }

    public List<_CMAX_SET> generateCMAX_SETs() throws Exception {

        this.cmaxSet = new LinkedList<>();
        for (int i = 0; i < this.numberOfAttributes; ++i) {
            executeCMAX_SET_Task(i);
        }

        return cmaxSet;
    }

    private void executeCMAX_SET_Task(int currentJob) {

        _MAX_SET maxSet = null;
        for (_MAX_SET m : this.maxSet) {
            if (m.getAttribute() == currentJob) {
                maxSet = m;
                break;
            }
        }

        _CMAX_SET result = new _CMAX_SET(currentJob);

        for (OpenBitSet il : maxSet.getCombinations()) {
            OpenBitSet inverse = new OpenBitSet();
            inverse.set(0, this.numberOfAttributes);
            inverse.xor(il);
            result.addCombination(inverse);
        }

        result.finalize_RENAME_THIS();
        this.cmaxSet.add(result);

    }

}
