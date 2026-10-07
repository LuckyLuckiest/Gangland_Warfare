package org.luckyraven.gangland.copsncrooks.npc.police.dispatch;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopConfigProvider;
import org.luckyraven.gangland.copsncrooks.npc.police.config.DispatchSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.config.HandoffSettings;
import org.luckyraven.gangland.copsncrooks.station.Station;
import org.luckyraven.gangland.copsncrooks.station.StationRegistry;

import java.util.function.Supplier;

import static java.util.Objects.requireNonNullElse;

/**
 * Picks the station a dispatched unit leaves from and its ETA ({@code cops.yml}'s {@code Cops.Dispatch}). The config
 * is read on every call, so {@code /glw reload} takes effect.
 *
 * @since 0.16.0
 */
public final class Dispatcher {

	private final StationRegistry             stations;
	private final Supplier<CopConfigProvider> config;

	public Dispatcher(StationRegistry stations, Supplier<CopConfigProvider> config) {
		this.stations = stations;
		this.config   = config;
	}

	/** The station a unit leaves from ({@code null} = the ring around the target) and its ETA in ms. */
	public record Plan(@Nullable Station station, long etaMs) { }

	/**
	 * Disabled -> Plan(null, 0). Else the nearest station in the target's world; with an active bias, the nearest
	 * station whose anchor is ahead ({@link SpawnBias#ahead}) wins over the nearest one only when its ETA is at most the
	 * nearest's ETA + Handoff {@code Bias_Seconds} (a far station ahead never turns a 5 s response into 40 s); no
	 * station -> Plan(null, 0).
	 */
	public Plan plan(Player target, long now, @Nullable SpawnBias bias) {
		CopConfigProvider provider = config.get();
		DispatchSettings dispatch = requireNonNullElse(provider == null ? null : provider.getDispatchSettings(),
		                                               DispatchSettings.DEFAULT);
		Location at = target.getLocation();
		World    world = at.getWorld();
		if (!dispatch.enabled() || world == null) return new Plan(null, 0L);

		boolean biased  = bias != null && bias.activeAt(now);
		Station nearest = null;
		Station ahead   = null;
		double  nearestD = Double.MAX_VALUE;
		double  aheadD   = Double.MAX_VALUE;
		for (Station station : stations.all()) {
			if (!station.getWorld().equals(world.getName())) continue;
			Location anchor = new Location(world, station.getX(), station.getY(), station.getZ());
			double   d      = horizontal(at, anchor);
			if (d < nearestD) {
				nearestD = d;
				nearest  = station;
			}
			if (biased && d < aheadD && bias.ahead(at, anchor)) {
				aheadD = d;
				ahead  = station;
			}
		}
		if (nearest == null) return new Plan(null, 0L);

		long nearestEta = dispatch.etaMs(nearestD);
		if (ahead != null && ahead != nearest) {
			HandoffSettings handoff = requireNonNullElse(provider == null ? null : provider.getHandoffSettings(),
			                                             HandoffSettings.DEFAULT);
			long            aheadEta = dispatch.etaMs(aheadD);
			if (aheadEta <= nearestEta + handoff.biasSeconds() * 1000L) return new Plan(ahead, aheadEta);
		}
		return new Plan(nearest, nearestEta);
	}

	private static double horizontal(Location a, Location b) {
		double dx = a.getX() - b.getX();
		double dz = a.getZ() - b.getZ();
		return Math.sqrt(dx * dx + dz * dz);
	}
}
