package org.luckyraven.gangland.command.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.keystone.economy.Currency;

import java.math.BigDecimal;

/**
 * Result of parsing a money amount typed into a chat command.
 *
 * <p>{@link Currency#parse(String)} only rejects text that is not a number — it happily returns
 * {@code -500}. Every command that moved money used to feed that straight into the balance arithmetic, so
 * a negative deposit credited cash, a negative withdrawal credited the bank, and a negative bounty paid the
 * sender. This type is the single place where both failures are decided, so the sign check cannot be
 * forgotten at a new call site.
 *
 * <p>Callers map {@link Failure#NOT_A_NUMBER} to {@code Messages.MUST_BE_NUMBERS} and
 * {@link Failure#NOT_POSITIVE} to {@code Messages.CANNOT_TAKE_LESS_THAN_ZERO}.
 */
public record ParsedAmount(@Nullable BigDecimal value, @Nullable Failure failure) {

	/**
	 * Why a raw amount was rejected.
	 */
	public enum Failure {
		/** The text is not a number at all. */
		NOT_A_NUMBER,
		/** The text parsed, but is zero or negative. */
		NOT_POSITIVE
	}

	/**
	 * Parses a raw command argument into a strictly positive money amount.
	 *
	 * @param raw the argument as typed by the player; {@code null} and blank are rejected as non-numbers
	 *
	 * @return a valid result carrying the amount, or a failed one carrying the reason
	 */
	@NotNull
	public static ParsedAmount of(@Nullable String raw) {
		if (raw == null || raw.isBlank()) {
			return new ParsedAmount(null, Failure.NOT_A_NUMBER);
		}

		BigDecimal parsed;

		try {
			parsed = Currency.parse(raw);
		} catch (NumberFormatException | ArithmeticException exception) {
			return new ParsedAmount(null, Failure.NOT_A_NUMBER);
		}

		if (parsed == null) {
			return new ParsedAmount(null, Failure.NOT_A_NUMBER);
		}

		if (parsed.signum() <= 0) {
			return new ParsedAmount(null, Failure.NOT_POSITIVE);
		}

		return new ParsedAmount(parsed, null);
	}

	/**
	 * @return {@code true} when the amount parsed and is strictly positive
	 */
	public boolean isValid() {
		return failure == null && value != null;
	}

	/**
	 * Builds the localized error to send back for a failed parse.
	 *
	 * @param raw the argument as typed, substituted into the "must be numbers" message
	 *
	 * @return the message to send
	 *
	 * @throws IllegalStateException when this result is valid — nothing to report
	 */
	@NotNull
	public String failureMessage(@Nullable String raw) {
		if (failure == null) {
			throw new IllegalStateException("No failure to report");
		}

		if (failure == Failure.NOT_POSITIVE) {
			return Messages.CANNOT_TAKE_LESS_THAN_ZERO.toString();
		}

		return Messages.MUST_BE_NUMBERS.toString().replace("%command%", raw == null ? "" : raw);
	}

	/**
	 * @return the parsed amount
	 *
	 * @throws IllegalStateException when this result is a failure — check {@link #isValid()} first
	 */
	@NotNull
	public BigDecimal require() {
		if (!isValid()) {
			throw new IllegalStateException("No amount parsed: " + failure);
		}

		return value;
	}

}
