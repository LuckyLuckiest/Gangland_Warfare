package org.luckyraven.gangland.copsncrooks.detainment.settings;

/**
 * {@code Detainment.Rescue} of {@code copsncrooks/detainment.yml}.
 *
 * @since 0.21.0
 */
public record RescueSettings(boolean enabled, int holdSeconds, int stars) {
}
