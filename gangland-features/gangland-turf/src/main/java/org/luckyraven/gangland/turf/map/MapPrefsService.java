package org.luckyraven.gangland.turf.map;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player map preferences, in memory only, cleared on quit (same lifetime as WandSelectionManager). Auto map is off
 * and the turf line is {@link HudMode#DEFAULT} for every player until they change it.
 */
public final class MapPrefsService {

	/** DEFAULT = turf line inside turfs (D12 A); ON = also the nearest turf outside; OFF = no turf line. */
	public enum HudMode { DEFAULT, ON, OFF }

	private final Set<UUID>             autoPlayers = ConcurrentHashMap.newKeySet();
	private final Map<UUID, HudMode>    hudModes    = new ConcurrentHashMap<>();

	public boolean isAuto(UUID player) {
		return autoPlayers.contains(player);
	}

	public void setAuto(UUID player, boolean auto) {
		if (auto) {
			autoPlayers.add(player);
		} else {
			autoPlayers.remove(player);
		}
	}

	public HudMode hudMode(UUID player) {
		return hudModes.getOrDefault(player, HudMode.DEFAULT);
	}

	public void setHudMode(UUID player, HudMode mode) {
		if (mode == null || mode == HudMode.DEFAULT) {
			hudModes.remove(player);
		} else {
			hudModes.put(player, mode);
		}
	}

	/** Snapshot of the players with auto map on. */
	public Set<UUID> autoViewers() {
		return Set.copyOf(autoPlayers);
	}

	public void clear(UUID player) {
		autoPlayers.remove(player);
		hudModes.remove(player);
	}
}
