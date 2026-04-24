package me.luckyraven.turf.listener.powerups;

import lombok.RequiredArgsConstructor;
import me.luckyraven.core.bean.listener.ListenerHandler;
import me.luckyraven.turf.data.CuboidRegion;
import me.luckyraven.turf.data.Turf;
import me.luckyraven.turf.events.TurfCaptureFailedEvent;
import me.luckyraven.turf.events.TurfCaptureStartEvent;
import me.luckyraven.turf.events.TurfCapturedEvent;
import me.luckyraven.turf.powerups.GarrisonManager;
import me.luckyraven.turf.turfnpcs.TurfNpcContract;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * Auto-deploys the owning gang's pre-purchased defender garrison the moment an enemy starts capturing the turf, and
 * pivots the per-turf Quartermaster NPC into hostile mode for the duration of the contest. The garrison is consumed (so
 * a turf with stock=3 spawns 3 defenders, then drops back to stock=0 — the owner has to re-buy from the Quartermaster).
 * Defenders are recalled and the Quartermaster is pacified on capture-complete or capture-failed regardless of who won,
 * so a finished contest never leaves stragglers behind.
 *
 * <p>Spawn anchor is the turf's region centre at the highest non-air block — same convention {@code /glw turf
 * tp} uses, so defenders end up standing on the surface rather than buried in stone or floating in midair.
 */
@ListenerHandler
@RequiredArgsConstructor
public final class GarrisonDeployListener implements Listener {

	private final GarrisonManager garrisons;
	private final TurfNpcContract npcs;

	@EventHandler
	public void onCaptureStart(TurfCaptureStartEvent event) {
		Turf turf = event.getTurf();
		// Only owned-turf captures arm the garrison + Quartermaster — unclaimed turfs by definition have no owner
		// to buy stock for and no Quartermaster to defend them, so neither hook applies.
		if (turf.isUnclaimed()) return;

		int challengerGangId = event.getChallengerGang().getId();

		// Engage the Quartermaster regardless of garrison stock — even with no garrison, the Quartermaster itself
		// fights back so an attacker can never just walk in and stand on a defenceless turf.
		npcs.engageQuartermaster(turf.getId(), challengerGangId);

		int stock = garrisons.count(turf.getId());
		if (stock <= 0) return;

		Location spawn = regionCentreSurface(turf.getRegion());
		if (spawn == null) return;

		int consumed = garrisons.consume(turf.getId(), stock);
		if (consumed <= 0) return;

		npcs.deployDefenders(turf.getId(), spawn, challengerGangId, consumed);
	}

	@EventHandler
	public void onCaptured(TurfCapturedEvent event) {
		npcs.recallDefenders(event.getTurf().getId());
		npcs.disengageQuartermaster(event.getTurf().getId());
	}

	@EventHandler
	public void onFailed(TurfCaptureFailedEvent event) {
		npcs.recallDefenders(event.getTurf().getId());
		npcs.disengageQuartermaster(event.getTurf().getId());
	}

	private Location regionCentreSurface(CuboidRegion region) {
		World world = Bukkit.getWorld(region.getWorld());
		if (world == null) return null;
		double centreX = (region.getMinX() + region.getMaxX()) / 2.0 + 0.5;
		double centreZ = (region.getMinZ() + region.getMaxZ()) / 2.0 + 0.5;
		int    y       = world.getHighestBlockYAt((int) centreX, (int) centreZ);
		return new Location(world, centreX, y + 1.0, centreZ);
	}
}
