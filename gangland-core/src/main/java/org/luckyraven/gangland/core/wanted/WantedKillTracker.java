package org.luckyraven.gangland.core.wanted;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.function.BiConsumer;
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

	/**
	 * Whether this tracker scores every wanted-relevant kill itself (the 0.12 heat ledger). When {@code true} the host
	 * routes every kill through {@link #recordKill} even with {@code Wanted.Kill_Combo} disabled, and star gains come
	 * back through {@link #onHeatStarTrigger} instead of a flat per-kill increment.
	 *
	 * @return {@code true} when kills are scored by heat; {@code false} (the default) keeps the pre-0.12 behaviour
	 */
	default boolean scoresAllKills() {
		return false;
	}

	/**
	 * Registers the handler that raises a player's wanted level to a heat-derived target.
	 *
	 * @param handler receives the offending player and the star level their heat now reaches
	 */
	default void onHeatStarTrigger(BiConsumer<Player, Integer> handler) { }
}
