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
 * @param spawnBetween   0.21.1 (owner ruling T-423): how far from his live spot toward that lead the hand-off units'
 *                       spawn ring is centred, 0..1 (0 = around him, ahead of the heading, as 0.16; 0.5 = midway).
 * @since 0.16.0
 */
public record HandoffSettings(boolean enabled, int headingSeconds, int biasSeconds, double coneDegrees,
                              double leadBlocks, double spawnBetween) {

	/**
	 * Matches the shipped cops.yml: heading over 2 s, bias for 10 s, 60 degree half-angle, 24 blocks of lead, spawned
	 * midway between him and the lead.
	 */
	public static final HandoffSettings DEFAULT = new HandoffSettings(true, 2, 10, 60.0, 24.0, 0.5);

	/** The 0.21.1 r5 settings, spawned midway ({@code Spawn_Between} 0.5). */
	public HandoffSettings(boolean enabled, int headingSeconds, int biasSeconds, double coneDegrees, double leadBlocks) {
		this(enabled, headingSeconds, biasSeconds, coneDegrees, leadBlocks, 0.5);
	}
}
