package org.luckyraven.gangland.copsncrooks.setup;

import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/** What the next {@code /glw cop setup save} stores: a single point (station, pickup) or a tagged cuboid. */
@Getter
public enum SetupMode {
	STATION(true, null),
	DISTRICT(false, "district"),
	HIDEOUT(false, "hideout"),
	PICKUP(true, null),
	RESTRICTED(false, "restricted"),
	BREAKER(false, "breaker");

	/** True when pos1 alone is enough (the anchor block). */
	private final boolean point;
	/** The region tag a cuboid mode writes; {@code null} for the point modes. */
	private final @Nullable String tag;

	SetupMode(boolean point, @Nullable String tag) {
		this.point = point;
		this.tag   = tag;
	}

	/** The mode called {@code name} (case-insensitive), or {@code null}. */
	public static @Nullable SetupMode byName(String name) {
		for (SetupMode mode : values())
			if (mode.name().equals(name.toUpperCase(Locale.ROOT))) return mode;
		return null;
	}

	/** Lowercase name, as typed in the command. */
	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}
}
