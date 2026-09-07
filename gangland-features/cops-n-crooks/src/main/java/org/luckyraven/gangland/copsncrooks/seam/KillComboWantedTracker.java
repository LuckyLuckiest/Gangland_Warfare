package org.luckyraven.gangland.copsncrooks.seam;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.copsncrooks.combo.KillCombo;
import org.luckyraven.gangland.copsncrooks.npc.entity.EntityMarkManager;
import org.luckyraven.gangland.gang.wanted.Wanted;
import org.luckyraven.gangland.gang.wanted.WantedKillTracker;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Seam 3 delegate: wraps the cops-n-crooks {@link KillCombo} tracker and {@link EntityMarkManager} NPC recognition
 * behind the core {@link WantedKillTracker} contract. Installed into the core {@code WantedKillTrackers} holder by
 * {@code CopsNCrooksModuleConfig.installCoreSeams()}. See documentation/module-loader.md, "Core seams".
 */
public final class KillComboWantedTracker implements WantedKillTracker {

	private final KillCombo         killCombo;
	private final EntityMarkManager entityMarks;

	public KillComboWantedTracker(KillCombo killCombo, EntityMarkManager entityMarks) {
		this.killCombo   = killCombo;
		this.entityMarks = entityMarks;
	}

	@Override
	public boolean countsForWanted(Entity victim) {
		return entityMarks.countsForWanted(victim);
	}

	@Override
	public void recordKill(Player killer, Wanted wanted, Entity victim, int resetAfterSeconds) {
		killCombo.recordKill(killer, wanted, victim, resetAfterSeconds);
	}

	@Override
	public void resetCombo(UUID victimId) {
		killCombo.resetCombo(victimId);
	}

	@Override
	public void onWantedTrigger(Consumer<Player> handler) {
		killCombo.setOnWantedLevelTrigger(event -> handler.accept(event.getPlayer()));
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
