package org.luckyraven.gangland.gadget.car;

import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Central registry for {@link Car} configurations loaded from {@code cars.yml}.
 */
public class CarManager {

	private final Map<String, Car> cars = new HashMap<>();

	public void register(String key, Car car) {
		cars.put(key.toLowerCase(), car);
	}

	@Nullable
	public Car getCar(String key) {
		return cars.get(key.toLowerCase());
	}

	public Map<String, Car> getCars() {
		return Collections.unmodifiableMap(cars);
	}

	public void clear() {
		cars.clear();
	}
}
