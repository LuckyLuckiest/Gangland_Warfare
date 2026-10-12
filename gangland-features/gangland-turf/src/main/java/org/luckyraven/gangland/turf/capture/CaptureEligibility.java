package org.luckyraven.gangland.turf.capture;

import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.contract.GangLookupContract;
import org.luckyraven.gangland.turf.data.Turf;

import java.util.function.IntToLongFunction;
import java.util.function.IntUnaryOperator;
import java.util.function.Supplier;

/**
 * The one capture verdict every surface reads (P2.2): the map hover, the HUD line, the turf info card, the enter
 * feedback and the capture boss bar. Pure and Bukkit-free. Extracted in 0.17.0 (W1 kickoff) from
 * CaptureService#isCapturable with identical behaviour. W2-R1 adds the OWNERS_ONLINE window and the wake-up shield inside
 * evaluate; evaluateFor adds the turf cap.
 */
public final class CaptureEligibility {

	public enum Reason {
		/** Nothing stops a capture. */
		ATTACKABLE,
		/** Captured less than Cooldown_Minutes ago; untilMillis = when the cooldown ends. */
		COOLDOWN,
		/** An owner was online within Post_Logoff_Protection_Minutes; untilMillis = lastMemberOnlineAt + window. */
		OWNERS_ACTIVE,
		/** OWNERS_ONLINE: no qualifying owner online now or within the logout window; untilMillis 0 - attackable when one logs in. */
		NO_OWNERS_ONLINE,
		/** OWNERS_ONLINE: an owner came online less than Wake_Up_Shield_Seconds ago; untilMillis = end of the shield. */
		WAKING_UP,
		/** evaluateFor only: the attacking gang already holds its turf cap; untilMillis 0. */
		OVER_CAP
	}

	/** untilMillis is 0 for ATTACKABLE. */
	public record Verdict(Reason reason, long untilMillis) {

		public boolean attackable() {
			return reason == Reason.ATTACKABLE;
		}
	}

	private static final Verdict ATTACKABLE_NOW = new Verdict(Reason.ATTACKABLE, 0L);

	/** Shortest OWNERS_ONLINE logout window, in ms (see {@link #onlineWindowMs}). */
	public static final long MIN_ONLINE_WINDOW_MS = 120_000L;

	private final GangLookupContract gangs;
	private final CaptureSettings    settings;
	private final Supplier<CaptureRules> rules;
	private final IntToLongFunction      wokeAt;
	private final IntUnaryOperator       turfCap;
	private final IntUnaryOperator       ownedTurfs;

	/** Today's rules (CaptureRules.LEGACY), no wake-up record and no cap - for callers and tests that predate W2. */
	public CaptureEligibility(GangLookupContract gangs, CaptureSettings settings) {
		this(gangs, settings, () -> CaptureRules.LEGACY, gangId -> 0L, gangId -> Integer.MAX_VALUE, gangId -> 0);
	}

	/**
	 * @param rules      the capture rules, read on every call (TurfRules::capture in production)
	 * @param wokeAt     gang id -> epoch ms that gang last woke up (GangPresenceTracker::wokeAt), 0 when unknown
	 * @param turfCap    gang id -> how many turfs that gang may hold (TurfRules.cap of its level)
	 * @param ownedTurfs gang id -> how many turfs it holds (TurfManager::countOwnedBy)
	 */
	public CaptureEligibility(GangLookupContract gangs, CaptureSettings settings, Supplier<CaptureRules> rules,
	                          IntToLongFunction wokeAt, IntUnaryOperator turfCap, IntUnaryOperator ownedTurfs) {
		this.gangs      = gangs;
		this.settings   = settings;
		this.rules      = rules;
		this.wokeAt     = wokeAt;
		this.turfCap    = turfCap;
		this.ownedTurfs = ownedTurfs;
	}

	/** The rules in force right now (W2 readers: CaptureService, the feedback listener, the boss bar). */
	public CaptureRules rules() {
		return rules.get();
	}

	/**
	 * The verdict for one attacking gang: {@link #evaluate} first; when that says attackable but the gang already holds
	 * its turf cap, OVER_CAP.
	 */
	public Verdict evaluateFor(Turf turf, int attackerGangId, long now) {
		Verdict verdict = evaluate(turf, now);
		if (!verdict.attackable()) {
			return verdict;
		}
		if (overCap(attackerGangId)) {
			return new Verdict(Reason.OVER_CAP, 0L);
		}
		return verdict;
	}

	/** True when gang {@code gangId} already holds as many turfs as its level allows (W2-R2 re-checks it at complete). */
	public boolean overCap(int gangId) {
		return ownedTurfs.applyAsInt(gangId) >= turfCap.applyAsInt(gangId);
	}

	/**
	 * Two window modes, picked by {@link CaptureRules#window()}:
	 * <ul>
	 *   <li>OWNERS_OFFLINE (the pre-0.17.0 rule): OWNERS_ACTIVE while an owner was online within the logout window,
	 *       attackable after; the wake-up record is ignored.</li>
	 *   <li>OWNERS_ONLINE (default): attackable while a qualifying owner is online or was within the logout window
	 *       ({@link #onlineWindowMs}); NO_OWNERS_ONLINE after that; WAKING_UP while a wake-up shield set by
	 *       {@code wokeAt} runs.</li>
	 * </ul>
	 */
	public Verdict evaluate(Turf turf, long now) {
		long cooldownEnd = turf.getLastCaptureTimestamp() + settings.getCooldownMinutes() * 60_000L;
		if (now < cooldownEnd) {
			return new Verdict(Reason.COOLDOWN, cooldownEnd);
		}
		if (turf.isUnclaimed()) {
			return ATTACKABLE_NOW;
		}
		Gang owner = gangs.findById(turf.getOwnerGangId());
		if (owner == null) {
			return ATTACKABLE_NOW;
		}
		CaptureRules current = rules.get();
		long last = owner.getLastMemberOnlineAt();
		if (current.window() == CaptureRules.Window.OWNERS_OFFLINE) {
			// Post-logoff grace: protected if any member online OR last-online within the grace window.
			long graceMs = settings.getPostLogoffProtectionMinutes() * 60_000L;
			if (now - last > graceMs) {
				return ATTACKABLE_NOW;
			}
			return new Verdict(Reason.OWNERS_ACTIVE, last + graceMs);
		}
		long windowMs = onlineWindowMs(settings);
		if (last <= 0 || now - last > windowMs) {
			return new Verdict(Reason.NO_OWNERS_ONLINE, 0L);
		}
		long woke = wokeAt.applyAsLong(owner.getId());
		long shieldMs = current.wakeUpShieldSeconds() * 1000L;
		if (shieldMs > 0 && woke > 0 && now < woke + shieldMs) {
			return new Verdict(Reason.WAKING_UP, woke + shieldMs);
		}
		return ATTACKABLE_NOW;
	}

	/**
	 * The logout window the OWNERS_ONLINE rule uses: Post_Logoff_Protection_Minutes, but never under two minutes,
	 * because the heartbeat stamps once a minute and a shorter window would flicker while an owner stays online.
	 */
	public static long onlineWindowMs(CaptureSettings settings) {
		// ponytail: heartbeat is 1/min, so the floor is 2 min; a live stamp per login would allow 0.
		return Math.max(settings.getPostLogoffProtectionMinutes() * 60_000L, MIN_ONLINE_WINDOW_MS);
	}
}
