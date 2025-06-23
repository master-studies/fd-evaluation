/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package cz.cuni.matfyz.algorithms.depminer.service;

import cz.cuni.matfyz.algorithms.depminer.model._CSVTestCase;
import cz.cuni.matfyz.algorithms.depminer.model._StrippedPartition;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 *
 * @author pavel.koupil
 */
public class _StrippedPartitionGenerator {

	public static String nullValue = "null#" + Math.random();

	private List<_StrippedPartition> returnValue;

	private Int2ObjectMap<Map<String, LongList>> translationMaps = new Int2ObjectOpenHashMap<>();

	public _StrippedPartitionGenerator() {
	}

	public List<_StrippedPartition> execute(_CSVTestCase input) throws Exception {

		int lineNumber = 0;

		// nacitani dat
		while (input.hasNext()) {

			List<String> line = input.next();

			for (int column = 0; column < line.size(); ++column) {

				String content = line.get(column);
				if (null == content) {
					content = _StrippedPartitionGenerator.nullValue;
				}

				Map<String, LongList> translationMap;
				if ((translationMap = this.translationMaps.get(column)) == null) {
					translationMap = new HashMap<>();
					this.translationMaps.put(column, translationMap);
				}
				LongList element;
				if ((element = translationMap.get(content)) == null) {
					element = new LongArrayList();
					translationMap.put(content, element);
				}
				element.add(lineNumber);

			}

			lineNumber++;
		}

		// Načtení seznamů a vytvoření oddělených oddílů
		this.returnValue = new LinkedList<>();
		for (int i : this.translationMaps.keySet()) {
			executeStrippedPartitionGenerationTask(i);
		}

		// úklid
		this.translationMaps.clear();

		return this.returnValue;

	}

	private void executeStrippedPartitionGenerationTask(int i) {

		_StrippedPartition sp = new _StrippedPartition(i);
		this.returnValue.add(sp);

		Map<String, LongList> toItterate = this.translationMaps.get(i);

		for (LongList it : toItterate.values()) {

			if (it.size() > 1) {
				sp.addElement(it);
			}

		}

		// úklid po práci
		this.translationMaps.get(i).clear();
	}

}
