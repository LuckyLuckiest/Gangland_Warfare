package org.luckyraven.gangland.copsncrooks.seam;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.civilians.npc.CivilianNpcRegistry;
import org.luckyraven.gangland.civilians.npc.CivilianState;
import org.luckyraven.gangland.civilians.npc.entity.EntityMarks;
import org.luckyraven.gangland.civilians.npc.npc.CivilianNpc;
import org.luckyraven.gangland.copsncrooks.combo.KillCombo;
import org.luckyraven.gangland.copsncrooks.heat.HeatService;
import org.luckyraven.gangland.core.wanted.Wanted;
import org.luckyraven.gangland.core.wanted.WantedKillTracker;
import org.luckyraven.keystone.npc.entity.NpcMarkManager;

import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Seam 3 delegate: wraps the cops-n-crooks {@link KillCombo} tracker and the shared {@link EntityMarks}/
 * {@link NpcMarkManager} NPC recognition behind the core {@link WantedKillTracker} contract. Installed into the
 * core {@code WantedKillTrackers} holder by {@code CopsNCrooksModuleConfig.installCoreSeams()}. See
 * documentation/module-loader.md, "Core seams".
 *
 * <p>0.12 heat ledger: with a {@link HeatService} whose {@code Heat.Enable} is on, every kill is scored in heat
 * ({@link #scoresAllKills()}) and stars are raised through the heat star trigger instead of the legacy
 * {@code Kill_Counter} thresholds. The self-defence exemption (killing a hostile civilian that is fighting back, i.e.
 * in {@link CivilianState#COMBAT}) lives in {@link #countsForWanted}, so it applies to every kill path the host
 * checks through this seam.
 */
public final class KillComboWantedTracker implements WantedKillTracker {

	private final           KillCombo           killCombo;
	private final           NpcMarkManager      markManager;
	private final @Nullable CivilianNpcRegistry civilianNpcRegistry;
	private final @Nullable HeatService         heatService;

	public KillComboWantedTracker(KillCombo killCombo, NpcMarkManager markManager) {
		this(killCombo, markManager, null, null);
	}

	public KillComboWantedTracker(KillCombo killCombo, NpcMarkManager markManager,
	                              @Nullable CivilianNpcRegistry civilianNpcRegistry,
	                              @Nullable HeatService heatService) {
		this.killCombo           = killCombo;
		this.markManager         = markManager;
		this.civilianNpcRegistry = civilianNpcRegistry;
		this.heatService         = heatService;
	}

	@Override
	public boolean countsForWanted(Entity victim) {
		return EntityMarks.countsForWanted(victim, markManager) && !isSelfDefence(victim);
	}

	@Override
	public boolean scoresAllKills() {
		return heatService != null && heatService.isEnabled();
	}

	@Override
	public void recordKill(Player killer, Wanted wanted, Entity victim, int resetAfterSeconds) {
		HeatService heat = this.heatService;

		if (heat != null && heat.isEnabled()) {
			int points = heat.recordCrime(killer, wanted, heat.classifyKill(victim), victim.getLocation());

			killCombo.recordKill(killer, wanted, victim, resetAfterSeconds, points, false);
			return;
		}

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

	@Override
	public void onHeatStarTrigger(BiConsumer<Player, Integer> handler) {
		if (heatService != null) heatService.setStarTrigger(handler);
	}

	/**
	 * A hostile civilian that is fighting back (in {@link CivilianState#COMBAT}) is fair game: killing it never raises
	 * wanted.
	 */
	private boolean isSelfDefence(Entity victim) {
		if (civilianNpcRegistry == null) return false;

		CivilianNpc npc = civilianNpcRegistry.getNpc(victim.getUniqueId());
		return npc != null && npc.isHostile() && npc.getCurrentState() == CivilianState.COMBAT;
	}
}
