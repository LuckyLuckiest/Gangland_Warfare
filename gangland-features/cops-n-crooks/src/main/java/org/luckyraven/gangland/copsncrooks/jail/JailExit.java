package org.luckyraven.gangland.copsncrooks.jail;

import lombok.Getter;
import org.bukkit.Location;
import org.jetbrains.annotations.Nullable;

/**
 * Persisted teleport target used when a player is released from jail. A single {@code jail_exit} table holds both
 * universal ({@link Scope#GLOBAL}) and per-jail ({@link Scope#SPECIFIC}) entries; the {@link #scope} attribute
 * discriminates them and {@link #jailId} is only populated for {@code SPECIFIC} rows.
 */
@Getter
public class JailExit {

	private final Scope    scope;
	@Nullable
	private final Integer  jailId;
	private       Location location;

	private JailExit(Scope scope, @Nullable Integer jailId, Location location) {
		this.scope    = scope;
		this.jailId   = jailId;
		this.location = location == null ? null : location.clone();
	}

	public static JailExit global(Location location) {
		return new JailExit(Scope.GLOBAL, null, location);
	}

	public static JailExit forJail(int jailId, Location location) {
		return new JailExit(Scope.SPECIFIC, jailId, location);
	}

	public boolean isGlobal() {
		return scope == Scope.GLOBAL;
	}

	public Location getLocation() {
		return location == null ? null : location.clone();
	}

	public void setLocation(Location location) {
		this.location = location == null ? null : location.clone();
	}

	/**
	 * Whether this row is a universal (fallback) exit or tied to a specific jail id.
	 */
	public enum Scope {
		GLOBAL,
		SPECIFIC
	}
}
