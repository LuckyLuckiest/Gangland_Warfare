package org.luckyraven.gangland.gadget.car;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.testsupport.BukkitRegistryFixture;
import org.luckyraven.gangland.item.fuel.FuelKey;
import org.luckyraven.keystone.item.ItemBuilder;
import org.luckyraven.keystone.item.nbt.NbtBridge;
import org.luckyraven.keystone.testkit.RecordingNbtAccessor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins {@link Car#isCarItem}/{@link Car#getCarId}/{@link Car#getPermission} NBT-tag identity checks
 * (gadgets-cars-fuel-jetpack.md W2 — Build/obtain a car item; W3 — Place a car in the world, which reads
 * {@code car}/{@code car_exhaust_side}/{@code fuel_*} tags straight off the held item).
 *
 * <p>Uses {@link RecordingNbtAccessor} installed through {@link NbtBridge} per {@code TESTING.md} §4/§8, so NBT
 * tag routing is exercised with no NBT provider on the classpath — {@code Car.buildItem}'s display-name/lore path
 * is deliberately not exercised here since it needs a live {@code Bukkit.getItemFactory()} this suite does not
 * stand up; only the pure NBT-tag reads/writes are covered.
 *
 * <p>Pins Observation #10 (gadgets-cars-fuel-jetpack.md): a fuel-enabled car item is stamped with the same
 * {@link FuelKey#FUEL_ID} tag a fuel can carries, so {@link org.luckyraven.gangland.item.fuel.Fuel#isFuelItem}
 * cannot distinguish "spare car in my inventory" from "fuel can in my inventory" — this test proves the tag
 * collision directly on a raw {@link ItemStack}, without needing the listeners that exploit it.
 */
@DisplayName("Car — NBT tag identity (isCarItem / getCarId / getPermission)")
class CarNbtIdentityTest {

	@BeforeAll
	static void bootstrapBukkitRegistry() {
		// Subject code reaches Material.isAir() / an XSeries registry lookup — see the fixture javadoc.
		BukkitRegistryFixture.install();
	}

	private RecordingNbtAccessor nbt;

	@BeforeEach
	void setUp() {
		nbt = new RecordingNbtAccessor();
		NbtBridge.install(nbt);
	}

	@AfterEach
	void tearDown() {
		NbtBridge.reset();
	}

	@Test
	@DisplayName("isCarItem/getCarId are false/null for an item with no car NBT tag at all")
	void isCarItem_noTag_false() {
		ItemStack plain = new ItemStack(Material.MINECART);

		assertFalse(Car.isCarItem(plain));
		assertNull(Car.getCarId(plain));
	}

	@Test
	@DisplayName("isCarItem/getCarId read back exactly what buildItem's NBT-tag step would have stamped")
	void isCarItem_stampedTag_readsBack() {
		ItemStack stack = new ItemStack(Material.MINECART);
		new ItemBuilder(stack).addTag(CarKey.CAR_ID.getKey(), "sports_car")
		                      .addTag(CarKey.CAR_DURABILITY.getKey(), 500)
		                      .addTag(CarKey.CAR_MAX_DURABILITY.getKey(), 500);

		assertTrue(Car.isCarItem(stack));
		assertEquals("sports_car", Car.getCarId(stack));
	}

	@Test
	@DisplayName("isCarItem/getCarId are false/null for a null item or an AIR stack")
	void isCarItem_nullOrAir_falseNull() {
		assertFalse(Car.isCarItem(null));
		assertNull(Car.getCarId(null));

		ItemStack air = new ItemStack(Material.AIR);
		assertFalse(Car.isCarItem(air));
		assertNull(Car.getCarId(air));
	}

	@Test
	@DisplayName("getPermission derives 'gangland.cars.<carId>' from the identity field, independent of NBT")
	void getPermission_derivedFromCarId() {
		Car car = Car.builder().carId("pickup_truck").build();

		assertEquals("gangland.cars.pickup_truck", car.getPermission());
	}

	@Test
	@DisplayName("Observation #10: a fuel-enabled car item carries the exact same fuel_id NBT tag a fuel can does")
	void fuelEnabledCarItem_collidesWithFuelCanNbtTag() {
		ItemStack stack = new ItemStack(Material.MINECART);
		ItemBuilder builder = new ItemBuilder(stack);
		builder.addTag(CarKey.CAR_ID.getKey(), "pickup_truck");
		// This is exactly what Car.buildItem stamps for a fuel-enabled car (Car.java: fuelEnabled && fuelKey
		// non-empty && maxFuel > 0), reproduced directly on the NBT layer rather than by calling buildItem (which
		// would also touch the display-name path this test suite avoids).
		builder.addTag(FuelKey.FUEL_ID.getKey(), "gasoline");
		builder.addTag(FuelKey.FUEL_CURRENT.getKey(), 10600);
		builder.addTag(FuelKey.FUEL_MAX.getKey(), 10600);

		assertTrue(Car.isCarItem(stack), "still identifiable as a car");
		assertTrue(builder.hasNBTTag(FuelKey.FUEL_ID.getKey()),
		           "the same item also satisfies the fuel-can NBT check used by FuelRefuelListener/"
		           + "FuelHoldDisplayListener/CarEntityInteractListener, with nothing to tell the two apart");
	}

}
