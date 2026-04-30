package org.luckyraven.gangland.persistence.config;

/**
 * Explicit absence of a value — used for YAML nulls and synthetic placeholders (e.g. the root of a document whose
 * top-level YAML node is itself null).
 *
 * @param location the source range; may point at the key position for missing values.
 * @param path path from document root.
 */
public record NullNode(SourceLocation location, String path) implements ConfigNode {

}
