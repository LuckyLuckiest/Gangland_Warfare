package org.luckyraven.gangland.civilians.witness;

import lombok.CustomLog;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.civilians.npc.CivilianService;
import org.luckyraven.gangland.civilians.npc.CivilianState;
import org.luckyraven.gangland.civilians.npc.npc.CivilianNpc;
import org.luckyraven.gangland.civilians.events.CivilianCalledInEvent;
import org.luckyraven.gangland.crime.CrimeService;
import org.luckyraven.gangland.crime.Crimes;
import org.luckyraven.keystone.util.ActionBarManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
@CustomLog
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
			return new Hooks(System::currentTimeMillis,
			                 (npc, criminal, range) -> npc.canSee(criminal, range),
			                 new WitnessAim(new BartizanArmed()),
			                 event -> Bukkit.getPluginManager().callEvent(event),
			                 (player, text) -> ActionBarManager.send(plugin, player, text, 100L),
			                 line -> log.debug(line));
		}
	}

	/** A player's last civilian victim, kept for 2 s so the victim is never its own witness. */
	private record Victim(UUID victimId, long atMillis) {
	}

	private record AssaultKey(UUID attacker, UUID victim) {
	}

	private static final long VICTIM_EXCLUSION_MILLIS = 2000L;

	private final CivilianService        civilians;
	private final CrimeService           crimes;
	private final Supplier<WitnessRules> rules;
	private final Supplier<String>       callingMessage;
	private final Hooks                  hooks;
	private final Map<UUID, WitnessCall> calls      = new LinkedHashMap<>();
	private final Map<UUID, Victim>      lastVictim = new HashMap<>();
	private final Map<AssaultKey, Long>  lastAssault = new HashMap<>();

	public WitnessService(CivilianService civilians, CrimeService crimes, Supplier<WitnessRules> rules,
	                      Supplier<String> callingMessage, Hooks hooks) {
		this.civilians       = civilians;
		this.crimes          = crimes;
		this.rules           = rules;
		this.callingMessage  = callingMessage;
		this.hooks           = hooks;
	}

	/** Starts a call for a gated crime at {@code at}; returns how many callers started (0 or 1). */
	public int witness(Player criminal, String crimeId, Location at) {
		WitnessRules rules = this.rules.get();
		if (!rules.enabled() || !criminal.isOnline() || at.getWorld() == null) return 0;

		UUID victimId = recentVictim(criminal);
		List<CivilianNpc> candidates = new ArrayList<>();
		for (CivilianNpc npc : civilians.getActiveNpcs()) {
			LivingEntity entity = npc.isValid() ? npc.getEntity() : null;
			if (entity == null || !at.getWorld().equals(entity.getWorld())) continue;
			if (entity.getLocation().distance(at) > rules.range()) continue;

			CivilianState state = npc.getCurrentState();
			if (state == CivilianState.FLEEING || state == CivilianState.COMBAT || state == CivilianState.CALLING) {
				continue;
			}
			if (entity.getUniqueId().equals(victimId)) continue;
			if (!hooks.sight().sees(npc, criminal, rules.range())) continue;
			candidates.add(npc);
		}

		if (candidates.isEmpty()) {
			hooks.debug().accept("WITNESS " + criminal.getName() + " crime=" + crimeId + " callers=0");
			return 0;
		}

		candidates.sort(Comparator.comparingDouble(npc -> npc.getEntity().getLocation().distanceSquared(at)));
		CivilianNpc caller = candidates.get(0);
		startCall(criminal, crimeId, at, caller, candidates.subList(1, candidates.size()), victimId);

		hooks.notifier().accept(criminal, callingMessage.get());
		hooks.debug().accept("WITNESS " + criminal.getName() + " crime=" + crimeId + " callers=1");
		return 1;
	}

	/** The victim of this attacker's last hit, while the 2 s exclusion window is open; null otherwise. */
	private @Nullable UUID recentVictim(Player attacker) {
		Victim victim = lastVictim.get(attacker.getUniqueId());
		if (victim == null) return null;
		boolean open = hooks.clock().getAsLong() - victim.atMillis() <= VICTIM_EXCLUSION_MILLIS;
		return open ? victim.victimId() : null;
	}

	/** Attaches a new call to {@code caller} and moves it into CALLING. */
	private void startCall(Player criminal, String crimeId, Location at, CivilianNpc caller,
	                       List<CivilianNpc> watchers, UUID victimId) {
		WitnessCall call = new WitnessCall(criminal, crimeId, at, caller, watchers, hooks.clock().getAsLong(),
		                                   victimId, rules.get().callerGlow(), this);
		calls.put(caller.getEntity().getUniqueId(), call);
		caller.setWitnessCall(call);
		caller.transitionTo(CivilianState.CALLING);
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
		long now = hooks.clock().getAsLong();
		LivingEntity entity = victim.getEntity();
		if (entity == null) return;

		if (!lethal && victim.getWitnessCall() != null) cancelCall(victim, CancelReason.HIT);

		UUID victimId = entity.getUniqueId();
		lastVictim.put(attacker.getUniqueId(), new Victim(victimId, now));

		WitnessRules rules = this.rules.get();
		if (lethal || !rules.reportAssault()) return;

		AssaultKey key  = new AssaultKey(attacker.getUniqueId(), victimId);
		Long       last = lastAssault.get(key);
		if (last != null && now - last < rules.assaultRepeatSeconds() * 1000L) return;

		lastAssault.values().removeIf(t -> now - t >= rules.assaultRepeatSeconds() * 1000L);
		lastAssault.put(key, now);
		crimes.commit(attacker, Crimes.ASSAULT_CIVILIAN, entity.getLocation());
	}

	/**
	 * Forgets {@code npc}'s call because shipped code moved it out of CALLING behind the service's back (alertFaction,
	 * an NPC attacker, player death): removes it from the map and the npc, logs CALL_CANCEL reason=lost, never
	 * transitions.
	 */
	public void dropCall(CivilianNpc npc) {
		WitnessCall call = npc.getWitnessCall();
		if (call == null) return;

		detach(call);
		logCancel(call, CancelReason.LOST);
	}

	/** One AI tick of a calling civilian: scare poll, then landing when Call_Seconds have passed. */
	public void tickCaller(CivilianNpc caller) {
		WitnessCall call = caller.getWitnessCall();
		if (call == null) return;

		WitnessRules rules = this.rules.get();
		if (!call.criminal().isOnline()) {
			cancelCall(caller, CancelReason.LOGOUT);
			return;
		}
		if (!caller.isValid()) {
			cancelCall(caller, CancelReason.LOST);
			return;
		}
		if (hooks.aim().aimsAt(call.criminal(), caller, rules.scareRange(), rules.scareAngle())) {
			cancelCall(caller, CancelReason.SCARED);
			return;
		}
		if (hooks.clock().getAsLong() - call.startedAtMillis() < rules.callSeconds() * 1000L) return;

		detach(call);
		hooks.events().accept(new CivilianCalledInEvent(call.criminal(), call.crimeId(), call.at(), caller.getEntity()));
		hooks.debug().accept("CALL_IN " + call.criminal().getName() + " crime=" + call.crimeId() + " caller="
				                     + caller.getEntity().getName());
		flee(caller, call);
	}

	/** Ends {@code caller}'s call without a report; HIT and SCARED send the caller fleeing. */
	public void cancelCall(CivilianNpc caller, CancelReason reason) {
		WitnessCall call = caller.getWitnessCall();
		if (call == null) return;

		detach(call);
		logCancel(call, reason);
		if (reason == CancelReason.HIT || reason == CancelReason.SCARED) {
			flee(caller, call);
		} else if (reason == CancelReason.LOGOUT && caller.isValid()) {
			caller.transitionTo(CivilianState.IDLE);
		}
	}

	/** The caller died: the next watcher that still sees the criminal starts a full new call, else the call is lost. */
	public void onCallerDeath(CivilianNpc caller) {
		WitnessCall call = caller.getWitnessCall();
		if (call == null) return;

		detach(call);
		Player criminal = call.criminal();
		if (criminal.isOnline()) {
			double range = rules.get().range();
			List<CivilianNpc> watchers = new ArrayList<>(call.watchers());
			for (CivilianNpc watcher : watchers) {
				if (!watcher.isValid() || watcher.getEntity() == null) continue;
				if (!hooks.sight().sees(watcher, criminal, range)) continue;

				List<CivilianNpc> rest = new ArrayList<>(watchers);
				rest.remove(watcher);
				startCall(criminal, call.crimeId(), call.at(), watcher, rest, call.victimId());
				return;
			}
		}
		logCancel(call, CancelReason.KILLED);
	}

	/** The criminal left the server: every call about them is cancelled. */
	public void onCriminalQuit(UUID criminalId) {
		for (WitnessCall call : activeCalls()) {
			if (call.criminal().getUniqueId().equals(criminalId)) cancelCall(call.caller(), CancelReason.LOGOUT);
		}
	}

	/** Removes the call from the map and from its caller. */
	private void detach(WitnessCall call) {
		calls.values().remove(call);
		call.caller().setWitnessCall(null);
	}

	private void logCancel(WitnessCall call, CancelReason reason) {
		hooks.debug().accept("CALL_CANCEL " + call.criminal().getName() + " crime=" + call.crimeId() + " reason="
				                     + reason.debugName());
	}

	/** Sends the caller fleeing from the criminal; the call is already detached. */
	private void flee(CivilianNpc caller, WitnessCall call) {
		caller.setLastAttackerLocation(call.criminal().getLocation());
		caller.transitionTo(CivilianState.FLEEING);
	}

	/** Every running call (snapshot). */
	public Collection<WitnessCall> activeCalls() {
		return List.copyOf(calls.values());
	}
}
