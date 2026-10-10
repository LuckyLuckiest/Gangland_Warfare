package org.luckyraven.gangland.turf.capture;

import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.contract.GangLookupContract;
import org.luckyraven.gangland.turf.data.Turf;

/**
 * The one capture verdict every surface reads (P2.2): the map hover, the HUD line, the turf info card, the enter
 * feedback and the capture boss bar. Pure and Bukkit-free. Extracted in 0.17.0 (W1 kickoff) from
 * CaptureService#isCapturable with identical behaviour; W2 changes the rules here, nowhere else.
 */
public final class CaptureEligibility {

	public enum Reason {
		/** Nothing stops a capture. */
		ATTACKABLE,
		/** Captured less than Cooldown_Minutes ago; untilMillis = when the cooldown ends. */
		COOLDOWN,
		/** An owner was online within Post_Logoff_Protection_Minutes; untilMillis = lastMemberOnlineAt + window. */
		OWNERS_ACTIVE
	}

	/** untilMillis is 0 for ATTACKABLE. */
	public record Verdict(Reason reason, long untilMillis) {

		public boolean attackable() {
			return reason == Reason.ATTACKABLE;
		}
	}

	private static final Verdict ATTACKABLE_NOW = new Verdict(Reason.ATTACKABLE, 0L);

	private final GangLookupContract gangs;
	private final CaptureSettings    settings;

	public CaptureEligibility(GangLookupContract gangs, CaptureSettings settings) {
		this.gangs    = gangs;
		this.settings = settings;
	}

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
		// Post-logoff grace: protected if any member online OR last-online within the grace window.
		long graceMs = settings.getPostLogoffProtectionMinutes() * 60_000L;
		if (now - owner.getLastMemberOnlineAt() > graceMs) {
			return ATTACKABLE_NOW;
		}
		return new Verdict(Reason.OWNERS_ACTIVE, owner.getLastMemberOnlineAt() + graceMs);
	}
}
