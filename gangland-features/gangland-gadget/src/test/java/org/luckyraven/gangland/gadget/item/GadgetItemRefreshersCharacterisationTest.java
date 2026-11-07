package org.luckyraven.gangland.gadget.item;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.testsupport.BukkitRegistryFixture;
import org.luckyraven.gangland.gadget.car.Car;
import org.luckyraven.gangland.gadget.car.CarKey;
import org.luckyraven.gangland.gadget.car.config.CarAddon;
import org.luckyraven.gangland.gadget.jetpack.Jetpack;
import org.luckyraven.gangland.gadget.jetpack.JetpackKey;
import org.luckyraven.gangland.gadget.jetpack.config.JetpackAddon;
import org.luckyraven.keystone.item.ItemBuilder;
import org.luckyraven.keystone.item.nbt.NbtBridge;
import org.luckyraven.keystone.testkit.RecordingNbtAccessor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Characterises {@code CarItemRefresher.refresh} and {@code JetpackItemRefresher.refresh} (no other test reaches them)
 * with real {@link Car}/{@link Jetpack} values, before the {@code context != null ? x.buildItem(context) :
 * x.buildItem()} ternary is collapsed: with and without a player context the refresher returns a freshly built, stamped
 * stack carrying the source's amount, and unknown or empty ids give {@code null}.
 */
@DisplayName("Car/Jetpack item refreshers — rebuild with and without a player context")
class GadgetItemRefreshersCharacterisationTest {

	@BeforeAll
	static void bootstrap() {
		BukkitRegistryFixture.install();
	}

	@BeforeEach
	void setUp() {
		NbtBridge.install(new RecordingNbtAccessor());
	}

	@AfterEach
	void tearDown() {
		NbtBridge.reset();
	}

	private static ItemStack source(Material material, String key, String id, int amount) {
		ItemStack stack = new ItemStack(material, amount);
		new ItemBuilder(stack).addTag(key, id);
		return stack;
	}

	@Test
	@DisplayName("car: a player context and no context both rebuild a stamped car with the source amount")
	void car_refresh_withAndWithoutContext() {
		CarAddon carAddon = mock(CarAddon.class);
		Car      car      = Car.builder().carId("sports").itemMaterial(Material.MINECART).maxDurability(500).build();
		when(carAddon.getCar("sports")).thenReturn(car);
		CarItemRefresher refresher = new CarItemRefresher(carAddon);

		ItemStack template = source(Material.MINECART, CarKey.CAR_ID.getKey(), "sports", 3);

		ItemStack withoutContext = refresher.refresh(template, null);
		ItemStack withContext    = refresher.refresh(template, mock(Player.class));

		for (ItemStack built : new ItemStack[]{withoutContext, withContext}) {
			assertNotNull(built);
			assertEquals(Material.MINECART, built.getType());
			assertEquals(3, built.getAmount());
			assertEquals("sports", Car.getCarId(built));
		}
	}

	@Test
	@DisplayName("car: an unknown id, an empty id and a non-car stack all give null")
	void car_refresh_nullCases() {
		CarAddon carAddon = mock(CarAddon.class);
		CarItemRefresher refresher = new CarItemRefresher(carAddon);

		assertNull(refresher.refresh(source(Material.MINECART, CarKey.CAR_ID.getKey(), "ghost", 1), null));
		assertNull(refresher.refresh(source(Material.MINECART, CarKey.CAR_ID.getKey(), "", 2), null));
	}

	@Test
	@DisplayName("jetpack: a player context and no context both rebuild a stamped jetpack with the source amount")
	void jetpack_refresh_withAndWithoutContext() {
		JetpackAddon jetpackAddon = mock(JetpackAddon.class);
		Jetpack      jetpack      = Jetpack.builder().jetpackId("jp").material(Material.IRON_CHESTPLATE).build();
		when(jetpackAddon.getJetpack("jp")).thenReturn(jetpack);
		JetpackItemRefresher refresher = new JetpackItemRefresher(jetpackAddon);

		ItemStack template = source(Material.IRON_CHESTPLATE, JetpackKey.JETPACK_ID.getKey(), "jp", 2);

		ItemStack withoutContext = refresher.refresh(template, null);
		ItemStack withContext    = refresher.refresh(template, mock(Player.class));

		for (ItemStack built : new ItemStack[]{withoutContext, withContext}) {
			assertNotNull(built);
			assertEquals(Material.IRON_CHESTPLATE, built.getType());
			assertEquals(2, built.getAmount());
			assertEquals("jp", Jetpack.getJetpackId(built));
		}
	}

	@Test
	@DisplayName("jetpack: an unknown id and an empty id give null")
	void jetpack_refresh_nullCases() {
		JetpackAddon         jetpackAddon = mock(JetpackAddon.class);
		JetpackItemRefresher refresher    = new JetpackItemRefresher(jetpackAddon);

		assertNull(refresher.refresh(source(Material.IRON_CHESTPLATE, JetpackKey.JETPACK_ID.getKey(), "ghost", 1), null));
		assertNull(refresher.refresh(source(Material.IRON_CHESTPLATE, JetpackKey.JETPACK_ID.getKey(), "", 2), null));
	}
}
