package org.luckyraven.gangland.gang.wanted;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Seam 3: kill-combo tracking and "does this NPC count for wanted" recognition. {@link WantedKillTrackers} is the
 * always-present holder; the cops-n-crooks module installs a delegate implementing this interface from its
 * {@code @PostConstruct} once it is loaded. See documentation/module-loader.md, "Core seams".
 */
public interface WantedKillTracker {

	boolean countsForWanted(Entity victim);

	void recordKill(Player killer, Wanted wanted, Entity victim, int resetAfterSeconds);

	void resetCombo(UUID victimId);

	void onWantedTrigger(Consumer<Player> handler);

	void onComboReset(Consumer<Player> handler);

	void onVictimDeath(Consumer<UUID> handler);
}
