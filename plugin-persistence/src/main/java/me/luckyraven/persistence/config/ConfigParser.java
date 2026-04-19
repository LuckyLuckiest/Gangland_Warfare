package me.luckyraven.persistence.config;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.Mark;
import org.yaml.snakeyaml.error.MarkedYAMLException;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.Tag;

import java.io.Reader;
import java.nio.file.Path;
import java.util.*;

/**
 * Parses YAML source text into a {@link ConfigDocument}: a positional {@link ConfigNode} tree whose every node carries
 * a {@link SourceLocation}.
 *
 * <p>Wraps SnakeYAML's {@link Yaml#compose(Reader)} so the layer has direct
 * access to {@link Mark}s. Parse errors (malformed indentation, unclosed strings, etc.) are funnelled into the supplied
 * {@link ConfigReport} as {@code yaml.parse} issues — the parser never throws on bad input.
 *
 * <p>When the top-level YAML node is not a mapping (an unusual shape for our
 * configs, but legal YAML), the parser records a {@code yaml.top_level} error and returns an empty mapping so
 * downstream readers still see a well-formed document.
 */
public final class ConfigParser {

	private static String joinPath(String parent, String key) {
		if (parent == null || parent.isEmpty()) return key;

		return parent + "." + key;
	}

	private static SourceLocation markRange(Path file, Mark start, Mark end) {
		if (start == null) return new SourceLocation(file, 1, 1, 1, 1);

		return SourceLocation.of(file, start, end);
	}

	private static SourceLocation markLocation(Path file, Mark mark) {
		if (mark == null) return new SourceLocation(file, 1, 1, 1, 1);

		return SourceLocation.of(file, mark, mark);
	}

	private static ConfigDocument emptyDocument(Path file, SourceLocation location) {
		MappingNode empty = new MappingNode(Collections.emptyMap(), location, "");

		return new ConfigDocument(empty, file);
	}

	/**
	 * Parse a YAML source into a positional document.
	 *
	 * @param file path used to populate {@link SourceLocation#file()}; may be {@code null} for streams.
	 * @param reader the YAML source; the caller owns closing it.
	 * @param report collector for parse issues.
	 *
	 * @return the parsed document. Never {@code null}; on any failure the root is an empty mapping.
	 */
	public ConfigDocument parse(Path file, Reader reader, ConfigReport report) {
		Yaml yaml = new Yaml();
		Node root;

		try {
			root = yaml.compose(reader);
		} catch (MarkedYAMLException exception) {
			SourceLocation at = markLocation(file, exception.getProblemMark());
			report.add(Severity.ERROR, at, "", exception.getProblem(), "yaml.parse");

			return emptyDocument(file, at);
		} catch (YAMLException exception) {
			SourceLocation at = new SourceLocation(file, 1, 1, 1, 1);
			report.add(Severity.ERROR, at, "", exception.getMessage(), "yaml.parse");

			return emptyDocument(file, at);
		}

		if (root == null) return emptyDocument(file, new SourceLocation(file, 1, 1, 1, 1));

		ConfigNode converted = convert(file, root, "", report);

		if (converted instanceof MappingNode mapping) return new ConfigDocument(mapping, file);

		report.add(Severity.ERROR, converted.location(), "",
		           "expected a top-level mapping, got " + root.getNodeId().toString().toLowerCase(),
		           "yaml.top_level");

		return emptyDocument(file, converted.location());
	}

	private ConfigNode convert(Path file, Node node, String path, ConfigReport report) {
		SourceLocation location = markRange(file, node.getStartMark(), node.getEndMark());

		return switch (node.getNodeId()) {
			case mapping -> convertMapping(file, (org.yaml.snakeyaml.nodes.MappingNode) node, path, location, report);
			case sequence -> convertSequence(file, (org.yaml.snakeyaml.nodes.SequenceNode) node, path, location,
			                                 report);
			case scalar -> convertScalar((org.yaml.snakeyaml.nodes.ScalarNode) node, path, location);
			case anchor -> convert(file, ((org.yaml.snakeyaml.nodes.AnchorNode) node).getRealNode(), path, report);
		};
	}

	private ConfigNode convertScalar(org.yaml.snakeyaml.nodes.ScalarNode node, String path, SourceLocation location) {
		String value = node.getValue();
		Tag    tag   = node.getTag();

		if (tag != null && tag.equals(Tag.NULL)) return new NullNode(location, path);

		return new ScalarNode(value, tag != null ? tag.getValue() : Tag.STR.getValue(), location, path);
	}

	private ConfigNode convertMapping(Path file, org.yaml.snakeyaml.nodes.MappingNode node,
	                                  String path, SourceLocation location, ConfigReport report) {
		Map<String, ConfigNode> entries = new LinkedHashMap<>();

		for (NodeTuple tuple : node.getValue()) {
			Node keyNode   = tuple.getKeyNode();
			Node valueNode = tuple.getValueNode();

			if (!(keyNode instanceof org.yaml.snakeyaml.nodes.ScalarNode keyScalar)) {
				report.add(Severity.ERROR, markRange(file, keyNode.getStartMark(), keyNode.getEndMark()),
				           path, "mapping key must be a scalar", "yaml.key");
				continue;
			}

			String key = keyScalar.getValue();

			if (entries.containsKey(key)) {
				report.add(Severity.WARNING, markRange(file, keyNode.getStartMark(), keyNode.getEndMark()),
				           joinPath(path, key), "duplicate key '" + key + "' — later value overrides earlier",
				           "yaml.duplicate");
			}

			String     childPath = joinPath(path, key);
			ConfigNode child     = convert(file, valueNode, childPath, report);

			entries.put(key, child);
		}

		return new MappingNode(Collections.unmodifiableMap(entries), location, path);
	}

	private ConfigNode convertSequence(Path file, org.yaml.snakeyaml.nodes.SequenceNode node,
	                                   String path, SourceLocation location, ConfigReport report) {
		List<Node>       rawItems = node.getValue();
		List<ConfigNode> items    = new ArrayList<>(rawItems.size());

		for (int i = 0; i < rawItems.size(); i++) {
			String childPath = path + "[" + i + "]";
			items.add(convert(file, rawItems.get(i), childPath, report));
		}

		return new SequenceNode(Collections.unmodifiableList(items), location, path);
	}

}
