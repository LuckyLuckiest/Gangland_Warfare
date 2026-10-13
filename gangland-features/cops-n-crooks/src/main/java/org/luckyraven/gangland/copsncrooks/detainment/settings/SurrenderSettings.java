package org.luckyraven.gangland.copsncrooks.detainment.settings;

/**
 * {@code Detainment.Surrender} of {@code copsncrooks/detainment.yml}.
 *
 * @since 0.21.0
 */
public record SurrenderSettings(boolean enabled, int maxStars, double sentenceMultiplier, double fineMultiplier,
                                double bailMultiplier, int resistWindowSeconds) {
}
