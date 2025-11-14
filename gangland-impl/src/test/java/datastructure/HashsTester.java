package datastructure;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class HashsTester {

	public static void main(String[] args) {
		int capacity = 15, iterations = 200;
		int size     = 1 << capacity;

		System.out.println(size);

		Set<Integer>          intSet;
		Integer[]             setArray;
		Map<Integer, Integer> intMap;

		long start, end;

		long set = 0, map = 0;

		for (int i = 0; i < iterations; i++) {
			intSet = new HashSet<>(capacity);
			intMap = new HashMap<>(capacity);

			for (int j = 0; j < size; j++) {
				intSet.add(j);
			}

			start    = System.currentTimeMillis();
			setArray = intSet.toArray(new Integer[0]);
			for (int value : setArray) {
				if (value == size / 2) break;
			}
			end = System.currentTimeMillis();

			set += end - start;

			for (int j = 0; j < size; j++) {
				intMap.put(j, j);
			}

			start = System.currentTimeMillis();
			intMap.get(size / 2);
			end = System.currentTimeMillis();

			map += end - start;
		}

		System.out.printf("After %d iterations it took\n", iterations);
		System.out.printf("HashSet: %.2f sec\n", set / (double) iterations);
		System.out.printf("HashMap: %.2f sec\n", map / (double) iterations);
	}

}
