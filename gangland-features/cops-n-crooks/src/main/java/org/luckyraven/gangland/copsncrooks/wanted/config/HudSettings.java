package org.luckyraven.gangland.copsncrooks.wanted.config;

/**
 * {@code Wanted.Hud} of {@code npc/wanted.yml}: each part of the chase HUD behind its own switch.
 *
 * @since 0.15.0
 */
public record HudSettings(boolean bossBar, boolean starCard, boolean title, boolean siren, String sirenSound,
                          float sirenVolume, float sirenPitch, boolean zoneRing, String zoneParticle, int zonePoints,
                          boolean compass) {
}
