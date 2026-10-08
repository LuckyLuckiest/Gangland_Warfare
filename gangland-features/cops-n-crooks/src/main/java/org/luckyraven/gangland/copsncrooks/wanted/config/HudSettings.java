package org.luckyraven.gangland.copsncrooks.wanted.config;

import org.luckyraven.gangland.copsncrooks.wanted.hud.TitleCue;

/**
 * {@code Wanted.Hud} of {@code copsncrooks/wanted.yml}: each part of the chase HUD behind its own switch. The three title
 * cues are the gained, lost and escaped events.
 *
 * @since 0.15.0
 */
public record HudSettings(boolean bossBar, boolean starCard, TitleCue gain, TitleCue lost, TitleCue escaped,
                          boolean siren, String sirenSound, float sirenVolume, float sirenPitch, boolean zoneRing,
                          String zoneParticle, int zonePoints, boolean compass) {
	/** The shipped {@code Wanted.Hud}. */
	public static final HudSettings DEFAULT = new HudSettings(true, true, TitleCue.DEFAULT, TitleCue.DEFAULT,
	                                                          TitleCue.ESCAPED, true, "BLOCK_NOTE_BLOCK_BELL", 1.0f, 0.5f,
	                                                          true, "DUST", 48, true);
}
