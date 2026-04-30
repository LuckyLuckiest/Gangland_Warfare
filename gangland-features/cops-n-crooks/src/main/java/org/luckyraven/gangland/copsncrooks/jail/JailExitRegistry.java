package org.luckyraven.gangland.copsncrooks.jail;

import org.bukkit.Location;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory lookup of per-jail exit locations. Populated at startup by the jail-exit repository and mutated by the
 * {@code /glw jail setexit} command. Lookups fall back to {@code null} when a jail has no configured exit — the release
 * pipeline then falls back to the configured waypoint.
 */
public final class JailExitRegistry {

	private final    Map<Integer, Location> exitsByJailId = new ConcurrentHashMap<>();
	private volatile Location               globalExit;

	public Location getExit(int jailId) {
		Location location = exitsByJailId.get(jailId);
		return location == null ? null : location.clone();
	}

	public Location getGlobalExit() {
		return globalExit == null ? null : globalExit.clone();
	}

	public void setGlobalExit(Location location) {
		this.globalExit = location == null ? null : location.clone();
	}

	public void setExit(int jailId, Location location) {
		if (location == null) {
			exitsByJailId.remove(jailId);
			return;
		}
		exitsByJailId.put(jailId, location.clone());
	}

	public boolean hasExit(int jailId) {
		return exitsByJailId.containsKey(jailId);
	}

	public void clear(int jailId) {
		exitsByJailId.remove(jailId);
	}

	/**
	 * Returns a snapshot of every configured exit as a {@link Collection} of {@link JailExit} records. Used as the
	 * repository data-supplier so auto-save writes the in-memory registry back to the {@code jail_exit} table.
	 */
	public Collection<JailExit> snapshot() {
		List<JailExit> exits = new ArrayList<>(exitsByJailId.size());
		for (Map.Entry<Integer, Location> entry : exitsByJailId.entrySet()) {
			exits.add(JailExit.forJail(entry.getKey(), entry.getValue()));
		}
		return exits;
	}
}
