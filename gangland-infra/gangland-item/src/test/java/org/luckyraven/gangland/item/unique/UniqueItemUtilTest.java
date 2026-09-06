package org.luckyraven.gangland.item.unique;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.item.support.PerStackNbtAccessor;
import org.luckyraven.keystone.item.nbt.NbtBridge;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link UniqueItemUtil}, routed through an installed {@link PerStackNbtAccessor} so tag
 * reads/writes are asserted with no NBT provider on the classpath (per {@code documentation/TESTING.md}
 * section 4).
 *
 * <p>Regression net for IT-01 (Observation #1, items-unique.md). {@code hasUniqueItem} used to do
 * {@code if (uniqueItem.compareTo(item) == 0) continue;} — it <em>skipped</em> the item that actually
 * matched and returned {@code true} only when the inventory held a <em>different</em> unique item of the
 * same material. Combined with {@code Allow_Duplicates: false} at the call site
 * ({@code LoadUniqueItem:46,89}), the duplicate guard never fired and unique items multiplied on every join.
 * Identity now comes from the {@code uniqueItem} NBT tag via {@code UniqueItem.matches}.
 *
 * <p>{@link PerStackNbtAccessor} rather than testkit's {@code RecordingNbtAccessor} is required here: the
 * latter keys tags by name only, so two stacks could never carry different unique-item keys.
 *
 * <p>{@code buildItem(Player)} is deliberately never invoked here — it needs a live {@code ItemFactory} to
 * back {@code ItemStack#getItemMeta()}. Every {@link ItemStack} below is a hand-stubbed Mockito mock
 * instead; {@code UniqueItemIdentityIntegrationTest} covers the real-stack path.
 */
@DisplayName("UniqueItemUtil — unique-item identity and inventory scanning")
class UniqueItemUtilTest {

	private PerStackNbtAccessor accessor;

	@BeforeEach
	void installNbt() {
		accessor = new PerStackNbtAccessor();
		NbtBridge.install(accessor);
	}

	@AfterEach
	void resetNbt() {
		NbtBridge.reset();
	}

	@Test
	@DisplayName("isUniqueItem is false for null and for AIR without even checking NBT")
	void isUniqueItem_nullOrAir_isFalse() {
		assertFalse(UniqueItemUtil.isUniqueItem(null));

		ItemStack air = mock(ItemStack.class);
		when(air.getType()).thenReturn(Material.AIR);
		assertFalse(UniqueItemUtil.isUniqueItem(air));
	}

	@Test
	@DisplayName("isUniqueItem reads the 'uniqueItem' NBT tag through ItemBuilder/NbtBridge")
	void isUniqueItem_readsNbtTag() {
		ItemStack stack = mock(ItemStack.class);
		when(stack.getType()).thenReturn(Material.IRON_INGOT);

		assertFalse(UniqueItemUtil.isUniqueItem(stack));

		accessor.put(stack, UniqueItemKeys.UNIQUE_ITEM_KEY, "phone");
		assertTrue(UniqueItemUtil.isUniqueItem(stack));
	}

	@Test
	@DisplayName("getUniqueItemKey returns null for a non-unique stack and the tag value for a unique one")
	void getUniqueItemKey_returnsTagOrNull() {
		ItemStack stack = mock(ItemStack.class);
		when(stack.getType()).thenReturn(Material.IRON_INGOT);

		assertNull(UniqueItemUtil.getUniqueItemKey(stack));

		accessor.put(stack, UniqueItemKeys.UNIQUE_ITEM_KEY, "phone");
		assertEquals("phone", UniqueItemUtil.getUniqueItemKey(stack));
	}

	@Test
	@DisplayName("IT-01: an inventory holding the target item reports hasUniqueItem == true")
	void hasUniqueItem_matchingItemPresent_reportsTrue() {
		UniqueItem phone = phone();

		ItemStack matchingStack = taggedStack(Material.IRON_INGOT, "phone");

		Player player = playerWithInventory(matchingStack);

		assertTrue(UniqueItemUtil.hasUniqueItem(player, phone),
		           "the genuinely matching item must be found — this is the duplicate guard");
	}

	@Test
	@DisplayName("IT-01: a DIFFERENT unique item of the same material does not report hasUniqueItem == true")
	void hasUniqueItem_differentUniqueItemSameMaterial_reportsFalse() {
		UniqueItem phone = phone();

		// A different unique item ("lockpick"), same material — must not satisfy the phone's duplicate guard.
		ItemStack otherUniqueStack = taggedStack(Material.IRON_INGOT, "lockpick");

		Player player = playerWithInventory(otherUniqueStack);

		assertFalse(UniqueItemUtil.hasUniqueItem(player, phone),
		            "identity is the uniqueItem NBT tag, so another unique item of the same material is not a match");
	}

	@Test
	@DisplayName("hasUniqueItem finds the target item among unrelated inventory contents")
	void hasUniqueItem_findsTargetAmongOtherContents() {
		UniqueItem phone = phone();

		ItemStack wrongMaterial = mock(ItemStack.class);
		when(wrongMaterial.getType()).thenReturn(Material.DIRT);

		ItemStack plainIron = mock(ItemStack.class);
		when(plainIron.getType()).thenReturn(Material.IRON_INGOT);

		ItemStack lockpick = taggedStack(Material.IRON_INGOT, "lockpick");
		ItemStack thePhone = taggedStack(Material.IRON_INGOT, "phone");

		Player player = playerWithInventory(null, wrongMaterial, plainIron, lockpick, thePhone);

		assertTrue(UniqueItemUtil.hasUniqueItem(player, phone));
	}

	@Test
	@DisplayName("hasUniqueItem ignores null slots, wrong-material items, and non-unique items of the right material")
	void hasUniqueItem_ignoresNonMatchingSlots() {
		UniqueItem phone = phone();

		ItemStack wrongMaterial = mock(ItemStack.class);
		when(wrongMaterial.getType()).thenReturn(Material.DIRT);

		ItemStack plainIron = mock(ItemStack.class); // right material, but not tagged as any unique item
		when(plainIron.getType()).thenReturn(Material.IRON_INGOT);

		Player player = playerWithInventory(null, wrongMaterial, plainIron);

		assertFalse(UniqueItemUtil.hasUniqueItem(player, phone));
	}

	@Test
	@DisplayName("hasUniqueItem does not match the right key stamped on the wrong material")
	void hasUniqueItem_rightKeyWrongMaterial_reportsFalse() {
		UniqueItem phone = phone();

		ItemStack wrongMaterial = taggedStack(Material.GOLD_INGOT, "phone");

		Player player = playerWithInventory(wrongMaterial);

		assertFalse(UniqueItemUtil.hasUniqueItem(player, phone));
	}

	// -------------------------------------------------------------- fixtures

	private static UniqueItem phone() {
		return UniqueItem.builder()
				.uniqueItem("phone").material(Material.IRON_INGOT).customModelData(0)
				.name("&6Phone")
				.addOnJoin(true).addOnRespawn(false).dropOnDeath(false).allowDuplicates(false).addToInventory(true)
				.build();
	}

	private ItemStack taggedStack(Material material, String uniqueKey) {
		ItemStack stack = mock(ItemStack.class);
		when(stack.getType()).thenReturn(material);
		accessor.put(stack, UniqueItemKeys.UNIQUE_ITEM_KEY, uniqueKey);
		return stack;
	}

	private Player playerWithInventory(ItemStack... contents) {
		Player          player    = mock(Player.class);
		PlayerInventory inventory = mock(PlayerInventory.class);
		when(player.getInventory()).thenReturn(inventory);
		when(inventory.getContents()).thenReturn(contents);
		return player;
	}

}
