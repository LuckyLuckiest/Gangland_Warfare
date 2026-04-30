package org.luckyraven.gangland.persistence.config.dsl;

import org.luckyraven.gangland.persistence.config.SourceLocation;

import java.util.Collections;
import java.util.Map;

/**
 * Positional result of parsing a bracketed attribute DSL such as {@code item[custom_model_data=1021,effects={}]}.
 *
 * <p>{@link DslValue} is a recursive tree: the root holds the head name and a
 * map of attributes; each attribute's value is itself a {@code DslValue}, allowing nested structures like
 * {@code effects={fire=true}}.
 *
 * @param name the head name (e.g. {@code item}); {@code null} for bare values.
 * @param attrs attribute map, insertion-ordered; empty when the value has no bracketed tail.
 * @param raw the raw slice of source this value was parsed from.
 * @param at position of this value inside the enclosing scalar.
 */
public record DslValue(String name, Map<String, DslValue> attrs, String raw, SourceLocation at) {

	/**
	 * Factory for a leaf value (no name, no attrs) such as a numeric attribute value.
	 *
	 * @param raw raw text.
	 * @param at location.
	 *
	 * @return a leaf {@code DslValue}.
	 */
	public static DslValue leaf(String raw, SourceLocation at) {
		return new DslValue(null, Collections.emptyMap(), raw, at);
	}

	/**
	 * @param key attribute name.
	 *
	 * @return the attribute's parsed value, or {@code null} if absent.
	 */
	public DslValue attr(String key) {
		return attrs.get(key);
	}

	/**
	 * @param key attribute name.
	 *
	 * @return {@code true} when the attribute exists.
	 */
	public boolean has(String key) {
		return attrs.containsKey(key);
	}

}
