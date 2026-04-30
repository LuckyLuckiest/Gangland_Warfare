package org.luckyraven.gangland.inventory.filter;

import java.util.HashMap;
import java.util.Map;

/**
 * Immutable filter value composed of a map of field → value predicates plus a {@link SortDescriptor}. Obtain the empty
 * filter for a binding via {@link FilterBinding#empty()}.
 */
public final class SearchFilter {

	private final Map<FilterField, FilterValue> values;
	private final SortDescriptor                sort;

	private SearchFilter(Map<FilterField, FilterValue> values, SortDescriptor sort) {
		this.values = values;
		this.sort   = sort;
	}

	public static SearchFilter empty(SortDescriptor sort) {
		return new SearchFilter(Map.of(), sort);
	}

	public SearchFilter with(FilterField field, FilterValue value) {
		if (value == null) return without(field);
		Map<FilterField, FilterValue> next = new HashMap<>(values);
		next.put(field, value);
		return new SearchFilter(Map.copyOf(next), sort);
	}

	public SearchFilter without(FilterField field) {
		if (!values.containsKey(field)) return this;
		Map<FilterField, FilterValue> next = new HashMap<>(values);
		next.remove(field);
		return new SearchFilter(Map.copyOf(next), sort);
	}

	public SearchFilter withSort(SortDescriptor next) {
		return new SearchFilter(values, next);
	}

	public SearchFilter clearValues() {
		return new SearchFilter(Map.of(), sort);
	}

	public boolean has(FilterField field) {
		return values.containsKey(field);
	}

	public FilterValue get(FilterField field) {
		return values.get(field);
	}

	public Map<FilterField, FilterValue> values() {
		return values;
	}

	public SortDescriptor sort() {
		return sort;
	}

	public boolean isEmpty() {
		return values.isEmpty();
	}

}
