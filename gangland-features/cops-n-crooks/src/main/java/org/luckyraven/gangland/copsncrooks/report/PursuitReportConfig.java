package org.luckyraven.gangland.copsncrooks.report;

import lombok.Getter;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.keystone.persistence.config.ConfigReport;
import org.luckyraven.keystone.persistence.config.MappingNode;
import org.luckyraven.keystone.persistence.config.NodeReader;

import java.util.Collections;
import java.util.List;

/**
 * Immutable view of the {@code Pursuit_Report:} section of {@code npc/cops.yml} (0.12 F6): the per-chase report sent
 * to the player when a chase ends, and the server-wide "breaking news" broadcast for a chase that ran long or hot.
 *
 * <p>Every key is optional: a missing section yields {@link #defaults()}, a missing key its default below, so a
 * server running an older {@code cops.yml} keeps loading.
 * <ul>
 *     <li>{@code Enable} ({@code true}) — the whole feature no-ops when {@code false}: no report, no broadcast.</li>
 *     <li>{@code Breaking_News.Enable} ({@code true}) — the server-wide line for a long or high-star chase.</li>
 *     <li>{@code Breaking_News.Min_Stars} ({@code 4}) — broadcasts when the chase's top star reaches this level.</li>
 *     <li>{@code Breaking_News.Min_Duration_Seconds} ({@code 120}) — or when the chase ran at least this long.</li>
 *     <li>{@code Breaking_News.Cooldown_Seconds} ({@code 300}) — global cooldown between broadcasts.</li>
 *     <li>{@code Messages.Report} — the report's lines, sent in order. Placeholders: {@code %outcome%},
 *     {@code %duration%} ({@code m:ss}), {@code %stars%}, {@code %cops%}.</li>
 *     <li>{@code Messages.Outcome_Escaped}/{@code Outcome_Busted}/{@code Outcome_Wasted} — the colored
 *     {@code %outcome%} value for each {@link ChaseOutcome}.</li>
 *     <li>{@code Messages.Breaking_News} — the broadcast line. Placeholders: {@code %player%},
 *     {@code %outcome_plain%} (a plain, uncolored verb phrase), {@code %duration%}, {@code %stars%},
 *     {@code %cops%}.</li>
 * </ul>
 */
@Getter
public final class PursuitReportConfig {

	public static final List<String> DEFAULT_REPORT = List.of(
			"&8&m----------&r &c&lPURSUIT REPORT &8&m----------",
			"&7Outcome: %outcome%",
			"&7Duration: &f%duration%  &7Top stars: %stars%",
			"&7Cops down: &f%cops%");

	public static final String DEFAULT_OUTCOME_ESCAPED = "&aESCAPED";
	public static final String DEFAULT_OUTCOME_BUSTED  = "&9BUSTED";
	public static final String DEFAULT_OUTCOME_WASTED  = "&4WASTED";
	public static final String DEFAULT_BREAKING_NEWS   = "&c&lBREAKING NEWS &7» &f%player% %outcome_plain% after a "
			+ "%duration% chase at %stars%&f, %cops% officers down.";

	public static final int DEFAULT_MIN_STARS            = 4;
	public static final int DEFAULT_MIN_DURATION_SECONDS = 120;
	public static final int DEFAULT_COOLDOWN_SECONDS     = 300;

	private final boolean      enabled;
	private final boolean      breakingNewsEnabled;
	private final int          breakingNewsMinStars;
	private final int          breakingNewsMinDurationSeconds;
	private final int          breakingNewsCooldownSeconds;
	private final List<String> reportMessages;
	private final String       outcomeEscapedMessage;
	private final String       outcomeBustedMessage;
	private final String       outcomeWastedMessage;
	private final String       breakingNewsMessage;

	public PursuitReportConfig(boolean enabled, boolean breakingNewsEnabled, int breakingNewsMinStars,
	                           int breakingNewsMinDurationSeconds, int breakingNewsCooldownSeconds,
	                           @Nullable List<String> reportMessages, @Nullable String outcomeEscapedMessage,
	                           @Nullable String outcomeBustedMessage, @Nullable String outcomeWastedMessage,
	                           @Nullable String breakingNewsMessage) {
		this.enabled                        = enabled;
		this.breakingNewsEnabled            = breakingNewsEnabled;
		this.breakingNewsMinStars           = Math.max(0, breakingNewsMinStars);
		this.breakingNewsMinDurationSeconds = Math.max(0, breakingNewsMinDurationSeconds);
		this.breakingNewsCooldownSeconds    = Math.max(0, breakingNewsCooldownSeconds);
		this.reportMessages                 = sanitizeReport(reportMessages);
		this.outcomeEscapedMessage          = orDefault(outcomeEscapedMessage, DEFAULT_OUTCOME_ESCAPED);
		this.outcomeBustedMessage           = orDefault(outcomeBustedMessage, DEFAULT_OUTCOME_BUSTED);
		this.outcomeWastedMessage           = orDefault(outcomeWastedMessage, DEFAULT_OUTCOME_WASTED);
		this.breakingNewsMessage            = orDefault(breakingNewsMessage, DEFAULT_BREAKING_NEWS);
	}

	/**
	 * @return the shipped defaults, used when {@code cops.yml} has no {@code Pursuit_Report:} section
	 */
	public static PursuitReportConfig defaults() {
		return new PursuitReportConfig(true, true, DEFAULT_MIN_STARS, DEFAULT_MIN_DURATION_SECONDS,
		                               DEFAULT_COOLDOWN_SECONDS, DEFAULT_REPORT, DEFAULT_OUTCOME_ESCAPED,
		                               DEFAULT_OUTCOME_BUSTED, DEFAULT_OUTCOME_WASTED, DEFAULT_BREAKING_NEWS);
	}

	/**
	 * Parses the {@code Pursuit_Report:} section of the {@code cops.yml} root.
	 *
	 * @param root positional reader over the {@code cops.yml} root mapping
	 * @param report issue collector drained by the enclosing loader
	 *
	 * @return the parsed config; {@link #defaults()} when the section is absent
	 */
	public static PursuitReportConfig parse(@Nullable NodeReader root, ConfigReport report) {
		if (root == null) return defaults();

		MappingNode section = root.get("Pursuit_Report").asMapping().orNull();
		if (section == null) return defaults();

		NodeReader pursuit = NodeReader.of(section, report);

		boolean enabled = pursuit.get("Enable").asBool().orDefault(true);

		boolean breakingEnabled = true;
		int     minStars        = DEFAULT_MIN_STARS;
		int     minDuration     = DEFAULT_MIN_DURATION_SECONDS;
		int     cooldown        = DEFAULT_COOLDOWN_SECONDS;

		MappingNode breaking = pursuit.get("Breaking_News").asMapping().orNull();
		if (breaking != null) {
			NodeReader breakingReader = NodeReader.of(breaking, report);

			breakingEnabled = breakingReader.get("Enable").asBool().orDefault(true);
			minStars        = breakingReader.get("Min_Stars").asInt().min(0).orDefault(DEFAULT_MIN_STARS);
			minDuration     = breakingReader.get("Min_Duration_Seconds").asInt().min(0)
			                                .orDefault(DEFAULT_MIN_DURATION_SECONDS);
			cooldown        = breakingReader.get("Cooldown_Seconds").asInt().min(0)
			                                .orDefault(DEFAULT_COOLDOWN_SECONDS);
		}

		List<String> reportLines    = DEFAULT_REPORT;
		String       outcomeEscaped = DEFAULT_OUTCOME_ESCAPED;
		String       outcomeBusted  = DEFAULT_OUTCOME_BUSTED;
		String       outcomeWasted  = DEFAULT_OUTCOME_WASTED;
		String       breakingNews   = DEFAULT_BREAKING_NEWS;

		MappingNode messages = pursuit.get("Messages").asMapping().orNull();
		if (messages != null) {
			NodeReader messageReader = NodeReader.of(messages, report);

			List<String> parsedReport = messageReader.get("Report").asList().ofStrings().orEmpty();
			if (!parsedReport.isEmpty()) reportLines = parsedReport;

			outcomeEscaped = messageReader.get("Outcome_Escaped").asString().orDefault(DEFAULT_OUTCOME_ESCAPED);
			outcomeBusted  = messageReader.get("Outcome_Busted").asString().orDefault(DEFAULT_OUTCOME_BUSTED);
			outcomeWasted  = messageReader.get("Outcome_Wasted").asString().orDefault(DEFAULT_OUTCOME_WASTED);
			breakingNews   = messageReader.get("Breaking_News").asString().orDefault(DEFAULT_BREAKING_NEWS);
		}

		return new PursuitReportConfig(enabled, breakingEnabled, minStars, minDuration, cooldown, reportLines,
		                               outcomeEscaped, outcomeBusted, outcomeWasted, breakingNews);
	}

	/**
	 * @param outcome the resolved chase outcome
	 *
	 * @return the colored {@code %outcome%} value for {@code outcome} ({@code Messages.Outcome_*})
	 */
	public String outcomeMessage(ChaseOutcome outcome) {
		return switch (outcome) {
			case ESCAPED -> outcomeEscapedMessage;
			case BUSTED  -> outcomeBustedMessage;
			case WASTED  -> outcomeWastedMessage;
		};
	}

	private static List<String> sanitizeReport(@Nullable List<String> lines) {
		if (lines == null || lines.isEmpty()) return DEFAULT_REPORT;
		return Collections.unmodifiableList(lines);
	}

	private static String orDefault(@Nullable String value, String fallback) {
		return value == null || value.isEmpty() ? fallback : value;
	}

}
