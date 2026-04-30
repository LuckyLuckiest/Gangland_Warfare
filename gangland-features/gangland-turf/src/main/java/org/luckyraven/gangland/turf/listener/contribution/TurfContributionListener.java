package org.luckyraven.gangland.turf.listener.contribution;

import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.luckyraven.gangland.core.bean.listener.ListenerHandler;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.contract.GangLookupContract;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.turf.contribution.TurfContributionSettings;
import org.luckyraven.gangland.turf.contribution.TurfContributionTickTask;
import org.luckyraven.gangland.turf.data.CuboidRegion;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.events.TurfCaptureFailedEvent;
import org.luckyraven.gangland.turf.events.TurfCapturedEvent;

/**
 * One-shot contribution bonuses on capture lifecycle events. Capture-complete credits the challenger gang's members who
 * are currently inside the turf region; capture-failed (DEFENDED reason) credits the defender gang's members who held
 * it. Presence-tick rewards are handled separately by {@link TurfContributionTickTask}.
 *
 * <p>"Inside the turf" is re-evaluated at event time so a player who happens to be mid-region at the tick of
 * completion gets the bonus; anyone who wandered off earlier only has their accumulated presence-tick points.
 */
@ListenerHandler
@RequiredArgsConstructor
public final class TurfContributionListener implements Listener {

	private final GangLookupContract       gangs;
	private final TurfContributionSettings settings;

	@EventHandler
	public void onCaptured(TurfCapturedEvent event) {
		if (settings.captureCompleteBonus() <= 0.0) return;
		Gang gang = event.getNewOwner();
		if (gang == null) return;
		awardPresent(gang, event.getTurf(), settings.captureCompleteBonus());
	}

	@EventHandler
	public void onFailed(TurfCaptureFailedEvent event) {
		if (event.getReason() != TurfCaptureFailedEvent.Reason.DEFENDED) return;
		if (settings.defenseSuccessBonus() <= 0.0) return;
		Turf    turf    = event.getTurf();
		Integer ownerId = turf.getOwnerGangId();
		if (ownerId == null) return;
		Gang owner = gangs.findById(ownerId);
		if (owner == null) return;
		awardPresent(owner, turf, settings.defenseSuccessBonus());
	}

	private void awardPresent(Gang gang, Turf turf, double points) {
		CuboidRegion region = turf.getRegion();
		for (Player online : Bukkit.getOnlinePlayers()) {
			if (!online.getWorld().getName().equals(region.getWorld())) continue;
			int x = online.getLocation().getBlockX();
			int y = online.getLocation().getBlockY();
			int z = online.getLocation().getBlockZ();
			if (x < region.getMinX() || x > region.getMaxX()) continue;
			if (z < region.getMinZ() || z > region.getMaxZ()) continue;
			// region may not enforce Y bounds for all turfs; use world bounds as a loose filter.
			if (y < 0 || y > 319) continue;

			for (Member member : gang.getMembers()) {
				if (member.getUuid().equals(online.getUniqueId())) {
					member.increaseContribution(points);
					break;
				}
			}
		}
	}
}
