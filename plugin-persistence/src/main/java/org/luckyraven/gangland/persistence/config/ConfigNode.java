package org.luckyraven.gangland.persistence.config;

/**
 * Sealed root of the positional config node tree produced by {@link ConfigParser}.
 *
 * <p>Every node carries the {@link SourceLocation} of the value it represents and
 * the dotted/bracketed {@link #path()} back to the document root. The tree is immutable; mutation is done by building a
 * new tree (round-trip serialization is the primary write path).
 */
public sealed interface ConfigNode permits ScalarNode, MappingNode, SequenceNode, NullNode {

	/**
	 * @return the source range this node occupies in its originating file.
	 */
	SourceLocation location();

	/**
	 * @return the dotted/bracketed path from document root; {@code ""} for the root mapping.
	 */
	String path();

}
