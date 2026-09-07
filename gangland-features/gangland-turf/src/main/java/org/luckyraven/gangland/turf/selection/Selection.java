package org.luckyraven.gangland.turf.selection;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import org.jetbrains.annotations.Nullable;

/**
 * Per-admin wand selection. In-memory only, cleared on quit. Both positions must belong to the same world.
 */
@Getter
@Setter
public final class Selection {

	private @Nullable String   world;
	private @Nullable Location pos1;
	private @Nullable Location pos2;
	/**
	 * The turf currently "selected" by the admin — set automatically on successful create and on
	 * {@code /glw turf select}, consumed by commands like {@code /glw turf tp} when called without an explicit id.
	 * Cleared whenever pos1/pos2 changes, because setting corners implies a fresh, not-yet-created turf.
	 */
	private @Nullable Integer  activeTurfId;

	public boolean isComplete() {
		return pos1 != null && pos2 != null;
	}

	public void set(Location location, boolean first) {
		if (location == null || location.getWorld() == null) {
			return;
		}
		// Reject cross-world selections by resetting on world mismatch.
		if (this.world != null && !this.world.equals(location.getWorld().getName())) {
			this.pos1 = null;
			this.pos2 = null;
		}
		this.world = location.getWorld().getName();
		if (first) {
			this.pos1 = location;
		} else {
			this.pos2 = location;
		}
		// New corners = new work-in-progress turf; invalidate any previously-selected turf id.
		this.activeTurfId = null;
	}
}
