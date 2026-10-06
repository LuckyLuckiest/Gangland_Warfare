package org.luckyraven.gangland.core.wanted;

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

	/**
	 * True when this kill is no crime at all (0.15.0: a player kill defending your own contested turf), so the core
	 * adds no kill notoriety and starts no bounty timer either. A delegate built before 0.15.0 exempts nothing.
	 *
	 * @since gangland-api 2.1
	 */
	default boolean exemptsKill(Player killer, Entity victim) {
		return false;
	}

	/**
	 * True when the delegate applies {@code Wanted.Kill_Combo.Enable} itself (0.15.0's heat tracker), so the core sends
	 * it every counted kill. A delegate built before 0.15.0 answers false and the core keeps the switch for it: with the
	 * combo off its kills take the core's one-star path, as on a 2.0 host.
	 *
	 * @since gangland-api 2.1
	 */
	default boolean appliesComboSwitch() {
		return false;
	}

	/**
	 * True when the delegate reads {@code Wanted.Kill_Combo.Enable} and {@code .Reset_After} from its own config
	 * (0.15.1: cops-n-crooks {@code copsncrooks/wanted.yml}) and applies them itself. The core then routes it every
	 * counted kill and passes {@code 0} as {@link #recordKill}'s {@code resetAfterSeconds}, which such a delegate
	 * ignores. A delegate built before api 2.2 answers false and keeps getting the legacy {@code settings.yml} values.
	 *
	 * @since gangland-api 2.2
	 */
	default boolean readsComboSettings() {
		return false;
	}
}
