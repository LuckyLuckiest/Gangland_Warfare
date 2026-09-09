package org.luckyraven.gangland.copsncrooks.seam;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.civilians.npc.entity.EntityMarks;
import org.luckyraven.gangland.copsncrooks.combo.KillCombo;
import org.luckyraven.gangland.gang.wanted.Wanted;
import org.luckyraven.gangland.gang.wanted.WantedKillTracker;
import org.luckyraven.keystone.npc.entity.NpcMarkManager;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Seam 3 delegate: wraps the cops-n-crooks {@link KillCombo} tracker and the shared {@link EntityMarks}/
 * {@link NpcMarkManager} NPC recognition behind the core {@link WantedKillTracker} contract. Installed into the
 * core {@code WantedKillTrackers} holder by {@code CopsNCrooksModuleConfig.installCoreSeams()}. See
 * documentation/module-loader.md, "Core seams".
 */
public final class KillComboWantedTracker implements WantedKillTracker {

	private final KillCombo      killCombo;
	private final NpcMarkManager markManager;

	public KillComboWantedTracker(KillCombo killCombo, NpcMarkManager markManager) {
		this.killCombo   = killCombo;
		this.markManager = markManager;
	}

	@Override
	public boolean countsForWanted(Entity victim) {
		return EntityMarks.countsForWanted(victim, markManager);
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
