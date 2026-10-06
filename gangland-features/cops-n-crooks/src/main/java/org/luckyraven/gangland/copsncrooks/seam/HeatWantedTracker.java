package org.luckyraven.gangland.copsncrooks.seam;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.civilians.npc.entity.EntityMark;
import org.luckyraven.gangland.civilians.npc.entity.EntityMarks;
import org.luckyraven.gangland.copsncrooks.combo.KillCombo;
import org.luckyraven.gangland.copsncrooks.wanted.config.ChaseConfigLoader;
import org.luckyraven.gangland.copsncrooks.wanted.config.KillComboSettings;
import org.luckyraven.gangland.copsncrooks.wanted.heat.HeatLedger;
import org.luckyraven.gangland.core.wanted.Wanted;
import org.luckyraven.gangland.core.wanted.WantedKillTracker;
import org.luckyraven.gangland.crime.CrimeService;
import org.luckyraven.gangland.crime.Crimes;
import org.luckyraven.keystone.npc.NpcSupport;
import org.luckyraven.keystone.npc.entity.NpcMarkManager;

import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Seam 3 delegate: routes every counted kill either to the heat ledger (as a crime) or, with {@code Heat.Enable} off,
 * to the old {@link KillCombo} / one-star-per-kill math. The heat switch is read per call. A civilian NPC managed by
 * gangland-civilians is left to that module; any other CIVILIAN-marked kill commits Kill_Civilian here. A kill made
 * defending your own contested turf mints nothing on either path (docket TF-49).
 */
public final class HeatWantedTracker implements WantedKillTracker {

	private final ChaseConfigLoader            config;
	private final HeatLedger                   ledger;
	private final CrimeService                 crimes;
	private final KillCombo                    killCombo;
	private final NpcMarkManager               marks;
	private final BiPredicate<Player, Location> defendingOwnTurf;
	private final Predicate<Entity>             managedCivilian;

	private volatile Consumer<Player> wantedTrigger;

	public HeatWantedTracker(ChaseConfigLoader config, HeatLedger ledger, CrimeService crimes, KillCombo killCombo,
	                         NpcMarkManager marks, BiPredicate<Player, Location> defendingOwnTurf,
	                         Predicate<Entity> managedCivilian) {
		this.config           = config;
		this.ledger           = ledger;
		this.crimes           = crimes;
		this.killCombo        = killCombo;
		this.marks            = marks;
		this.defendingOwnTurf = defendingOwnTurf;
		this.managedCivilian  = managedCivilian;
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

	/** Kill_Combo.Enable and Reset_After come from copsncrooks/wanted.yml (0.15.1), not from the core. */
	@Override
	public boolean readsComboSettings() {
		return true;
	}

	/**
	 * @param resetAfterSeconds ignored: the core passes 0 because {@link #readsComboSettings()} is true; the combo uses
	 * 		wanted.yml's {@code Wanted.Kill_Combo.Reset_After}
	 */
	@Override
	public void recordKill(Player killer, Wanted wanted, Entity victim, int resetAfterSeconds) {
		boolean realPlayer = victim instanceof Player && !NpcSupport.isNpc(victim);

		if (exemptsKill(killer, victim)) return;

		if (!config.get().heat().enabled()) {
			KillComboSettings combo = config.getKillCombo();
			if (combo.enabled()) {
				killCombo.recordKill(killer, wanted, victim, combo.resetAfterSeconds());
				return;
			}

			Consumer<Player> trigger = wantedTrigger;
			if (trigger != null) trigger.accept(killer);
			return;
		}

		EntityMark mark = EntityMarks.of(marks.getMark(victim));
		if (mark == EntityMark.POLICE) {
			crimes.commit(killer, Crimes.KILL_COP, victim.getLocation());
		} else if (realPlayer) {
			crimes.commit(killer, Crimes.KILL_PLAYER, victim.getLocation());
		} else if (mark == EntityMark.CIVILIAN && !managedCivilian.test(victim)) {
			// a gangland-civilians NPC is published by its own death listener (hostile-in-combat exemption); a vanilla
			// villager, wandering trader or shop NPC has nobody else to report it
			crimes.commit(killer, Crimes.KILL_CIVILIAN, victim.getLocation());
		}
		// anything else is no crime
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
