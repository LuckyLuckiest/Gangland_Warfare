package org.luckyraven.gangland.persistence.config;

import java.util.List;

/**
 * A YAML sequence (ordered list).
 *
 * @param items ordered items. Must not be mutated by callers.
 * @param location the source range covering the sequence.
 * @param path path from document root to this sequence (elements use {@code path[index]} form).
 */
public record SequenceNode(List<ConfigNode> items, SourceLocation location, String path) implements ConfigNode {

}
