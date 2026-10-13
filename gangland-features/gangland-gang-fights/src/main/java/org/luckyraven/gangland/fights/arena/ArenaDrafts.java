package org.luckyraven.gangland.fights.arena;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** The draft each staff member is building, in memory only (a restart drops them). */
public final class ArenaDrafts {

	private final Map<UUID, ArenaDraft> drafts = new ConcurrentHashMap<>();

	public ArenaDraft start(UUID staff, String name) {
		ArenaDraft draft = new ArenaDraft(name);
		drafts.put(staff, draft);
		return draft;
	}

	public Optional<ArenaDraft> of(UUID staff) {
		return Optional.ofNullable(drafts.get(staff));
	}

	public void drop(UUID staff) {
		drafts.remove(staff);
	}
}
