package org.luckyraven.gangland.inventory.filter;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class FilterRegistry {

	private final Map<String, FilterBinding> bindings = new ConcurrentHashMap<>();

	public void register(FilterBinding binding) {
		bindings.put(binding.id(), binding);
	}

	public FilterBinding get(String id) {
		return bindings.get(id);
	}

	public boolean contains(String id) {
		return bindings.containsKey(id);
	}

	public Collection<FilterBinding> all() {
		return Collections.unmodifiableCollection(bindings.values());
	}

}
