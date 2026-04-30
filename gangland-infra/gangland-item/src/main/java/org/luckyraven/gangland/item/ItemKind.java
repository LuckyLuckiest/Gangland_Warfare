package org.luckyraven.gangland.item;

/**
 * Single source of truth for the logical identity of every item kind the plugin handles. Used as a stable key by both
 * sides of the item pipeline: {@link ItemConverter}s (string → ItemStack) are registered under the kind's label, and
 * {@link ItemSerializer}s (ItemStack → string) expose the kind via {@link ItemSerializer#kind()}. Renaming a label here
 * updates every converter, serializer, and ledger id at once — callers never hardcode the string.
 *
 * <p>Predicates (the "is this stack a weapon?" checks) deliberately do not live here because they reference
 * NBT-key constants that belong to feature modules ({@code gangland-gadget}, …). They live in a parallel
 * {@code ItemPredicates} class in {@code gangland-impl}.
 */
public enum ItemKind {
	UNIQUE("unique"),
	WEAPON("weapon"),
	AMMUNITION("ammunition"),
	WEARABLE("wearable"),
	CAR("car"),
	MONEY("money"),
	MATERIAL("material");

	private final String label;

	ItemKind(String label) {
		this.label = label;
	}

	public String label() {
		return label;
	}
}
