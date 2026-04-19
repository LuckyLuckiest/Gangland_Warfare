package me.luckyraven.persistence.config;

import org.yaml.snakeyaml.error.Mark;

import java.nio.file.Path;

/**
 * 1-indexed source range inside a config file.
 *
 * <p>Line and column numbers are 1-based to match conventional editor reporting
 * ({@code file.yml:12:5}). SnakeYAML {@link Mark} coordinates are 0-based, so {@link #of(Path, Mark, Mark)} shifts them
 * on construction.
 */
public record SourceLocation(Path file, int line, int column, int endLine, int endColumn) {

	/**
	 * Build a {@code SourceLocation} from a SnakeYAML start/end mark pair.
	 *
	 * @param file source file the marks originated from; may be {@code null} for synthetic streams.
	 * @param start inclusive start mark (SnakeYAML 0-indexed).
	 * @param end exclusive end mark (SnakeYAML 0-indexed); falls back to {@code start} when absent.
	 *
	 * @return a 1-indexed location covering the mark range.
	 */
	public static SourceLocation of(Path file, Mark start, Mark end) {
		Mark effectiveEnd = end != null ? end : start;

		return new SourceLocation(
				file,
				start.getLine() + 1,
				start.getColumn() + 1,
				effectiveEnd.getLine() + 1,
				effectiveEnd.getColumn() + 1
		);
	}

	/**
	 * Offsets this location by an advance within a scalar string.
	 *
	 * <p>Used by {@link me.luckyraven.persistence.config.dsl.StringDslParser}
	 * implementations to project sub-token positions relative to the enclosing scalar. {@code rawAdvance} is the
	 * substring preceding the sub-token; any {@code \n} advances the line and resets the column.
	 *
	 * @param rawAdvance substring preceding the sub-token inside the scalar's raw value.
	 * @param length number of characters consumed by the sub-token itself.
	 *
	 * @return a new location with {@code endLine}/{@code endColumn} computed from {@code length}.
	 */
	public SourceLocation shift(String rawAdvance, int length) {
		int line   = this.line;
		int column = this.column;

		for (int i = 0; i < rawAdvance.length(); i++) {
			char c = rawAdvance.charAt(i);

			if (c == '\n') {
				line++;
				column = 1;
			} else {
				column++;
			}
		}

		int endLine   = line;
		int endColumn = column + length;

		return new SourceLocation(file, line, column, endLine, endColumn);
	}

	/**
	 * Short human-friendly descriptor: {@code file.yml:12:5}. Used in log lines.
	 *
	 * @return the formatted descriptor, or {@code <unknown>:L:C} when the file is {@code null}.
	 */
	public String describe() {
		String fileName = file != null ? file.getFileName().toString() : "<unknown>";

		return fileName + ":" + line + ":" + column;
	}

}
