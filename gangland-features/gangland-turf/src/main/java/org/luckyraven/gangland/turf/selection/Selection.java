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

	/** Fallback for {@code Turf.Selection_Other_World}; shared by pos1, pos2 and the wand listener. */
	public static final String OTHER_WORLD_FALLBACK = "&cThat corner is in another world than your selection. Run /glw turf wand to start a new one.";

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

	/**
	 * Sets one corner. A corner in a different world than the OTHER corner is rejected (TF-29): the existing corners are
	 * kept unchanged.
	 *
	 * @return {@code false} when the corner was rejected (or has no world), {@code true} when it was set
	 */
	public boolean set(Location location, boolean first) {
		if (location == null || location.getWorld() == null) {
			return false;
		}
		String name  = location.getWorld().getName();
		Location other = first ? this.pos2 : this.pos1;
		// Compare with the stored world name: Location.getWorld() throws once the world has been unloaded.
		if (other != null && this.world != null && !this.world.equals(name)) {
			return false;
		}
		this.world = name;
		if (first) {
			this.pos1 = location;
		} else {
			this.pos2 = location;
		}
		// New corners = new work-in-progress turf; invalidate any previously-selected turf id.
		this.activeTurfId = null;
		return true;
	}
}
