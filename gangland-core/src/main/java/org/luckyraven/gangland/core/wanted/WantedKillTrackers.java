package org.luckyraven.gangland.core.wanted;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Seam 3 holder: kill-combo tracking. Always present as a core bean so {@code EntityDamageListener} always
 * constructs; inert (combo-disabled behaviour, i.e. the existing {@code Settings.isWantedKillComboEnabled()}
 * branches take over) until the cops-n-crooks module installs a delegate. See documentation/module-loader.md,
 * "Core seams".
 */
public final class WantedKillTrackers {

	private volatile WantedKillTracker delegate;

	private Consumer<Player> wantedTrigger;
	private Consumer<Player> comboReset;
	private Consumer<UUID>   victimDeath;

	public boolean isActive() {
		return delegate != null;
	}

	/**
	 * Installs the delegate and replays any handler already registered via {@code on*} onto it, so registration
	 * order between the module's install call and the listener's handler registration never matters.
	 */
	public void install(WantedKillTracker tracker) {
		this.delegate = tracker;
		if (wantedTrigger != null) tracker.onWantedTrigger(wantedTrigger);
		if (comboReset != null) tracker.onComboReset(comboReset);
		if (victimDeath != null) tracker.onVictimDeath(victimDeath);
	}

	public boolean countsForWanted(Entity victim) {
		WantedKillTracker current = this.delegate;
		return current != null && current.countsForWanted(victim);
	}

	public void recordKill(Player killer, Wanted wanted, Entity victim, int resetAfterSeconds) {
		WantedKillTracker current = this.delegate;
		if (current != null) current.recordKill(killer, wanted, victim, resetAfterSeconds);
	}

	public void resetCombo(UUID victimId) {
		WantedKillTracker current = this.delegate;
		if (current != null) current.resetCombo(victimId);
	}

	public void onWantedTrigger(Consumer<Player> handler) {
		this.wantedTrigger = handler;
		WantedKillTracker current = this.delegate;
		if (current != null) current.onWantedTrigger(handler);
	}

	public void onComboReset(Consumer<Player> handler) {
		this.comboReset = handler;
		WantedKillTracker current = this.delegate;
		if (current != null) current.onComboReset(handler);
	}

	public void onVictimDeath(Consumer<UUID> handler) {
		this.victimDeath = handler;
		WantedKillTracker current = this.delegate;
		if (current != null) current.onVictimDeath(handler);
	}
}
