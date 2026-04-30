package org.luckyraven.gangland.copsncrooks.npc.trader.trait;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public final class TraderTraitRegistry {

	private final AtomicReference<Map<String, TraderTraitDefinition>> byId =
			new AtomicReference<>(Collections.emptyMap());

	public TraderTraitDefinition get(String id) {
		if (id == null) return null;
		return byId.get().get(id);
	}

	public boolean exists(String id) {
		return id != null && byId.get().containsKey(id);
	}

	public Collection<TraderTraitDefinition> all() {
		return byId.get().values();
	}

	public Set<String> ids() {
		return byId.get().keySet();
	}

	public void replaceAll(Map<String, TraderTraitDefinition> replacement) {
		byId.set(Collections.unmodifiableMap(new LinkedHashMap<>(replacement)));
	}

}
