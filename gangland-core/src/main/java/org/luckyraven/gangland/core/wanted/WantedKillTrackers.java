package org.luckyraven.gangland.core.wanted;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntSupplier;

/**
 * Seam 3 holder: kill-combo tracking. Always present as a core bean so {@code EntityDamageListener} always
 * constructs; inert (the core's one-star-per-kill path) until the cops-n-crooks module installs a delegate. Since api
 * 2.2 the combo switch and reset window belong to the delegate ({@link WantedKillTracker#readsComboSettings()}); the
 * legacy suppliers only feed a delegate built before that. See documentation/module-loader.md, "Core seams".
 */
public final class WantedKillTrackers {

	private final BooleanSupplier legacyComboEnabled;
	private final IntSupplier     legacyResetAfterSeconds;

	private volatile WantedKillTracker delegate;

	private Consumer<Player> wantedTrigger;
	private Consumer<Player> comboReset;
	private Consumer<UUID>   victimDeath;

	/** No legacy combo values: a pre-2.2 delegate sees the combo off. */
	public WantedKillTrackers() {
		this(() -> false, () -> 0);
	}

	/**
	 * @param legacyComboEnabled      the old {@code settings.yml} {@code Wanted.Kill_Combo.Enable}, read per kill and
	 *                                only for a delegate that does not {@linkplain WantedKillTracker#readsComboSettings()
	 *                                read its own}
	 * @param legacyResetAfterSeconds the old {@code Wanted.Kill_Combo.Reset_After}, same rule
	 * @since gangland-api 2.2
	 */
	public WantedKillTrackers(BooleanSupplier legacyComboEnabled, IntSupplier legacyResetAfterSeconds) {
		this.legacyComboEnabled      = legacyComboEnabled;
		this.legacyResetAfterSeconds = legacyResetAfterSeconds;
	}

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

	/**
	 * True when a delegate gets the counted kills: always one that reads its own combo settings or applies the combo
	 * switch, else (a pre-0.15 delegate) only with the legacy combo switch on.
	 *
	 * @since gangland-api 2.2
	 */
	public boolean routesKills() {
		WantedKillTracker current = this.delegate;
		return current != null && (current.readsComboSettings() || current.appliesComboSwitch() ||
		                           legacyComboEnabled.getAsBoolean());
	}

	/**
	 * Hands the kill to the delegate, with the legacy reset window for a delegate that does not read its own.
	 *
	 * @since gangland-api 2.2
	 */
	public void recordKill(Player killer, Wanted wanted, Entity victim) {
		WantedKillTracker current = this.delegate;
		if (current == null) return;

		int resetAfter = current.readsComboSettings() ? 0 : legacyResetAfterSeconds.getAsInt();
		current.recordKill(killer, wanted, victim, resetAfter);
	}

	/**
	 * @deprecated since api 2.2 the switch belongs to the delegate; use {@link #routesKills()}.
	 */
	@Deprecated
	public boolean routesKills(boolean comboEnabled) {
		WantedKillTracker current = this.delegate;
		return current != null && (comboEnabled || current.appliesComboSwitch());
	}

	public boolean exemptsKill(Player killer, Entity victim) {
		WantedKillTracker current = this.delegate;
		return current != null && current.exemptsKill(killer, victim);
	}

	/**
	 * @deprecated since api 2.2 the reset window belongs to the delegate; use {@link #recordKill(Player, Wanted, Entity)}.
	 */
	@Deprecated
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
