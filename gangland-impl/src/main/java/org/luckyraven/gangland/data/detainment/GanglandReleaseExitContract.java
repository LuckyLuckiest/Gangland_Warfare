package org.luckyraven.gangland.data.detainment;

import org.bukkit.Location;
import org.luckyraven.gangland.copsncrooks.detainment.release.ReleaseExitContract;
import org.luckyraven.gangland.copsncrooks.jail.JailExitRegistry;
import org.luckyraven.gangland.data.teleportation.Waypoint;
import org.luckyraven.gangland.data.teleportation.WaypointManager;
import org.luckyraven.gangland.file.configuration.Settings;

/**
 * Resolves the teleport target for a jail release using a cascading fallback:
 * <ol>
 *   <li>Per-jail exit (set via {@code /glw jail setexit <id>}).</li>
 *   <li>Global exit (set via {@code /glw jail setexit} with no arg).</li>
 *   <li>Configured fallback waypoint ({@code Detainment.Fallback_Exit_Waypoint} in settings.yml).</li>
 *   <li>Any other waypoint in the waypoint manager.</li>
 *   <li>{@code null} — caller skips the teleport and the player stays where they are.</li>
 * </ol>
 */
public final class GanglandReleaseExitContract implements ReleaseExitContract {

	private final JailExitRegistry jailExitRegistry;
	private final WaypointManager  waypointManager;

	public GanglandReleaseExitContract(JailExitRegistry jailExitRegistry, WaypointManager waypointManager) {
		this.jailExitRegistry = jailExitRegistry;
		this.waypointManager  = waypointManager;
	}

	@Override
	public Location resolveExit(int jailId) {
		Location perJail = jailExitRegistry.getExit(jailId);
		if (perJail != null) return perJail;

		Location global = jailExitRegistry.getGlobalExit();
		if (global != null) return global;

		String configuredName = Settings.getDetainmentFallbackExitWaypoint();
		if (configuredName != null && !configuredName.isBlank()) {
			Waypoint named = waypointManager.get(configuredName);
			if (named != null) {
				Location namedLocation = named.getLocation();
				if (namedLocation != null) return namedLocation;
			}
		}

		for (Waypoint waypoint : waypointManager.getWaypoints().values()) {
			if (waypoint == null) continue;
			Location anyLocation = waypoint.getLocation();
			if (anyLocation != null) return anyLocation;
		}

		// Last resort: release on the spot — no teleport.
		return null;
	}
}
