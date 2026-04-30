package org.luckyraven.gangland.economy;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Canonical currency representation for the plugin. All gangland money values are {@link BigDecimal}s scaled to
 * {@value #SCALE} decimal places using {@link RoundingMode#HALF_UP}, matching real-world two-cent accounting while
 * avoiding the precision ceiling of {@code double}. Use the static factories to normalise raw input from YAML, command
 * args, or legacy {@code double} call sites, and use the formatting helper when rendering to players.
 */
public final class Currency {

	public static final int          SCALE         = 2;
	public static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;
	public static final BigDecimal   ZERO          = BigDecimal.ZERO.setScale(SCALE, ROUNDING_MODE);

	private Currency() {
	}

	/**
	 * Normalises a raw {@link BigDecimal} to the canonical scale. Safe to call on values that already have the correct
	 * scale — no precision is lost.
	 */
	public static BigDecimal of(BigDecimal raw) {
		if (raw == null) return ZERO;
		return raw.setScale(SCALE, ROUNDING_MODE);
	}

	/**
	 * Adapter for legacy {@code double} call-sites. Uses {@link BigDecimal#valueOf(double)} so the IEEE 754
	 * representation is converted via its canonical {@code toString} and avoids the raw constructor's unpredictable
	 * scale.
	 */
	public static BigDecimal of(double value) {
		return BigDecimal.valueOf(value).setScale(SCALE, ROUNDING_MODE);
	}

	public static BigDecimal of(long value) {
		return BigDecimal.valueOf(value).setScale(SCALE, ROUNDING_MODE);
	}

	/**
	 * Parses a canonical string representation (plain decimal, no exponential form) into normalised currency. Throws
	 * {@link NumberFormatException} on malformed input — callers catch this the same way they would for
	 * {@link Double#parseDouble(String)}.
	 */
	public static BigDecimal parse(String raw) {
		if (raw == null || raw.isBlank()) return ZERO;
		// YAML literals like 100_000 reach us with underscores intact (string-typed nodes preserve the raw token).
		// Strip them so admins can use Java-style thousand separators alongside plain decimals in bank_tiers.yml
		// and settings.yml.
		String cleaned = raw.trim().replace("_", "");
		return new BigDecimal(cleaned).setScale(SCALE, ROUNDING_MODE);
	}

	/**
	 * Parses a value from a YAML literal where the loader may hand us a {@link Number} (for small ints / doubles) or a
	 * {@link String} (for arbitrary-precision values). Falls back to {@link #ZERO} on {@code null}.
	 */
	public static BigDecimal ofYaml(Object raw) {
		return switch (raw) {
			case null -> ZERO;
			case BigDecimal bd -> of(bd);
			case Number n -> of(n.doubleValue());
			default -> parse(String.valueOf(raw));
		};
	}

	/**
	 * Round-trip String form used by DB storage. Guaranteed lossless: {@code parse(plainString(x)).equals(x)} holds for
	 * any normalised {@code x}.
	 */
	public static String plainString(BigDecimal value) {
		if (value == null) return ZERO.toPlainString();
		return of(value).toPlainString();
	}

	/**
	 * {@code double} projection for interop with APIs that can't accept {@link BigDecimal} (Vault, exp4j formula
	 * engine, legacy placeholders). Beyond 2^53 this is lossy — callers that matter clamp separately.
	 */
	public static double toDouble(BigDecimal value) {
		if (value == null) return 0D;
		return value.doubleValue();
	}

	public static BigDecimal multiply(BigDecimal base, double factor) {
		if (base == null) return ZERO;
		return base.multiply(BigDecimal.valueOf(factor)).setScale(SCALE, ROUNDING_MODE);
	}

}
