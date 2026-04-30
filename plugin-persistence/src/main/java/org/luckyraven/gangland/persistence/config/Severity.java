package org.luckyraven.gangland.persistence.config;

/**
 * Severity of a {@link ConfigIssue}.
 *
 * <p>{@link #ERROR} indicates the config is unusable in some way (missing required
 * key, malformed YAML, type mismatch on a required field). {@link #WARNING} covers soft issues (deprecated key,
 * out-of-range value coerced to default). {@link #INFO} is used for informational notes such as auto-migrations.
 */
public enum Severity {

	ERROR,
	WARNING,
	INFO

}
