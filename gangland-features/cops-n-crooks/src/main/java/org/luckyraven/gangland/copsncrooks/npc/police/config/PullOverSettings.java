package org.luckyraven.gangland.copsncrooks.npc.police.config;

/** Speeding pull-overs and tickets ({@code Cops.Pull_Over}); speeds in blocks per second. @since 0.20.0 */
public record PullOverSettings(boolean enabled, double range, double speedLimit, int complySeconds, double stopSpeed, int stopHoldSeconds, double ticketAmount, int repeatSeconds) {

	/** TDD stub: neutral values until the green chunk sets the shipped defaults and clamps. */
	public static final PullOverSettings DEFAULT = new PullOverSettings(false, 0.0, 0.0, 0, 0.0, 0, 0.0, 0);
}
