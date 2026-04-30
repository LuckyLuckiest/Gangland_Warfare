package org.luckyraven.gangland.persistence.config.dsl;

import org.luckyraven.gangland.persistence.config.ConfigReport;
import org.luckyraven.gangland.persistence.config.Severity;
import org.luckyraven.gangland.persistence.config.SourceLocation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reference {@link StringDslParser} implementation for bracketed/braced attribute syntax:
 *
 * <pre>
 *   item[custom_model_data=1021,effects={fire=true,duration=5}]
 *   sound[name=ENTITY_ZOMBIE_DEATH,volume=1.0]
 *   bare_leaf
 * </pre>
 *
 * <p>Grammar (informal):
 * <pre>
 *   value     := token ( '[' attrs ']' | '{' attrs '}' )?
 *   attrs     := attr ( ',' attr )*
 *   attr      := KEY '=' value
 *   token     := anything except '[' '{' ',' ']' '}' '='
 * </pre>
 *
 * <p>Reported errors use the {@code dsl.*} code family and carry positions
 * derived from the enclosing scalar's {@link SourceLocation}. The parser recovers at the nearest closing bracket so a
 * single syntax error does not abort parsing of the whole scalar.
 *
 * <p>Limitations (v1): quoted strings (e.g. {@code key="hello, world"}) are
 * not supported — commas inside attribute values terminate the value.
 */
public final class BracketedAttrsParser implements StringDslParser<DslValue> {

	@Override
	public DslValue parse(String raw, SourceLocation scalarLoc, ConfigReport report) {
		if (raw == null) return null;

		Cursor   cursor = new Cursor(raw, scalarLoc, report);
		DslValue result = cursor.parseValue();

		cursor.skipWhitespace();

		if (cursor.index < raw.length()) {
			report.add(Severity.WARNING, cursor.locAt(cursor.index, raw.length() - cursor.index),
			           "", "trailing characters after DSL value: \""
			               + raw.substring(cursor.index) + "\"",
			           "dsl.trailing");
		}

		return result;
	}

	private static final class Cursor {

		private final String         raw;
		private final SourceLocation scalarLoc;
		private final ConfigReport   report;
		private       int            index;

		Cursor(String raw, SourceLocation scalarLoc, ConfigReport report) {
			this.raw       = raw;
			this.scalarLoc = scalarLoc;
			this.report    = report;
			this.index     = 0;
		}

		DslValue parseValue() {
			skipWhitespace();

			int start = index;
			readToken();
			int    tokenEnd = index;
			String token    = raw.substring(start, tokenEnd).trim();

			skipWhitespace();

			if (peek('[')) return parseBracketed(start, token, '[', ']');
			if (peek('{')) return parseBracketed(start, token, '{', '}');

			SourceLocation loc = locAt(start, tokenEnd - start);
			return DslValue.leaf(token, loc);
		}

		private DslValue parseBracketed(int start, String head, char open, char close) {
			advance(1);

			Map<String, DslValue> attrs = parseAttrs(close);

			skipWhitespace();

			if (peek(close)) {
				advance(1);
			} else {
				report.add(Severity.ERROR, locAt(index, 1), "",
				           "expected '" + close + "' to close '" + open + "'", "dsl.unclosed");
			}

			int end = index;

			return new DslValue(
					head.isEmpty() ? null : head,
					Collections.unmodifiableMap(attrs),
					raw.substring(start, end),
					locAt(start, end - start)
			);
		}

		private Map<String, DslValue> parseAttrs(char closer) {
			Map<String, DslValue> attrs = new LinkedHashMap<>();

			skipWhitespace();

			if (peek(closer) || eof()) return attrs;

			while (true) {
				skipWhitespace();

				int keyStart = index;
				readToken();
				String key = raw.substring(keyStart, index).trim();

				if (key.isEmpty()) {
					report.add(Severity.ERROR, locAt(keyStart, 1), "", "empty attribute key", "dsl.syntax");
					skipToCloser(closer);
					return attrs;
				}

				skipWhitespace();

				if (!peek('=')) {
					report.add(Severity.ERROR, locAt(index, 1), "",
					           "expected '=' after attribute '" + key + "'", "dsl.syntax");
					skipToCloser(closer);
					return attrs;
				}

				advance(1);

				DslValue value = parseValue();

				if (attrs.containsKey(key)) {
					report.add(Severity.WARNING, value.at(), "",
					           "duplicate attribute '" + key + "' — later value wins", "dsl.duplicate");
				}

				attrs.put(key, value);

				skipWhitespace();

				if (peek(',')) {
					advance(1);
					continue;
				}

				break;
			}

			return attrs;
		}

		private void readToken() {
			while (index < raw.length()) {
				char c = raw.charAt(index);

				if (c == '[' || c == '{' || c == '}' || c == ']' || c == ',' || c == '=') break;

				index++;
			}
		}

		private void skipWhitespace() {
			while (index < raw.length() && Character.isWhitespace(raw.charAt(index))) index++;
		}

		private void skipToCloser(char closer) {
			int depth = 0;

			while (index < raw.length()) {
				char c = raw.charAt(index);

				if (depth == 0 && (c == closer || c == ',')) return;
				if (c == '[' || c == '{') depth++;
				if (c == ']' || c == '}') depth--;

				index++;
			}
		}

		private boolean peek(char c) {
			return index < raw.length() && raw.charAt(index) == c;
		}

		private boolean eof() {
			return index >= raw.length();
		}

		private void advance(int n) {
			index = Math.min(raw.length(), index + n);
		}

		private SourceLocation locAt(int offset, int length) {
			int line   = scalarLoc.line();
			int column = scalarLoc.column();

			for (int i = 0; i < offset && i < raw.length(); i++) {
				if (raw.charAt(i) == '\n') {
					line++;
					column = 1;
				} else {
					column++;
				}
			}

			int endLine   = line;
			int endColumn = column;

			for (int i = offset; i < offset + length && i < raw.length(); i++) {
				if (raw.charAt(i) == '\n') {
					endLine++;
					endColumn = 1;
				} else {
					endColumn++;
				}
			}

			return new SourceLocation(scalarLoc.file(), line, column, endLine, endColumn);
		}

	}

}
