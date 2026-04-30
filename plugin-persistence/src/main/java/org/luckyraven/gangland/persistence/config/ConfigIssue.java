package org.luckyraven.gangland.persistence.config;

/**
 * A located parse or validation diagnostic emitted by the config layer.
 *
 * @param severity the importance of the issue.
 * @param at the source location the issue points to.
 * @param path the dotted/bracketed config path (e.g. {@code traders.smiley.Sell_Price_Ratio} or
 *        {@code traders.smiley.Tags[0]}); empty for file-level issues.
 * @param message human-readable description.
 * @param code stable identifier for programmatic filtering ({@code yaml.parse}, {@code config.required},
 *        {@code config.type}, {@code dsl.int}, ...).
 */
public record ConfigIssue(Severity severity, SourceLocation at, String path, String message, String code) {

	/**
	 * Short one-line rendering used by {@link ConfigReport#log}.
	 *
	 * @return {@code file.yml:L:C at path | message [code]}.
	 */
	public String render() {
		String location = at != null ? at.describe() : "<unknown>";
		String suffix   = path == null || path.isEmpty() ? "" : " at " + path;

		return location + suffix + " | " + message + " [" + code + "]";
	}

}
