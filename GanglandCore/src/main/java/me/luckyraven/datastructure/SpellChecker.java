package me.luckyraven.datastructure;

import lombok.Getter;

import java.util.*;

@Getter
public class SpellChecker {

	private final Set<String>                dictionary;
	private final Map<Integer, List<String>> suggestions;
	private final String                     word;

	public SpellChecker(String word, Set<String> dictionary) {
		this.suggestions = new TreeMap<>();
		this.word        = word;
		this.dictionary  = dictionary;
	}

	/**
	 * Generates all the possible suggestions according to their difference length
	 *
	 * @return the number of suggestions generated
	 */
	public long generateSuggestions() {
		long words = 0;

		for (String word : dictionary) {
			// check Damerau-Levenshtein distance
			int length = damerauLevenshteinDistance(this.word, word);

			// add the word
			suggestions.merge(length, new ArrayList<>(List.of(word)), (old, current) -> {
				old.add(word);
				return old;
			});

			++words;
		}

		return words;
	}

	/**
	 * Uses Damerau Levenshtein Distance algorithm but unoptimized
	 *
	 * @param word1 The first word
	 * @param word2 The second word
	 *
	 * @return The difference length of both words
	 */
	public int levenshteinDistance(String word1, String word2) {
		int[] v0 = new int[word2.length() + 1];
		int[] v1 = new int[word2.length() + 1];

		for (int i = 0; i < word2.length(); i++) {
			v0[i] = i;
		}

		for (int i = 0; i < word1.length(); i++) {
			v1[0] = i + 1;

			for (int j = 0; j < word2.length(); j++) {
				int deletion     = v0[j + 1] + 1;
				int insertion    = v1[j] + 1;
				int substitution = word1.charAt(i) == word2.charAt(j) ? v0[j] : v0[j] + 1;

				v1[j + 1] = Math.min(Math.min(deletion, insertion), substitution);
			}

			System.arraycopy(v1, 0, v0, 0, v1.length);
		}

		return v0[word2.length()];
	}

	/**
	 * Uses Damerau Levenshtein Distance algorithm but optimized, additionally it checks for the transposition
	 *
	 * @param word1 The first word
	 * @param word2 The second word
	 *
	 * @return The difference length of both words
	 */
	public int damerauLevenshteinDistance(String word1, String word2) {
		int m = word1.length();
		int n = word2.length();

		if (n > m)
			// swap between the words, leaving the m as the largest and n as the smallest
			return damerauLevenshteinDistance(word2, word1);

		// Use the longer word's length as the row size
		int[] currentRow = new int[m + 1];

		// Initialize the first row
		for (int i = 0; i <= m; i++)
			 currentRow[i] = i;

		int leftValue, upperLeftValue, temp;

		for (int j = 1; j <= n; j++) {
			leftValue      = j;
			upperLeftValue = currentRow[0];
			currentRow[0]  = j;

			for (int i = 1; i <= m; i++) {
				int cost = (word1.charAt(i - 1) == word2.charAt(j - 1)) ? 0 : 1;

				int deletion     = currentRow[i - 1] + 1;
				int insertion    = leftValue + 1;
				int substitution = upperLeftValue + cost;

				temp          = currentRow[i];
				currentRow[i] = Math.min(Math.min(deletion, insertion), substitution);

				// Check for transposition
				if (i > 1 && j > 1 && word1.charAt(i - 1) == word2.charAt(j - 2) &&
					word1.charAt(i - 2) == word2.charAt(j - 1)) currentRow[i] = Math.min(currentRow[i],
																						 currentRow[i - 2] + cost);

				leftValue      = currentRow[i];
				upperLeftValue = temp;
			}
		}

		return currentRow[m];
	}
}
