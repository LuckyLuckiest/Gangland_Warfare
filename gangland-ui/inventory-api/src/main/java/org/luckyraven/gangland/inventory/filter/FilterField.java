package org.luckyraven.gangland.inventory.filter;

/**
 * A field a {@link SearchFilter} can narrow or sort by. Canonical fields live in {@link StandardFilterField}; features
 * (gangs, trader shop, auctions, etc.) may implement this interface to declare domain-specific fields without touching
 * the inventory-api module.
 */
public interface FilterField {

	String id();

	InputKind inputKind();

	enum InputKind {

		TEXT,
		ENUM,
		RANGE,
		DATE,
		BOOLEAN

	}

}
