package org.luckyraven.gangland.copsncrooks.seam;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.copsncrooks.wanted.heat.HeatLedger;
import org.luckyraven.gangland.data.gang.GangMembership;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.manager.TurfManager;

import java.util.function.BiPredicate;

/**
 * TF-49: the killer's gang owns the turf being captured at the kill location, so the kill is a defence and mints no
 * wanted level.
 */
public final class DefendingOwnTurf implements BiPredicate<Player, Location> {

	private final TurfManager     turfs;
	private final GangMembership gangs;

	public DefendingOwnTurf(TurfManager turfs, GangMembership gangs) {
		this.turfs = turfs;
		this.gangs = gangs;
	}

	@Override
	public boolean test(Player killer, Location at) {
		Turf turf = HeatLedger.contestedTurfAt(turfs, at);
		int  gang = gangs.gangIdOf(killer.getUniqueId());
		return turf != null && turf.getOwnerGangId() != null && gang != -1 && turf.getOwnerGangId() == gang;
	}
}
