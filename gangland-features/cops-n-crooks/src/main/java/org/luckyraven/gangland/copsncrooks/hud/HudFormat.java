package org.luckyraven.gangland.copsncrooks.hud;

import org.luckyraven.gangland.core.wanted.Wanted;

/**
 * Pure string formatting for the wanted HUD (0.12 F3): the flashing star string, the {@code m:ss} clock and applying
 * a boss-bar/title template's {@code %stars%}/{@code %time%} placeholders.
 */
public final class HudFormat {

	private HudFormat() {
	}

	/**
	 * The star string for a boss bar title. While flashing off, every star (filled or empty) renders grey so the
	 * whole row disappears against the bar for that tick; otherwise the filled stars render red, matching the
	 * default {@code IN_SIGHT}/{@code WANTED} messages.
	 *
	 * @param level the wanted level
	 * @param max the wanted maximum level
	 * @param flashOff true on a flash-off tick (SEARCHING alternates this every second)
	 *
	 * @return the color-coded star string ({@code Wanted.buildStars} prefixed with a color code)
	 */
	public static String stars(int level, int max, boolean flashOff) {
		String plain = Wanted.buildStars(level, max);
		return (flashOff ? "&7" : "&c") + plain;
	}

	/**
	 * Formats a duration as {@code m:ss}, floored to the whole second. Negative input floors to {@code 0:00}.
	 *
	 * @param seconds the duration in seconds
	 *
	 * @return the {@code m:ss} string
	 */
	public static String time(double seconds) {
		int total   = (int) Math.max(0D, Math.floor(seconds));
		int minutes = total / 60;
		int secs    = total % 60;

		return minutes + ":" + (secs < 10 ? "0" + secs : String.valueOf(secs));
	}

	/**
	 * Replaces {@code %stars%} and {@code %time%} in a message template. Missing placeholders are a no-op.
	 *
	 * @param template the message template, e.g. {@code HudConfig.getSearchingMessage()}
	 * @param stars the star string, usually from {@link #stars(int, int, boolean)}
	 * @param time the time string, usually from {@link #time(double)}
	 *
	 * @return the template with placeholders replaced
	 */
	public static String apply(String template, String stars, String time) {
		return template.replace("%stars%", stars).replace("%time%", time);
	}
}
