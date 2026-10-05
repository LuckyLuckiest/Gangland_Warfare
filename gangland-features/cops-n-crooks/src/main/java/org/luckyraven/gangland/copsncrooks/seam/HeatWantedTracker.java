package org.luckyraven.gangland.copsncrooks.seam;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.civilians.npc.entity.EntityMark;
import org.luckyraven.gangland.civilians.npc.entity.EntityMarks;
import org.luckyraven.gangland.copsncrooks.combo.KillCombo;
import org.luckyraven.gangland.copsncrooks.wanted.config.ChaseConfigLoader;
import org.luckyraven.gangland.copsncrooks.wanted.heat.HeatLedger;
import org.luckyraven.gangland.core.wanted.Wanted;
import org.luckyraven.gangland.core.wanted.WantedKillTracker;
import org.luckyraven.gangland.crime.CrimeService;
import org.luckyraven.gangland.crime.Crimes;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.keystone.npc.NpcSupport;
import org.luckyraven.keystone.npc.entity.NpcMarkManager;

import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

/**
 * Seam 3 delegate: routes every counted kill either to the heat ledger (as a crime) or, with {@code Heat.Enable} off,
 * to the old {@link KillCombo} / one-star-per-kill math. The heat switch is read per call. A kill made defending your
 * own contested turf mints nothing on either path (docket TF-49).
 */
public final class HeatWantedTracker implements WantedKillTracker {

	private final ChaseConfigLoader            config;
	private final HeatLedger                   ledger;
	private final CrimeService                 crimes;
	private final KillCombo                    killCombo;
	private final NpcMarkManager               marks;
	private final BiPredicate<Player, Location> defendingOwnTurf;

	private volatile Consumer<Player> wantedTrigger;

	public HeatWantedTracker(ChaseConfigLoader config, HeatLedger ledger, CrimeService crimes, KillCombo killCombo,
	                         NpcMarkManager marks, BiPredicate<Player, Location> defendingOwnTurf) {
		this.config           = config;
		this.ledger           = ledger;
		this.crimes           = crimes;
		this.killCombo        = killCombo;
		this.marks            = marks;
		this.defendingOwnTurf = defendingOwnTurf;
	}

	@Override
	public boolean countsForWanted(Entity victim) {
		return EntityMarks.countsForWanted(victim, marks);
	}

	@Override
	public boolean exemptsKill(Player killer, Entity victim) {
		return victim instanceof Player && !NpcSupport.isNpc(victim)
		       && defendingOwnTurf.test(killer, victim.getLocation());
	}

	@Override
	public boolean appliesComboSwitch() {
		return true;
	}

	@Override
	public void recordKill(Player killer, Wanted wanted, Entity victim, int resetAfterSeconds) {
		boolean realPlayer = victim instanceof Player && !NpcSupport.isNpc(victim);

		if (exemptsKill(killer, victim)) return;

		if (!config.get().heat().enabled()) {
			if (Settings.isWantedKillComboEnabled()) {
				killCombo.recordKill(killer, wanted, victim, resetAfterSeconds);
				return;
			}

			Consumer<Player> trigger = wantedTrigger;
			if (trigger != null) trigger.accept(killer);
			return;
		}

		if (EntityMarks.of(marks.getMark(victim)) == EntityMark.POLICE) {
			crimes.commit(killer, Crimes.KILL_COP, victim.getLocation());
		} else if (realPlayer) {
			crimes.commit(killer, Crimes.KILL_PLAYER, victim.getLocation());
		}
		// a civilian kill is published by gangland-civilians; anything else is no crime
	}

	@Override
	public void resetCombo(UUID victimId) {
		killCombo.resetCombo(victimId);
	}

	@Override
	public void onWantedTrigger(Consumer<Player> handler) {
		this.wantedTrigger = handler;
		killCombo.setOnWantedLevelTrigger(event -> handler.accept(event.getPlayer()));
		ledger.setStarTrigger(handler);
	}

	@Override
	public void onComboReset(Consumer<Player> handler) {
		killCombo.setOnComboReset(event -> handler.accept(event.getPlayer()));
	}

	@Override
	public void onVictimDeath(Consumer<UUID> handler) {
		killCombo.setOnPlayerDeath(handler::accept);
	}
}
