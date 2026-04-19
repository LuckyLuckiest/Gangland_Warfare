package me.luckyraven.persistence.config;

import org.apache.logging.log4j.Logger;

import java.util.*;

/**
 * Mutable collector for {@link ConfigIssue}s surfaced while parsing, validating, or reading a single config file.
 *
 * <p>Accumulates issues instead of failing fast — a file with five problems
 * produces five diagnostics, not one. Callers drain the report at load-time via {@link #log(Logger)} to emit a per-file
 * block.
 */
public final class ConfigReport {

	private static final Set<String> GLOBALLY_EXEMPT_KEYS = Set.of("Config_Version");

	private final List<ConfigIssue> issues            = new ArrayList<>();
	private final List<NodeReader>  registeredReaders = new ArrayList<>();
	private       boolean           unknownKeysSwept  = false;

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
	 * @return an unmodifiable snapshot of the issues recorded so far. Runs the unknown-key sweep on first call so tests
	 * 		inspecting {@code issues()} without calling {@link #log} still see unknown-key warnings.
	 */
	public List<ConfigIssue> issues() {
		sweepUnknownKeysOnce();
		return Collections.unmodifiableList(issues);
	}

	/**
	 * @return {@code true} when at least one {@link Severity#ERROR} issue is present.
	 */
	public boolean hasErrors() {
		sweepUnknownKeysOnce();

		for (ConfigIssue issue : issues) {
			if (issue.severity() == Severity.ERROR) return true;
		}

		return false;
	}

	/**
	 * @return {@code true} when the report has no issues at all.
	 */
	public boolean isEmpty() {
		sweepUnknownKeysOnce();
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
		sweepUnknownKeysOnce();

		for (ConfigIssue issue : issues) {
			String line = issue.render();

			switch (issue.severity()) {
				case ERROR -> logger.error(line);
				case WARNING -> logger.warn(line);
				case INFO -> logger.info(line);
			}
		}
	}

	/**
	 * Register a {@link NodeReader} for the unknown-key sweep. Called automatically from {@link NodeReader#of}; no
	 * production code should call this directly.
	 *
	 * @param reader the reader to include in the sweep.
	 */
	void registerReader(NodeReader reader) {
		registeredReaders.add(reader);
	}

	private void sweepUnknownKeysOnce() {
		if (unknownKeysSwept) return;
		unknownKeysSwept = true;

		// Group readers by mapping identity — multiple readers can wrap the same mapping when the YAML is consumed from
		// several sites (e.g. WeaponAddon reads Reload.Sound/Action_Bar while AmmunitionSectionParser reads
		// Reload.Cooldown/Type). Their touched sets must union; otherwise each side's unread-set would falsely flag the
		// other side's legitimate reads.
		Map<MappingNode, Set<String>> touchedByMapping = new IdentityHashMap<>();

		for (NodeReader reader : registeredReaders) {
			touchedByMapping.computeIfAbsent(reader.mapping(), k -> new HashSet<>())
			                .addAll(reader.touchedKeys());
		}

		for (Map.Entry<MappingNode, Set<String>> entry : touchedByMapping.entrySet()) {
			MappingNode mapping = entry.getKey();
			Set<String> touched = entry.getValue();
			Set<String> actual  = mapping.entries().keySet();

			for (String key : actual) {
				if (touched.contains(key)) continue;
				if (GLOBALLY_EXEMPT_KEYS.contains(key)) continue;

				String suggestion = SpellCheckerSuggest.best(key, touched, 2);

				String message = suggestion == null
				                 ? "unknown key '" + key + "'"
				                 : "unknown key '" + key + "' (did you mean '" + suggestion + "'?)";

				String path = mapping.path() == null || mapping.path().isEmpty()
				              ? key
				              : mapping.path() + "." + key;

				issues.add(new ConfigIssue(Severity.WARNING, mapping.location(), path, message,
				                           "config.unknown_key"));
			}
		}
	}

}
