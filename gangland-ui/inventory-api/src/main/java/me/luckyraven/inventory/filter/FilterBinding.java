package me.luckyraven.inventory.filter;

import java.util.List;

/**
 * Declarative link between an inventory view (by id) and the filter spec it supports. One binding per list view;
 * features construct bindings at startup and register them in {@link FilterRegistry}.
 */
public record FilterBinding(String id,
                            String targetInventory,
                            List<FilterField> fields,
                            SortDescriptor defaultSort,
                            List<SortDescriptor> sortCycle) {

	public SearchFilter empty() {
		return SearchFilter.empty(defaultSort);
	}

	public boolean supports(FilterField field) {
		for (FilterField candidate : fields) {
			if (candidate.id().equalsIgnoreCase(field.id())) return true;
		}
		return false;
	}

	public FilterField findField(String id) {
		if (id == null) return null;
		for (FilterField candidate : fields) {
			if (candidate.id().equalsIgnoreCase(id)) return candidate;
		}
		return null;
	}

	public SortDescriptor nextSort(SortDescriptor current) {
		if (current == null) return defaultSort;
		return current.cycle(sortCycle);
	}

}
