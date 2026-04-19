package me.luckyraven.persistence.config;

import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Mutable collector for {@link ConfigIssue}s surfaced while parsing, validating, or reading a single config file.
 *
 * <p>Accumulates issues instead of failing fast — a file with five problems
 * produces five diagnostics, not one. Callers drain the report at load-time via {@link #log(Logger)} to emit a per-file
 * block.
 */
public final class ConfigReport {

	private final List<ConfigIssue> issues = new ArrayList<>();

	/**
	 * Append a pre-built issue.
	 *
	 * @param issue the issue to record.
	 */
	public void add(ConfigIssue issue) {
		issues.add(issue);
	}

	/**
	 * Convenience constructor — build and record an issue in one call.
	 *
	 * @param severity the severity.
	 * @param at source location of the offending token.
	 * @param path dotted/bracketed config path (may be empty for file-level issues).
	 * @param message human-readable description.
	 * @param code stable diagnostic code (e.g. {@code config.required}).
	 */
	public void add(Severity severity, SourceLocation at, String path, String message, String code) {
		issues.add(new ConfigIssue(severity, at, path, message, code));
	}

	/**
	 * @return an unmodifiable snapshot of the issues recorded so far.
	 */
	public List<ConfigIssue> issues() {
		return Collections.unmodifiableList(issues);
	}

	/**
	 * @return {@code true} when at least one {@link Severity#ERROR} issue is present.
	 */
	public boolean hasErrors() {
		for (ConfigIssue issue : issues) {
			if (issue.severity() == Severity.ERROR) return true;
		}

		return false;
	}

	/**
	 * @return {@code true} when the report has no issues at all.
	 */
	public boolean isEmpty() {
		return issues.isEmpty();
	}

	/**
	 * Emit every accumulated issue to {@code logger}, routed to the matching log level.
	 *
	 * <p>Each issue is emitted as its own log line so log aggregators keep them
	 * independently searchable; admins read them as a grouped block because they are emitted back-to-back during file
	 * load.
	 *
	 * @param logger the target logger (typically the FileHandler's logger).
	 */
	public void log(Logger logger) {
		for (ConfigIssue issue : issues) {
			String line = issue.render();

			switch (issue.severity()) {
				case ERROR -> logger.error(line);
				case WARNING -> logger.warn(line);
				case INFO -> logger.info(line);
			}
		}
	}

}
