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
	CAR_OWNER("car_owner"),
	CAR_DURABILITY("car_durability"),
	CAR_MAX_DURABILITY("car_max_durability"),
	CAR_EXHAUST_SIDE("car_exhaust_side");

	private final String key;
}
