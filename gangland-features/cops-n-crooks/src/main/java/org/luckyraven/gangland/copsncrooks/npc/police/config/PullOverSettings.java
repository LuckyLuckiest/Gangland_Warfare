package org.luckyraven.gangland.copsncrooks.npc.police.config;

/** Speeding pull-overs and tickets ({@code Cops.Pull_Over}); speeds in blocks per second. @since 0.20.0 */
public record PullOverSettings(boolean enabled, double range, double speedLimit, int complySeconds, double stopSpeed, int stopHoldSeconds, double ticketAmount, int repeatSeconds) {

	/** Matches the 0.20.0 cops.yml defaults. */
	public static final PullOverSettings DEFAULT = new PullOverSettings(true, 24.0, 12.0, 6, 1.0, 2, 150.0, 120);

	public PullOverSettings {
		range = Math.max(1.0, range);
		speedLimit = Math.max(1.0, speedLimit);
		complySeconds = Math.max(1, complySeconds);
		stopSpeed = Math.max(0.0, stopSpeed);
		stopHoldSeconds = Math.max(0, stopHoldSeconds);
		ticketAmount = Math.max(0.0, ticketAmount);
		repeatSeconds = Math.max(0, repeatSeconds);
	}
}
