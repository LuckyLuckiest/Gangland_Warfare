package org.luckyraven.gangland.persistence.config.dsl;

import org.luckyraven.gangland.persistence.config.ConfigIssue;
import org.luckyraven.gangland.persistence.config.ConfigReport;
import org.luckyraven.gangland.persistence.config.SourceLocation;

/**
 * Parses a mini-language embedded inside a YAML scalar, with positions derived from the enclosing scalar's
 * {@link SourceLocation}.
 *
 * <p>Invoked explicitly per field via
 * {@code reader.get("Item").asDsl(itemDsl)} — plain strings are never speculatively re-parsed.
 *
 * @param <T> the parsed value type.
 */
@FunctionalInterface
public interface StringDslParser<T> {

	/**
	 * Parse {@code raw} into a {@link T}.
	 *
	 * <p>Implementations append located {@link ConfigIssue}s
	 * to {@code report} instead of throwing, consistent with the accumulate-not-fail-fast contract of the config layer.
	 * Return {@code null} to signal a hard failure — callers decide how to surface that (default, skip, treat as
	 * error).
	 *
	 * @param raw the scalar value as read from YAML.
	 * @param scalarLoc the source location of the scalar's opening character.
	 * @param report the issue collector.
	 *
	 * @return the parsed value, or {@code null} when unrecoverably invalid.
	 */
	T parse(String raw, SourceLocation scalarLoc, ConfigReport report);

}
