package me.luckyraven.inventory.filter;

/**
 * Canonical filter fields shared across views. Features may still implement {@link FilterField} directly for
 * domain-specific axes (e.g. {@code BID_COUNT} on an auction list).
 */
public enum StandardFilterField implements FilterField {

	NAME(InputKind.TEXT),
	CATEGORY(InputKind.ENUM),
	COLOR(InputKind.ENUM),
	DESCRIPTION(InputKind.TEXT),
	DATE(InputKind.DATE),
	MEMBERS(InputKind.RANGE),
	PRICE(InputKind.RANGE),
	QUANTITY(InputKind.RANGE),
	RARITY(InputKind.ENUM),
	TAG(InputKind.ENUM);

	private final InputKind inputKind;

	StandardFilterField(InputKind inputKind) {
		this.inputKind = inputKind;
	}

	@Override
	public String id() {
		return name();
	}

	@Override
	public InputKind inputKind() {
		return inputKind;
	}

}
