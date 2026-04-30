package org.luckyraven.gangland.persistence.config;

import org.luckyraven.gangland.persistence.config.dsl.StringDslParser;

import java.util.*;

/**
 * Fluent, positional accessor over a {@link MappingNode}.
 *
 * <p>Each {@code get(key)} chain is a small pipeline: select a child, assert
 * its type, run validators, fall back to a default. Every terminal call appends located {@link ConfigIssue}s to a
 * shared {@link ConfigReport} instead of throwing, so a single loader pass accumulates all diagnostics.
 *
 * <pre>{@code
 * NodeReader r = NodeReader.of(section, report);
 * String name  = r.get("Display_Name").asString().required().orElse(id);
 * double ratio = r.get("Sell_Price_Ratio").asDouble().min(0).required().orElse(1.0);
 * List<String> tags = r.get("Tags").asList().ofStrings().orEmpty();
 * }</pre>
 */
public final class NodeReader {

	private final MappingNode  mapping;
	private final ConfigReport report;
	private final Set<String>  touchedKeys = new HashSet<>();

	private NodeReader(MappingNode mapping, ConfigReport report) {
		this.mapping = mapping;
		this.report  = report;
	}

	/**
	 * @param mapping the mapping to read from.
	 * @param report issue collector.
	 *
	 * @return a reader bound to {@code mapping}, self-registered with {@code report} for the unknown-key sweep.
	 */
	public static NodeReader of(MappingNode mapping, ConfigReport report) {
		NodeReader reader = new NodeReader(mapping, report);
		report.registerReader(reader);
		return reader;
	}

	private static String joinPath(String parent, String key) {
		if (parent == null || parent.isEmpty()) return key;

		return parent + "." + key;
	}

	/**
	 * Strips digit-grouping underscores from a numeric YAML scalar so {@code "10_000_000"} parses the same way Bukkit's
	 * {@code YamlConfiguration.getInt/getDouble} used to accept it. Leaves leading signs and exponents intact; never
	 * merges non-digit characters across underscores (so a typo like {@code 1_a0} still fails).
	 */
	private static String normalizeNumeric(String raw) {
		String trimmed = raw.trim();

		if (trimmed.indexOf('_') < 0) return trimmed;

		StringBuilder builder = new StringBuilder(trimmed.length());

		for (int i = 0; i < trimmed.length(); i++) {
			char c = trimmed.charAt(i);

			if (c == '_' && i > 0 && i < trimmed.length() - 1 && Character.isDigit(trimmed.charAt(i - 1)) &&
			    Character.isDigit(trimmed.charAt(i + 1))) {
				continue;
			}

			builder.append(c);
		}

		return builder.toString();
	}

	private static void recordIfMissing(State state, SourceLocation loc, String path, String typeName,
	                                    ConfigReport report) {
		if (state == State.MISSING) {
			report.add(Severity.ERROR, loc, path, "required " + typeName + " missing", "config.required");
		}
	}

	/**
	 * @return the backing mapping.
	 */
	public MappingNode mapping() {
		return mapping;
	}

	/**
	 * @return the bound report.
	 */
	public ConfigReport report() {
		return report;
	}

	/**
	 * @param key mapping key.
	 *
	 * @return {@code true} when the key is present and not an explicit {@link NullNode}.
	 */
	public boolean has(String key) {
		ConfigNode child = mapping.get(key);

		return child != null && !(child instanceof NullNode);
	}

	/**
	 * @return every defined key in iteration order. Calling this implies the loader intends to consume every key in the
	 * 		mapping, so the unknown-key sweep treats them all as touched.
	 */
	public Iterable<String> keys() {
		Set<String> keySet = mapping.entries().keySet();
		touchedKeys.addAll(keySet);
		return keySet;
	}

	/**
	 * Select a child entry. The returned access object holds enough context (path, location, report) to produce
	 * positional errors even when the value is missing.
	 *
	 * @param key mapping key.
	 *
	 * @return a chainable accessor.
	 */
	public NodeAccess get(String key) {
		touchedKeys.add(key);

		String     path  = joinPath(mapping.path(), key);
		ConfigNode child = mapping.get(key);

		SourceLocation locationForMissing = mapping.location();

		return new NodeAccess(child, path, locationForMissing, report);
	}

	Set<String> touchedKeys() {
		return touchedKeys;
	}

	/**
	 * State of a typed access after coercion.
	 */
	private enum State {
		MISSING,
		INVALID,
		VALID
	}

	// ------------------------------------------------------------ typed accesses

	/**
	 * Untyped accessor returned by {@link NodeReader#get(String)}. Calls to {@code as*()} produce a typed access bound
	 * to the same location/path.
	 */
	public static final class NodeAccess {

		private final ConfigNode     node;
		private final String         path;
		private final SourceLocation locForMissing;
		private final ConfigReport   report;

		NodeAccess(ConfigNode node, String path, SourceLocation locForMissing, ConfigReport report) {
			this.node          = node;
			this.path          = path;
			this.locForMissing = locForMissing;
			this.report        = report;
		}

		private static String kindOf(ConfigNode node) {
			return switch (node) {
				case ScalarNode ignored -> "scalar";
				case MappingNode ignored -> "mapping";
				case SequenceNode ignored -> "list";
				case NullNode ignored -> "null";
			};
		}

		/**
		 * @return the raw node, or {@code null} when absent.
		 */
		public ConfigNode node() {
			return node;
		}

		/**
		 * @return {@code true} when a non-null value is present.
		 */
		public boolean exists() {
			return node != null && !(node instanceof NullNode);
		}

		/**
		 * Coerce to a string. Any scalar value is accepted verbatim.
		 *
		 * @return a string access.
		 */
		public StringAccess asString() {
			if (node == null || node instanceof NullNode) {
				return new StringAccess(null, State.MISSING, path, loc(), report);
			}

			if (node instanceof ScalarNode scalar) {
				return new StringAccess(scalar.value(), State.VALID, path, scalar.location(), report);
			}

			report.add(Severity.ERROR, loc(), path, "expected string, got " + kindOf(node), "config.type");
			return new StringAccess(null, State.INVALID, path, loc(), report);
		}

		/**
		 * Coerce to a 32-bit integer.
		 *
		 * @return an int access.
		 */
		public IntAccess asInt() {
			if (node == null || node instanceof NullNode) {
				return new IntAccess(0, State.MISSING, path, loc(), report);
			}

			if (node instanceof ScalarNode scalar) {
				try {
					return new IntAccess(Integer.parseInt(normalizeNumeric(scalar.value())), State.VALID, path,
					                     scalar.location(), report);
				} catch (NumberFormatException e) {
					report.add(Severity.ERROR, scalar.location(), path, "expected int, got \"" + scalar.value() + "\"",
					           "config.int");
					return new IntAccess(0, State.INVALID, path, scalar.location(), report);
				}
			}

			report.add(Severity.ERROR, loc(), path, "expected int, got " + kindOf(node), "config.type");
			return new IntAccess(0, State.INVALID, path, loc(), report);
		}

		/**
		 * Coerce to a double-precision float.
		 *
		 * @return a double access.
		 */
		public DoubleAccess asDouble() {
			if (node == null || node instanceof NullNode) {
				return new DoubleAccess(0.0, State.MISSING, path, loc(), report);
			}

			if (node instanceof ScalarNode scalar) {
				try {
					return new DoubleAccess(Double.parseDouble(normalizeNumeric(scalar.value())), State.VALID, path,
					                        scalar.location(), report);
				} catch (NumberFormatException e) {
					report.add(Severity.ERROR, scalar.location(), path,
					           "expected number, got \"" + scalar.value() + "\"", "config.double");
					return new DoubleAccess(0.0, State.INVALID, path, scalar.location(), report);
				}
			}

			report.add(Severity.ERROR, loc(), path, "expected number, got " + kindOf(node), "config.type");
			return new DoubleAccess(0.0, State.INVALID, path, loc(), report);
		}

		/**
		 * Coerce to a boolean. Accepts {@code true / false / yes / no / on / off} (case-insensitive), matching Bukkit's
		 * {@code YamlConfiguration} behavior.
		 *
		 * @return a bool access.
		 */
		public BoolAccess asBool() {
			if (node == null || node instanceof NullNode) {
				return new BoolAccess(false, State.MISSING, path, loc(), report);
			}

			if (node instanceof ScalarNode scalar) {
				String v = scalar.value().trim().toLowerCase(Locale.ROOT);

				if (v.equals("true") || v.equals("yes") || v.equals("on")) {
					return new BoolAccess(true, State.VALID, path, scalar.location(), report);
				}
				if (v.equals("false") || v.equals("no") || v.equals("off")) {
					return new BoolAccess(false, State.VALID, path, scalar.location(), report);
				}

				report.add(Severity.ERROR, scalar.location(), path, "expected boolean, got \"" + scalar.value() + "\"",
				           "config.bool");
				return new BoolAccess(false, State.INVALID, path, scalar.location(), report);
			}

			report.add(Severity.ERROR, loc(), path, "expected boolean, got " + kindOf(node), "config.type");
			return new BoolAccess(false, State.INVALID, path, loc(), report);
		}

		/**
		 * Descend into a sub-mapping.
		 *
		 * @return a mapping access.
		 */
		public MappingAccess asMapping() {
			if (node == null || node instanceof NullNode) {
				return new MappingAccess(null, State.MISSING, path, loc(), report);
			}

			if (node instanceof MappingNode mapping) {
				return new MappingAccess(mapping, State.VALID, path, mapping.location(), report);
			}

			report.add(Severity.ERROR, loc(), path, "expected mapping, got " + kindOf(node), "config.type");
			return new MappingAccess(null, State.INVALID, path, loc(), report);
		}

		/**
		 * Begin a sequence chain. Subsequent calls like {@link ListAccess#ofStrings()} specify the element type.
		 *
		 * @return a sequence access.
		 */
		public ListAccess asList() {
			if (node == null || node instanceof NullNode) {
				return new ListAccess(null, State.MISSING, path, loc(), report);
			}

			if (node instanceof SequenceNode seq) {
				return new ListAccess(seq, State.VALID, path, seq.location(), report);
			}

			report.add(Severity.ERROR, loc(), path, "expected list, got " + kindOf(node), "config.type");
			return new ListAccess(null, State.INVALID, path, loc(), report);
		}

		/**
		 * Parse the value as an embedded string DSL.
		 *
		 * @param parser DSL parser to delegate to.
		 * @param <T> parser output type.
		 *
		 * @return a DSL access holding the parser result (or a MISSING/INVALID state).
		 */
		public <T> DslAccess<T> asDsl(StringDslParser<T> parser) {
			if (node == null || node instanceof NullNode) {
				return new DslAccess<>(null, State.MISSING, path, loc(), report);
			}

			if (node instanceof ScalarNode scalar) {
				T     parsed = parser.parse(scalar.value(), scalar.location(), report);
				State state  = parsed != null ? State.VALID : State.INVALID;

				return new DslAccess<>(parsed, state, path, scalar.location(), report);
			}

			report.add(Severity.ERROR, loc(), path, "expected DSL string, got " + kindOf(node), "config.type");
			return new DslAccess<>(null, State.INVALID, path, loc(), report);
		}

		/**
		 * @return location to attribute issues to: the node's, or the parent mapping's when absent.
		 */
		private SourceLocation loc() {
			return node != null ? node.location() : locForMissing;
		}

	}

	/**
	 * String access with a {@code required} validator and default fallbacks.
	 */
	public static final class StringAccess {

		private final String         value;
		private final State          state;
		private final String         path;
		private final SourceLocation loc;
		private final ConfigReport   report;

		StringAccess(String value, State state, String path, SourceLocation loc, ConfigReport report) {
			this.value  = value;
			this.state  = state;
			this.path   = path;
			this.loc    = loc;
			this.report = report;
		}

		/**
		 * @return this, after recording an {@code config.required} issue if missing.
		 */
		public StringAccess required() {
			recordIfMissing(state, loc, path, "string", report);
			return this;
		}

		/**
		 * @return the value or {@code null} when missing/invalid.
		 */
		public String orNull() {
			return state == State.VALID ? value : null;
		}

		/**
		 * @param def fallback value.
		 *
		 * @return the value when valid, otherwise {@code def}.
		 */
		public String orDefault(String def) {
			return state == State.VALID ? value : def;
		}

		/**
		 * @param fallback fallback (synonym of {@link #orDefault(String)}; reads more naturally after
		 *        {@code required()}).
		 *
		 * @return the value or the fallback.
		 */
		public String orElse(String fallback) {
			return orDefault(fallback);
		}

	}

	/**
	 * Int access with {@code min}/{@code max}/{@code required} validators.
	 */
	public static final class IntAccess {

		private final int            value;
		private final String         path;
		private final SourceLocation loc;
		private final ConfigReport   report;
		private       State          state;

		IntAccess(int value, State state, String path, SourceLocation loc, ConfigReport report) {
			this.value  = value;
			this.state  = state;
			this.path   = path;
			this.loc    = loc;
			this.report = report;
		}

		public IntAccess min(int min) {
			if (state == State.VALID && value < min) {
				report.add(Severity.ERROR, loc, path, "value " + value + " below minimum " + min, "config.range");
				state = State.INVALID;
			}
			return this;
		}

		public IntAccess max(int max) {
			if (state == State.VALID && value > max) {
				report.add(Severity.ERROR, loc, path, "value " + value + " above maximum " + max, "config.range");
				state = State.INVALID;
			}
			return this;
		}

		public IntAccess required() {
			recordIfMissing(state, loc, path, "int", report);
			return this;
		}

		public int orDefault(int def) {
			return state == State.VALID ? value : def;
		}

		public int orElse(int fallback) {
			return orDefault(fallback);
		}

	}

	/**
	 * Double access with {@code min}/{@code max}/{@code required} validators.
	 */
	public static final class DoubleAccess {

		private final double         value;
		private final String         path;
		private final SourceLocation loc;
		private final ConfigReport   report;
		private       State          state;

		DoubleAccess(double value, State state, String path, SourceLocation loc, ConfigReport report) {
			this.value  = value;
			this.state  = state;
			this.path   = path;
			this.loc    = loc;
			this.report = report;
		}

		public DoubleAccess min(double min) {
			if (state == State.VALID && value < min) {
				report.add(Severity.ERROR, loc, path, "value " + value + " below minimum " + min, "config.range");
				state = State.INVALID;
			}
			return this;
		}

		public DoubleAccess max(double max) {
			if (state == State.VALID && value > max) {
				report.add(Severity.ERROR, loc, path, "value " + value + " above maximum " + max, "config.range");
				state = State.INVALID;
			}
			return this;
		}

		public DoubleAccess required() {
			recordIfMissing(state, loc, path, "number", report);
			return this;
		}

		public double orDefault(double def) {
			return state == State.VALID ? value : def;
		}

		public double orElse(double fallback) {
			return orDefault(fallback);
		}

	}

	/**
	 * Bool access with {@code required} and default fallbacks.
	 */
	public static final class BoolAccess {

		private final boolean        value;
		private final String         path;
		private final SourceLocation loc;
		private final ConfigReport   report;
		private final State          state;

		BoolAccess(boolean value, State state, String path, SourceLocation loc, ConfigReport report) {
			this.value  = value;
			this.state  = state;
			this.path   = path;
			this.loc    = loc;
			this.report = report;
		}

		public BoolAccess required() {
			recordIfMissing(state, loc, path, "boolean", report);
			return this;
		}

		public boolean orDefault(boolean def) {
			return state == State.VALID ? value : def;
		}

		public boolean orElse(boolean fallback) {
			return orDefault(fallback);
		}

	}

	/**
	 * Mapping access — descend into a sub-mapping, e.g. to build a nested reader.
	 */
	public static final class MappingAccess {

		private final MappingNode    value;
		private final String         path;
		private final SourceLocation loc;
		private final ConfigReport   report;
		private final State          state;

		MappingAccess(MappingNode value, State state, String path, SourceLocation loc, ConfigReport report) {
			this.value  = value;
			this.state  = state;
			this.path   = path;
			this.loc    = loc;
			this.report = report;
		}

		public MappingAccess required() {
			recordIfMissing(state, loc, path, "mapping", report);
			return this;
		}

		public MappingNode orNull() {
			return state == State.VALID ? value : null;
		}

		/**
		 * @return the mapping, or an empty one anchored at the parent location when missing.
		 */
		public MappingNode orEmpty() {
			if (state == State.VALID) return value;

			return new MappingNode(Collections.emptyMap(), loc, path);
		}

		/**
		 * @return a new reader bound to this mapping, or {@code null} when missing.
		 */
		public NodeReader reader() {
			return state == State.VALID ? NodeReader.of(value, report) : null;
		}

	}

	/**
	 * Sequence access — call {@code ofStrings()} / {@code ofInts()} / {@code ofDoubles()} to specify element type.
	 */
	public static final class ListAccess {

		private final SequenceNode   value;
		private final State          state;
		private final String         path;
		private final SourceLocation loc;
		private final ConfigReport   report;

		ListAccess(SequenceNode value, State state, String path, SourceLocation loc, ConfigReport report) {
			this.value  = value;
			this.state  = state;
			this.path   = path;
			this.loc    = loc;
			this.report = report;
		}

		public TypedListAccess<String> ofStrings() {
			return collect("string", ScalarNode::value);
		}

		public TypedListAccess<Integer> ofInts() {
			return collect("int", (ScalarNode s) -> {
				try {
					return Integer.parseInt(normalizeNumeric(s.value()));
				} catch (NumberFormatException e) {
					return null;
				}
			});
		}

		public TypedListAccess<Double> ofDoubles() {
			return collect("number", (ScalarNode s) -> {
				try {
					return Double.parseDouble(normalizeNumeric(s.value()));
				} catch (NumberFormatException e) {
					return null;
				}
			});
		}

		private <T> TypedListAccess<T> collect(String typeName, java.util.function.Function<ScalarNode, T> coerce) {
			if (state == State.MISSING) return new TypedListAccess<>(null, State.MISSING, path, loc, report);
			if (state == State.INVALID) return new TypedListAccess<>(null, State.INVALID, path, loc, report);

			List<T> out = new ArrayList<>(value.items().size());

			for (ConfigNode item : value.items()) {
				if (!(item instanceof ScalarNode scalar)) {
					report.add(Severity.ERROR, item.location(), item.path(),
					           "expected " + typeName + ", got " + NodeAccess.kindOf(item), "config.type");
					continue;
				}

				T coerced = coerce.apply(scalar);

				if (coerced == null) {
					report.add(Severity.ERROR, scalar.location(), scalar.path(),
					           "expected " + typeName + ", got \"" + scalar.value() + "\"", "config." + typeName);
					continue;
				}

				out.add(coerced);
			}

			return new TypedListAccess<>(out, State.VALID, path, loc, report);
		}

	}

	/**
	 * Element-typed list access with {@code required} and default fallbacks.
	 */
	public static final class TypedListAccess<T> {

		private final List<T>        value;
		private final State          state;
		private final String         path;
		private final SourceLocation loc;
		private final ConfigReport   report;

		TypedListAccess(List<T> value, State state, String path, SourceLocation loc, ConfigReport report) {
			this.value  = value;
			this.state  = state;
			this.path   = path;
			this.loc    = loc;
			this.report = report;
		}

		public TypedListAccess<T> required() {
			recordIfMissing(state, loc, path, "list", report);
			return this;
		}

		public List<T> orNull() {
			return state == State.VALID ? value : null;
		}

		public List<T> orEmpty() {
			return state == State.VALID ? value : Collections.emptyList();
		}

	}

	// ------------------------------------------------------------ shared

	/**
	 * Dsl access with {@code required} and default fallbacks.
	 */
	public static final class DslAccess<T> {

		private final T              value;
		private final State          state;
		private final String         path;
		private final SourceLocation loc;
		private final ConfigReport   report;

		DslAccess(T value, State state, String path, SourceLocation loc, ConfigReport report) {
			this.value  = value;
			this.state  = state;
			this.path   = path;
			this.loc    = loc;
			this.report = report;
		}

		public DslAccess<T> required() {
			recordIfMissing(state, loc, path, "DSL value", report);
			return this;
		}

		public T orNull() {
			return state == State.VALID ? value : null;
		}

		public T orDefault(T def) {
			return state == State.VALID ? value : def;
		}

	}

}
