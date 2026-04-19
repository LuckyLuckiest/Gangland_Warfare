package me.luckyraven.persistence.config;

import java.util.Map;

/**
 * A YAML mapping (key/value dictionary).
 *
 * @param entries insertion-ordered entries. Implementations are free to wrap this in
 *        {@link java.util.Collections#unmodifiableMap}; callers must not mutate it.
 * @param location the source range covering the mapping.
 * @param path path from document root to this mapping.
 */
public record MappingNode(Map<String, ConfigNode> entries, SourceLocation location, String path) implements ConfigNode {

	/**
	 * Convenience accessor — returns the child node for {@code key} or {@code null} when absent.
	 *
	 * @param key mapping key.
	 *
	 * @return the mapped node, or {@code null} if no entry exists.
	 */
	public ConfigNode get(String key) {
		return entries.get(key);
	}

	/**
	 * @param key mapping key to look up.
	 *
	 * @return {@code true} when the mapping contains an entry for {@code key}.
	 */
	public boolean has(String key) {
		return entries.containsKey(key);
	}

}
