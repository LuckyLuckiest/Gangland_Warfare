package org.luckyraven.gangland.civilians.witness;

import lombok.CustomLog;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.civilians.message.CivilianMessages;
import org.luckyraven.gangland.civilians.npc.CivilianService;
import org.luckyraven.gangland.civilians.npc.CivilianState;
import org.luckyraven.gangland.civilians.npc.npc.CivilianNpc;
import org.luckyraven.gangland.civilians.npc.state.behavior.CivilianLookController;
import org.luckyraven.gangland.civilians.events.CivilianCalledInEvent;
import org.luckyraven.gangland.crime.CrimeService;
import org.luckyraven.gangland.crime.Crimes;
import org.luckyraven.gangland.data.region.PlaceNames;
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
import java.util.function.IntSupplier;
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

		/** {@link #live(JavaPlugin, IntSupplier)} at the shipped 20-tick AI rate. */
		public static Hooks live(JavaPlugin plugin) {
			return live(plugin, () -> 20);
		}

		/**
		 * The production hooks: wall clock, {@code npc.canSee}, {@link WitnessAim}, Bukkit events, action bar. The bar
		 * lasts one AI tick ({@code aiTickRate}, game ticks) plus a second: the countdown refreshes it every AI tick, so
		 * it never blinks off in between, and the Stopped line fades soon after.
		 */
		public static Hooks live(JavaPlugin plugin, IntSupplier aiTickRate) {
			return new Hooks(System::currentTimeMillis,
			                 (npc, criminal, range) -> npc.canSee(criminal, range),
			                 new WitnessAim(new BartizanArmed()),
			                 event -> Bukkit.getPluginManager().callEvent(event),
			                 (player, text) -> ActionBarManager.send(plugin, player, text,
			                                                         Math.max(1, aiTickRate.getAsInt()) + 20L),
			                 line -> log.debug(line));
		}
	}

	/** A player's last civilian victim, kept for 2 s so the victim is never its own witness. */
	private record Victim(UUID victimId, long atMillis) {
	}

	private record AssaultKey(UUID attacker, UUID victim) {
	}

	private static final long VICTIM_EXCLUSION_MILLIS = 2000L;

	/** The format every 911 line is spoken in ({@code Shouts.Witness_Format} of civilian_messages.yml). */
	private static final String WITNESS_FORMAT = "Witness_Format";

	private final CivilianService        civilians;
	private final CrimeService           crimes;
	private final Supplier<WitnessRules> rules;
	private final CivilianMessages       messages;
	private final PlaceNames             places;
	private final Hooks                  hooks;
	private final Map<UUID, WitnessCall> calls      = new LinkedHashMap<>();
	private final Map<UUID, Victim>      lastVictim = new HashMap<>();
	private final Map<AssaultKey, Long>  lastAssault = new HashMap<>();
	/** Civilian entity id -> when the criminal stopped its call (hit, scared, intimidated): Scared_Cooldown_Seconds. */
	private final Map<UUID, Long>        stoppedAt  = new HashMap<>();
	/** Player id -> when he last fired a weapon: Loud_Shot_Seconds. */
	private final Map<UUID, Long>        lastShot   = new HashMap<>();

	public WitnessService(CivilianService civilians, CrimeService crimes, Supplier<WitnessRules> rules,
	                      CivilianMessages messages, PlaceNames places, Hooks hooks) {
		this.civilians = civilians;
		this.crimes    = crimes;
		this.rules     = rules;
		this.messages  = messages;
		this.places    = places;
		this.hooks     = hooks;
	}

	/** The service clock (milliseconds), for the CALLING behaviour's notice timer. */
	public long now() {
		return hooks.clock().getAsLong();
	}

	/** The criminal right-clicked {@code npc}: stops its call when he is its criminal and within Intimidate_Range. */
	public boolean intimidate(CivilianNpc npc, Player player) {
		WitnessCall  call   = npc.getWitnessCall();
		LivingEntity entity = npc.getEntity();
		double       range  = rules.get().intimidateRange();
		if (call == null || entity == null || range <= 0) return false;
		if (!call.criminal().getUniqueId().equals(player.getUniqueId())) return false;

		Location from = player.getLocation();
		if (from == null || from.getWorld() == null || !from.getWorld().equals(entity.getWorld())) return false;
		if (from.distance(entity.getLocation()) > range) return false;

		cancelCall(npc, CancelReason.INTIMIDATED);
		return true;
	}

	/**
	 * {@code shooter} fired a weapon of Bartizan category {@code weaponType}: when Loud_Weapon_Types lists it (a gun, a
	 * throwable; never a melee swing by default) his crimes in the next Loud_Shot_Seconds are loud.
	 */
	public void heardShot(Player shooter, String weaponType) {
		boolean noisy = rules.get().loudWeaponTypes().stream().anyMatch(type -> type.equalsIgnoreCase(weaponType));
		if (noisy) lastShot.put(shooter.getUniqueId(), now());
	}

	/**
	 * Whether {@code target} is inside the horizontal cone {@code viewAngle} degrees wide, centred on {@code eye}'s yaw
	 * (half the angle each side, the edge included). 360 or more sees all round.
	 */
	static boolean inView(Location eye, @Nullable Location target, double viewAngle) {
		if (viewAngle >= 360.0) return true;
		if (target == null) return false;

		double dx       = target.getX() - eye.getX();
		double dz       = target.getZ() - eye.getZ();
		double distance = Math.hypot(dx, dz);
		if (distance < 1.0E-4) return true;

		// Bukkit yaw: 0 = +Z, 90 = -X
		double yaw = Math.toRadians(eye.getYaw());
		double cos = (-Math.sin(yaw) * dx + Math.cos(yaw) * dz) / distance;
		return Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, cos)))) <= viewAngle / 2.0 + 1.0E-9;
	}

	/** A crime in Loud_Crimes, or one within Loud_Shot_Seconds of the criminal's own gunshot. */
	private boolean isLoud(Player criminal, String crimeId, WitnessRules rules, long now) {
		if (rules.loudCrimes().contains(crimeId)) return true;

		Long shot = lastShot.get(criminal.getUniqueId());
		return rules.loudShotSeconds() > 0 && shot != null && now - shot <= rules.loudShotSeconds() * 1000L;
	}

	/** Whether the criminal stopped this civilian's call less than Scared_Cooldown_Seconds ago. */
	private boolean stillScared(UUID civilianId, WitnessRules rules, long now) {
		Long at = stoppedAt.get(civilianId);
		if (at == null) return false;
		if (now - at < rules.scaredCooldownSeconds() * 1000L) return true;

		stoppedAt.remove(civilianId);
		return false;
	}

	/** Starts a call for a gated crime at {@code at}; returns how many callers started (0 or 1). */
	public int witness(Player criminal, String crimeId, Location at) {
		WitnessRules rules = this.rules.get();
		if (!rules.enabled() || !criminal.isOnline() || at.getWorld() == null) return 0;

		long              now        = now();
		boolean           loud       = isLoud(criminal, crimeId, rules, now);
		Location          criminalAt = criminal.getLocation();
		UUID              victimId   = recentVictim(criminal);
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
			if (stillScared(entity.getUniqueId(), rules, now)) continue;
			// a loud crime turns every free civilian in range to the noise (visibly), so the view cone is met; a silent
			// one counts only in front of a civilian
			if (loud) {
				CivilianLookController.faceLocation(entity, at.clone().add(0, 1.6, 0));
			} else if (!inView(entity.getLocation(), criminalAt, rules.viewAngle())) {
				continue;
			}
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
		                                   victimId, rules.get(), this);
		calls.put(caller.getEntity().getUniqueId(), call);
		caller.setWitnessCall(call);
		caller.transitionTo(CivilianState.CALLING);
		speak(caller, criminal, "Witness_Start", Map.of());
		countdown(call, rules.get().callSeconds());
	}

	/** Shows the criminal the countdown when its whole number of seconds changed. */
	private void countdown(WitnessCall call, int secondsLeft) {
		if (secondsLeft == call.shownSeconds()) return;

		call.shownSeconds(secondsLeft);
		hooks.notifier().accept(call.criminal(), messages.witnessCalling(civilians.callsign(call.caller()), secondsLeft));
	}

	/** {@code speaker} says one 911 line ({@code Shouts.Lines.<key>}) to the criminal and the bystanders. */
	private void speak(CivilianNpc speaker, Player criminal, String key, Map<String, String> extras) {
		civilians.shout(speaker, key, WITNESS_FORMAT, criminal, extras);
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
		long elapsed = hooks.clock().getAsLong() - call.startedAtMillis();
		long total   = rules.callSeconds() * 1000L;
		if (elapsed < total) {
			countdown(call, (int) ((total - elapsed + 999L) / 1000L));
			return;
		}

		detach(call);
		String place = places.locate(call.at()).orElseGet(messages::witnessUnknownPlace);
		speak(caller, call.criminal(), "Witness_Landed", Map.of("place", place));
		hooks.events().accept(new CivilianCalledInEvent(call.criminal(), call.crimeId(), call.at(), caller.getEntity()));
		hooks.debug().accept("CALL_IN " + call.criminal().getName() + " crime=" + call.crimeId() + " caller="
				                     + caller.getEntity().getName());
		flee(caller, call);
	}

	/**
	 * Ends {@code caller}'s call without a report. HIT, SCARED and INTIMIDATED are the criminal stopping it: the caller
	 * says Witness_Scared, the criminal is told Witness_Stopped, the caller flees and cannot call again for
	 * Scared_Cooldown_Seconds.
	 */
	public void cancelCall(CivilianNpc caller, CancelReason reason) {
		WitnessCall call = caller.getWitnessCall();
		if (call == null) return;

		detach(call);
		logCancel(call, reason);
		if (reason == CancelReason.HIT || reason == CancelReason.SCARED || reason == CancelReason.INTIMIDATED) {
			LivingEntity entity = caller.getEntity();
			long now      = now();
			long cooldown = rules.get().scaredCooldownSeconds() * 1000L;
			stoppedAt.values().removeIf(at -> now - at >= cooldown);
			if (entity != null) stoppedAt.put(entity.getUniqueId(), now);
			speak(caller, call.criminal(), "Witness_Scared", Map.of());
			hooks.notifier().accept(call.criminal(), messages.witnessStopped());
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
				// a watcher already busy (the gated kill of this caller started its own call there) keeps what it has
				CivilianState state = watcher.getCurrentState();
				if (state == CivilianState.CALLING || state == CivilianState.FLEEING || state == CivilianState.COMBAT) {
					continue;
				}
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
		lastShot.remove(criminalId);
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
