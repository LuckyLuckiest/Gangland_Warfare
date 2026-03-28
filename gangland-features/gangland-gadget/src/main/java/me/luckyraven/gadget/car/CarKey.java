package me.luckyraven.gadget.car;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * NBT tag keys used to identify and store car item data.
 */
@Getter
@RequiredArgsConstructor
public enum CarKey {

	CAR_ID("car"),
	CAR_FUEL("car_fuel"),
	CAR_MAX_FUEL("car_max_fuel"),
	CAR_OWNER("car_owner"),
	CAR_DURABILITY("car_durability"),
	CAR_MAX_DURABILITY("car_max_durability");

	private final String key;
}
