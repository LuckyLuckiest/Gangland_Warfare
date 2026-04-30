package org.luckyraven.gangland.persistence.config;

/**
 * A scalar (string / number / boolean / null-typed string) node.
 *
 * @param value the raw textual value as it appeared in the source.
 * @param tag the SnakeYAML resolved tag (e.g. {@code tag:yaml.org,2002:str}); useful for distinguishing a quoted
 *        {@code "42"} from the integer {@code 42}.
 * @param location the source range covering this scalar.
 * @param path the path from document root to this value.
 */
public record ScalarNode(String value, String tag, SourceLocation location, String path) implements ConfigNode {

}
