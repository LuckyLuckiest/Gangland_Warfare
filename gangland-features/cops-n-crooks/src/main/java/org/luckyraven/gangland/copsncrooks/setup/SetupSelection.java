package org.luckyraven.gangland.copsncrooks.setup;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import org.jetbrains.annotations.Nullable;

/** One admin's wand selection (copy of the turf wand shape). In memory only; both corners share a world. */
@Getter
public final class SetupSelection {

	private @Nullable String   world;
	private @Nullable Location pos1;
	private @Nullable Location pos2;
	@Setter
	private SetupMode          mode = SetupMode.STATION;

	/** Point modes need pos1 only, cuboid modes both corners. */
	public boolean isComplete() {
		return pos1 != null && (mode.isPoint() || pos2 != null);
	}

	public void set(Location location, boolean first) {
		if (location == null || location.getWorld() == null) return;

		String name = location.getWorld().getName();
		if (world != null && !world.equals(name)) {
			pos1 = null;
			pos2 = null;
		}
		world = name;
		if (first) pos1 = location;
		else pos2 = location;
	}
}
