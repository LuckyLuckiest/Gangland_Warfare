package me.luckyraven.persistence.config;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.nodes.Tag;

import java.io.Writer;
import java.util.*;

/**
 * Writes a {@link ConfigNode} tree back to YAML.
 *
 * <p>Round-trip only: read → mutate tree → write. Comments from the source
 * file are <b>not</b> preserved — SnakeYAML 1.x's {@code compose()} discards them, and the shaded version bundled with
 * Spigot does not include the comment-preservation upgrade. Loaders that must keep comments intact should continue to
 * use {@code FileHandler#getFileConfiguration().save()} on the Bukkit path.
 *
 * <p>Structure preservation:
 * <ul>
 *   <li>Mapping key order matches the {@link MappingNode#entries()} iteration order.</li>
 *   <li>Sequence element order matches {@link SequenceNode#items()}.</li>
 *   <li>Scalar YAML tag ({@code int}, {@code float}, {@code bool}, {@code null}, {@code str})
 *       is re-materialized so a round-tripped {@code "42"} stays quoted and
 *       a round-tripped {@code 42} stays unquoted.</li>
 * </ul>
 */
public final class ConfigSerializer {

	/**
	 * Dump {@code node} to {@code writer} using block YAML style.
	 *
	 * @param node the root node. Usually a {@link MappingNode}; any shape is accepted.
	 * @param writer output; the caller is responsible for closing it.
	 */
	public void write(ConfigNode node, Writer writer) {
		Object java = toJava(node);

		DumperOptions options = new DumperOptions();
		options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
		options.setIndent(2);
		options.setPrettyFlow(true);

		Yaml yaml = new Yaml(options);
		yaml.dump(java, writer);
	}

	/**
	 * Convenience overload for in-memory serialization / tests.
	 *
	 * @param node the root.
	 *
	 * @return the YAML document as a string.
	 */
	public String writeToString(ConfigNode node) {
		Object java = toJava(node);

		DumperOptions options = new DumperOptions();
		options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
		options.setIndent(2);
		options.setPrettyFlow(true);

		Yaml yaml = new Yaml(options);
		return yaml.dump(java);
	}

	private Object toJava(ConfigNode node) {
		return switch (node) {
			case NullNode ignored -> null;
			case ScalarNode s -> scalarToJava(s);
			case MappingNode m -> mappingToJava(m);
			case SequenceNode s -> sequenceToJava(s);
		};
	}

	private Map<String, Object> mappingToJava(MappingNode mapping) {
		Map<String, Object> out = new LinkedHashMap<>();

		for (Map.Entry<String, ConfigNode> entry : mapping.entries().entrySet()) {
			out.put(entry.getKey(), toJava(entry.getValue()));
		}

		return out;
	}

	private List<Object> sequenceToJava(SequenceNode sequence) {
		List<Object> out = new ArrayList<>(sequence.items().size());

		for (ConfigNode item : sequence.items()) {
			out.add(toJava(item));
		}

		return out;
	}

	private Object scalarToJava(ScalarNode scalar) {
		String tag   = scalar.tag();
		String value = scalar.value();

		if (tag == null) return value;

		if (tag.equals(Tag.INT.getValue())) {
			try {
				long parsed = Long.parseLong(value.trim());

				if (parsed >= Integer.MIN_VALUE && parsed <= Integer.MAX_VALUE) return (int) parsed;
				return parsed;
			} catch (NumberFormatException e) {
				return value;
			}
		}

		if (tag.equals(Tag.FLOAT.getValue())) {
			try {
				return Double.parseDouble(value.trim());
			} catch (NumberFormatException e) {
				return value;
			}
		}

		if (tag.equals(Tag.BOOL.getValue())) {
			String v = value.trim().toLowerCase(Locale.ROOT);

			if (v.equals("true") || v.equals("yes") || v.equals("on")) return Boolean.TRUE;
			if (v.equals("false") || v.equals("no") || v.equals("off")) return Boolean.FALSE;

			return value;
		}

		return value;
	}

}
