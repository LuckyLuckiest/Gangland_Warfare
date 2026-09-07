package org.luckyraven.gangland.item.fuel;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * NBT tag keys used to identify and store fuel item data.
 */
@Getter
@RequiredArgsConstructor
public enum FuelKey {

	FUEL_ID("fuel"),
	FUEL_CURRENT("fuel_current"),
	FUEL_MAX("fuel_max");

	private final String key;
}
