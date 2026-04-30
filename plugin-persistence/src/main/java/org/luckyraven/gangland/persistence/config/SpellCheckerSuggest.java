package org.luckyraven.gangland.persistence.config;

import org.luckyraven.gangland.core.datastructure.SpellChecker;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Thin wrapper over {@link SpellChecker} that returns the single closest entry from a dictionary, or {@code null} when
 * the best edit distance exceeds the caller's tolerance. Shared by the YAML unknown-key sweep in {@link ConfigReport}
 * and the DSL unknown-attribute sweep in {@code ItemDslAdapter} so both sides emit consistent "did you mean?" text.
 */
public final class SpellCheckerSuggest {

	private SpellCheckerSuggest() {
	}

	/**
	 * Return the dictionary entry with the smallest Damerau-Levenshtein distance to {@code unknown}, provided that
	 * distance is {@code <= maxDistance}. Returns {@code null} for an empty dictionary or when no entry is close
	 * enough.
	 *
	 * @param unknown the misspelled word.
	 * @param dictionary the candidate pool — typically the set of keys the loader/converter actually read.
	 * @param maxDistance the largest edit distance to accept as a suggestion.
	 *
	 * @return the best candidate or {@code null}.
	 */
	public static String best(String unknown, Set<String> dictionary, int maxDistance) {
		if (dictionary.isEmpty()) return null;

		SpellChecker checker = new SpellChecker(unknown, dictionary);
		checker.generateSuggestions();

		int    bestDist = Integer.MAX_VALUE;
		String best     = null;

		for (Map.Entry<Integer, List<String>> entry : checker.getSuggestions().entrySet()) {
			if (entry.getKey() < bestDist && !entry.getValue().isEmpty()) {
				bestDist = entry.getKey();
				best     = entry.getValue().getFirst();
			}
		}

		return (best != null && bestDist <= maxDistance) ? best : null;
	}

}
