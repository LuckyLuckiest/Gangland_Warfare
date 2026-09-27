package org.luckyraven.gangland.copsncrooks.evasion;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * What the evasion clock takes when it runs out ({@code Wanted.Evasion.Drop_Mode}).
 */
public enum EvasionDropMode {

	/**
	 * Drop one star, then restart the search at the new level.
	 */
	ONE_STAR,

	/**
	 * Drop every star at once, ending the chase.
	 */
	ALL_STARS;

	/**
	 * Parses a configured drop mode, case-insensitively.
	 *
	 * @param raw the configured value
	 *
	 * @return the matching mode, or {@link #ONE_STAR} for a missing or unknown value
	 */
	public static EvasionDropMode parse(@Nullable String raw) {
		if (raw == null) return ONE_STAR;

		String normalized = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
		for (EvasionDropMode mode : values()) {
			if (mode.name().equals(normalized)) return mode;
		}
		return ONE_STAR;
	}
}
