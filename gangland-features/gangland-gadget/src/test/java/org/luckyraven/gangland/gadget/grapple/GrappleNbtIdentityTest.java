package org.luckyraven.gangland.gadget.grapple;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.testsupport.BukkitRegistryFixture;
import org.luckyraven.gangland.gadget.item.GadgetItemPredicates;
import org.luckyraven.keystone.item.ItemBuilder;
import org.luckyraven.keystone.item.nbt.NbtBridge;
import org.luckyraven.keystone.testkit.RecordingNbtAccessor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins {@link Grapple#isGrappleItem}/{@link Grapple#getGrappleId}/{@link Grapple#getPermission} NBT-tag identity
 * checks, mirroring {@code jetpack.JetpackNbtIdentityTest} (itself mirroring {@code car.CarNbtIdentityTest}, S4) —
 * including the permission assertion.
 */
@DisplayName("Grapple — NBT tag identity (isGrappleItem / getGrappleId / getPermission)")
class GrappleNbtIdentityTest {

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
	@DisplayName("isGrappleItem/getGrappleId are false/null for an item with no grapple NBT tag at all")
	void isGrappleItem_noTag_false() {
		ItemStack plain = new ItemStack(Material.FISHING_ROD);

		assertFalse(Grapple.isGrappleItem(plain));
		assertNull(Grapple.getGrappleId(plain));
	}

	@Test
	@DisplayName("isGrappleItem/getGrappleId read back exactly what buildItem's NBT-tag step would have stamped")
	void isGrappleItem_stampedTag_readsBack() {
		ItemStack stack = new ItemStack(Material.FISHING_ROD);
		new ItemBuilder(stack).addTag(GrappleKey.GRAPPLE_ID.getKey(), "grapple");

		assertTrue(Grapple.isGrappleItem(stack));
		assertEquals("grapple", Grapple.getGrappleId(stack));
	}

	@Test
	@DisplayName("isGrappleItem/getGrappleId are false/null for a null item or an AIR stack")
	void isGrappleItem_nullOrAir_falseNull() {
		assertFalse(Grapple.isGrappleItem(null));
		assertNull(Grapple.getGrappleId(null));

		ItemStack air = new ItemStack(Material.AIR);
		assertFalse(Grapple.isGrappleItem(air));
		assertNull(Grapple.getGrappleId(air));
	}

	@Test
	@DisplayName("getPermission derives 'gangland.grapples.<grappleId>' from the identity field, independent of NBT")
	void getPermission_derivedFromGrappleId() {
		Grapple grapple = Grapple.builder().grappleId("grapple").build();

		assertEquals("gangland.grapples.grapple", grapple.getPermission());
	}

	@Test
	@DisplayName("GadgetItemPredicates.CAR/JETPACK and GRAPPLE are mutually exclusive on a grapple-tagged stack")
	void carJetpackAndGrapplePredicates_mutuallyExclusive() {
		ItemStack stack = new ItemStack(Material.FISHING_ROD);
		new ItemBuilder(stack).addTag(GrappleKey.GRAPPLE_ID.getKey(), "grapple");

		assertTrue(GadgetItemPredicates.GRAPPLE.test(stack));
		assertFalse(GadgetItemPredicates.CAR.test(stack));
		assertFalse(GadgetItemPredicates.JETPACK.test(stack));
	}

	@Test
	@DisplayName("buildItem() stamps GRAPPLE_ID and carries no fuel NBT tags (cooldown only, WS8-D1)")
	void buildItem_stampsIdentityOnly_noFuelTags() {
		Grapple grapple = Grapple.builder()
		                         .grappleId("grapple")
		                         .displayName("&bGrappling Hook")
		                         .material(Material.FISHING_ROD)
		                         .build();

		ItemStack stack = grapple.buildItem();

		assertTrue(Grapple.isGrappleItem(stack));
		assertEquals("grapple", Grapple.getGrappleId(stack));
	}
}
