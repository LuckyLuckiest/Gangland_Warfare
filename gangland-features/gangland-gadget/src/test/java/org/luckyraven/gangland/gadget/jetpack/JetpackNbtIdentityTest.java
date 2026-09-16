package org.luckyraven.gangland.gadget.jetpack;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.testsupport.BukkitRegistryFixture;
import org.luckyraven.gangland.gadget.item.GadgetItemPredicates;
import org.luckyraven.gangland.item.fuel.FuelKey;
import org.luckyraven.keystone.item.ItemBuilder;
import org.luckyraven.keystone.item.nbt.NbtBridge;
import org.luckyraven.keystone.testkit.RecordingNbtAccessor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins {@link Jetpack#isJetpackItem}/{@link Jetpack#getJetpackId}/{@link Jetpack#getPermission} NBT-tag identity
 * checks, mirroring {@code car.CarNbtIdentityTest} (WS7 G3, gadget-wave-2026-09-16). Also pins the plan's §7 ask
 * that {@link GadgetItemPredicates#CAR} and {@link GadgetItemPredicates#JETPACK} are mutually exclusive on a
 * jetpack-tagged stack.
 */
@DisplayName("Jetpack — NBT tag identity (isJetpackItem / getJetpackId / getPermission)")
class JetpackNbtIdentityTest {

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
	@DisplayName("isJetpackItem/getJetpackId are false/null for an item with no jetpack NBT tag at all")
	void isJetpackItem_noTag_false() {
		ItemStack plain = new ItemStack(Material.IRON_CHESTPLATE);

		assertFalse(Jetpack.isJetpackItem(plain));
		assertNull(Jetpack.getJetpackId(plain));
	}

	@Test
	@DisplayName("isJetpackItem/getJetpackId read back exactly what buildItem's NBT-tag step would have stamped")
	void isJetpackItem_stampedTag_readsBack() {
		ItemStack stack = new ItemStack(Material.IRON_CHESTPLATE);
		new ItemBuilder(stack).addTag(JetpackKey.JETPACK_ID.getKey(), "jetpack");

		assertTrue(Jetpack.isJetpackItem(stack));
		assertEquals("jetpack", Jetpack.getJetpackId(stack));
	}

	@Test
	@DisplayName("isJetpackItem/getJetpackId are false/null for a null item or an AIR stack")
	void isJetpackItem_nullOrAir_falseNull() {
		assertFalse(Jetpack.isJetpackItem(null));
		assertNull(Jetpack.getJetpackId(null));

		ItemStack air = new ItemStack(Material.AIR);
		assertFalse(Jetpack.isJetpackItem(air));
		assertNull(Jetpack.getJetpackId(air));
	}

	@Test
	@DisplayName("getPermission derives 'gangland.jetpacks.<jetpackId>' from the identity field, independent of NBT")
	void getPermission_derivedFromJetpackId() {
		Jetpack jetpack = Jetpack.builder().jetpackId("jetpack").build();

		assertEquals("gangland.jetpacks.jetpack", jetpack.getPermission());
	}

	@Test
	@DisplayName("a jetpack item also carries the fuel NBT tags buildItem() stamps, and is still recognized as a jetpack")
	void jetpackItem_carriesFuelTags_stillRecognized() {
		ItemStack   stack   = new ItemStack(Material.IRON_CHESTPLATE);
		ItemBuilder builder = new ItemBuilder(stack);
		builder.addTag(JetpackKey.JETPACK_ID.getKey(), "jetpack");
		builder.addTag(FuelKey.FUEL_ID.getKey(), "jetpack_fuel");
		builder.addTag(FuelKey.FUEL_CURRENT.getKey(), 3600);
		builder.addTag(FuelKey.FUEL_MAX.getKey(), 3600);

		assertTrue(Jetpack.isJetpackItem(stack));
		assertEquals("jetpack", Jetpack.getJetpackId(stack));
	}

	@Test
	@DisplayName("GadgetItemPredicates.CAR and JETPACK are mutually exclusive on a jetpack-tagged stack")
	void carAndJetpackPredicates_mutuallyExclusive() {
		ItemStack stack = new ItemStack(Material.IRON_CHESTPLATE);
		new ItemBuilder(stack).addTag(JetpackKey.JETPACK_ID.getKey(), "jetpack");

		assertTrue(GadgetItemPredicates.JETPACK.test(stack));
		assertFalse(GadgetItemPredicates.CAR.test(stack));
	}

	@Test
	@DisplayName("buildItem() stamps JETPACK_ID and a full, factory-fresh fuel load (FUEL_CURRENT == Max_Fuel) — "
	             + "the refresher's whole contract (review I2)")
	void buildItem_stampsIdentityAndFullFuel() {
		Jetpack jetpack = Jetpack.builder()
		                         .jetpackId("jetpack")
		                         .displayName("&bJetpack")
		                         .material(Material.IRON_CHESTPLATE)
		                         .fuelKey("gasoline")
		                         .maxFuel(3600)
		                         .build();

		ItemStack   stack   = jetpack.buildItem();
		ItemBuilder builder = new ItemBuilder(stack);

		assertTrue(Jetpack.isJetpackItem(stack));
		assertEquals("jetpack", Jetpack.getJetpackId(stack));
		assertEquals(3600, builder.getIntegerTagData(FuelKey.FUEL_CURRENT.getKey()), "FUEL_CURRENT == Max_Fuel");
		assertEquals(3600, builder.getIntegerTagData(FuelKey.FUEL_MAX.getKey()));
	}

}
