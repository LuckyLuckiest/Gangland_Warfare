package org.luckyraven.gangland.copsncrooks.npc.police.config;

/**
 * The pursuit hand-off when a suspect breaks contact ({@code cops.yml}'s {@code Cops.Handoff}): the squad radios the
 * heading and units ahead intercept.
 *
 * @param enabled        {@code false} keeps the 0.15 behaviour.
 * @param headingSeconds seconds of travel used to read the suspect's heading.
 * @param biasSeconds    seconds a hand-off biases where reinforcements appear.
 * @param coneDegrees    half-angle, either side of the heading, of the bias cone.
 * @param leadBlocks     0.21.1 (owner ruling R5): how far along the squad-seen heading, past the squad's last-known
 *                       spot, the hand-off units are sent; clamped at use inside the spawn leash margin.
 * @since 0.16.0
 */
public record HandoffSettings(boolean enabled, int headingSeconds, int biasSeconds, double coneDegrees,
                              double leadBlocks) {

	/** Matches the shipped cops.yml: heading over 2 s, bias for 10 s, 60 degree half-angle, 24 blocks of lead. */
	public static final HandoffSettings DEFAULT = new HandoffSettings(true, 2, 10, 60.0, 24.0);
}
