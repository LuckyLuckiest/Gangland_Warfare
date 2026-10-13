package org.luckyraven.gangland.civilians.witness;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.civilians.npc.CivilianService;
import org.luckyraven.gangland.civilians.npc.npc.CivilianNpc;
import org.luckyraven.gangland.crime.CrimeService;

import java.util.Collection;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Civilians-owned witness bean (cops call it): finds the civilian who saw an unwitnessed crime and runs that civilian's
 * 911 call until it lands or is stopped.
 *
 * @since 0.20.0
 */
public final class WitnessService {

	/** Whether {@code npc} has {@code criminal} in line of sight within {@code range}. */
	@FunctionalInterface
	public interface Sight {

		boolean sees(CivilianNpc npc, Player criminal, double range);
	}

	/** Whether {@code criminal} is aiming a weapon at {@code caller} (within range blocks and angleDegrees). */
	@FunctionalInterface
	public interface Aim {

		boolean aimsAt(Player criminal, CivilianNpc caller, double range, double angleDegrees);
	}

	/** Time, sight, aim, event bus, action-bar and debug sinks; replaced by fakes in tests. */
	public record Hooks(LongSupplier clock, Sight sight, Aim aim, Consumer<Event> events,
	                    BiConsumer<Player, String> notifier, Consumer<String> debug) {

		/** The production hooks: wall clock, {@code npc.canSee}, {@link WitnessAim}, Bukkit events, action bar. */
		public static Hooks live(JavaPlugin plugin) {
			throw new UnsupportedOperationException("TDD stub");
		}
	}

	public WitnessService(CivilianService civilians, CrimeService crimes, Supplier<WitnessRules> rules,
	                      Supplier<String> callingMessage, Hooks hooks) {
	}

	/** Starts a call for a gated crime at {@code at}; returns how many callers started (0 or 1). */
	public int witness(Player criminal, String crimeId, Location at) {
		throw new UnsupportedOperationException("TDD stub");
	}

	/** A player hit a civilian (not lethal): cancels that civilian's own call, and publishes Assault_Civilian (throttled). */
	public void onPlayerHitCivilian(Player attacker, CivilianNpc victim) {
		onPlayerHitCivilian(attacker, victim, false);
	}

	/**
	 * A player hit a civilian. When {@code lethal} the hit kills it: the call stays attached for
	 * {@link #onCallerDeath} (hand-over) and no Assault_Civilian is published (Kill_Civilian covers the kill).
	 */
	public void onPlayerHitCivilian(Player attacker, CivilianNpc victim, boolean lethal) {
		throw new UnsupportedOperationException("TDD stub");
	}

	/**
	 * Forgets {@code npc}'s call because shipped code moved it out of CALLING behind the service's back (alertFaction,
	 * an NPC attacker, player death): removes it from the map and the npc, logs CALL_CANCEL reason=lost, never
	 * transitions.
	 */
	public void dropCall(CivilianNpc npc) {
		throw new UnsupportedOperationException("TDD stub");
	}

	/** One AI tick of a calling civilian: scare poll, then landing when Call_Seconds have passed. */
	public void tickCaller(CivilianNpc caller) {
		throw new UnsupportedOperationException("TDD stub");
	}

	/** Ends {@code caller}'s call without a report; HIT and SCARED send the caller fleeing. */
	public void cancelCall(CivilianNpc caller, CancelReason reason) {
		throw new UnsupportedOperationException("TDD stub");
	}

	/** The caller died: the next watcher that still sees the criminal starts a full new call, else the call is lost. */
	public void onCallerDeath(CivilianNpc caller) {
		throw new UnsupportedOperationException("TDD stub");
	}

	/** The criminal left the server: every call about them is cancelled. */
	public void onCriminalQuit(UUID criminalId) {
		throw new UnsupportedOperationException("TDD stub");
	}

	/** Every running call (snapshot). */
	public Collection<WitnessCall> activeCalls() {
		throw new UnsupportedOperationException("TDD stub");
	}
}
