package org.luckyraven.gangland.item.unique;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.keystone.item.nbt.NbtBridge;
import org.luckyraven.keystone.testkit.RecordingNbtAccessor;
import org.luckyraven.keystone.util.ChatUtil;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link UniqueItemUtil}, routed through an installed {@link RecordingNbtAccessor} so tag reads/
 * writes are asserted with no NBT provider on the classpath (per {@code documentation/TESTING.md} section 4).
 *
 * <p>Pins Observation #1 (items-unique.md), the highest-value regression test in this area: {@code hasUniqueItem}
 * does {@code if (uniqueItem.compareTo(item) == 0) continue;} — it <em>skips</em> the item that actually matches
 * and returns {@code true} only when the inventory holds a <em>different</em> unique item of the same material.
 * Combined with {@code Allow_Duplicates: false} at the call site ({@code LoadUniqueItem:46,89}), this means the
 * duplicate guard never fires for a genuine duplicate.
 *
 * <p>{@code buildItem(Player)} is deliberately never invoked here — it needs a live {@code ItemFactory} to back
 * {@code ItemStack#getItemMeta()}, which is not available without a running server or MockBukkit (neither is wired
 * into this repo's testkit). Every {@link ItemStack} below is a hand-stubbed Mockito mock instead, exactly as
 * {@code UniqueItemTest} does for {@code compareTo}.
 */
@DisplayName("UniqueItemUtil — unique-item identity and inventory scanning")
class UniqueItemUtilTest {

	private RecordingNbtAccessor accessor;

	@BeforeEach
	void installNbt() {
		accessor = new RecordingNbtAccessor();
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

		accessor.values.put(UniqueItemKeys.UNIQUE_ITEM_KEY, "phone");
		assertTrue(UniqueItemUtil.isUniqueItem(stack));
	}

	@Test
	@DisplayName("getUniqueItemKey returns null for a non-unique stack and the tag value for a unique one")
	void getUniqueItemKey_returnsTagOrNull() {
		ItemStack stack = mock(ItemStack.class);
		when(stack.getType()).thenReturn(Material.IRON_INGOT);

		assertNull(UniqueItemUtil.getUniqueItemKey(stack));

		accessor.values.put(UniqueItemKeys.UNIQUE_ITEM_KEY, "phone");
		assertEquals("phone", UniqueItemUtil.getUniqueItemKey(stack));
	}

	@Test
	@DisplayName("Observation #1: an inventory holding the target item itself (and nothing else) reports "
			+ "hasUniqueItem == false")
	void hasUniqueItem_onlyTheMatchingItemPresent_reportsFalse() {
		UniqueItem uniqueItem = UniqueItem.builder()
				.uniqueItem("phone").material(Material.IRON_INGOT).customModelData(0)
				.name("Phone") // no colour codes, so compareTo's name check can genuinely succeed
				.addOnJoin(true).addOnRespawn(false).dropOnDeath(false).allowDuplicates(false).addToInventory(true)
				.build();

		ItemStack matchingStack = mockUniqueStack(Material.IRON_INGOT, "Phone", "phone");

		Player player = playerWithInventory(matchingStack);

		assertFalse(UniqueItemUtil.hasUniqueItem(player, uniqueItem),
		            "the one genuinely matching item is skipped by the inverted 'continue' branch");
	}

	@Test
	@DisplayName("Observation #1: a DIFFERENT unique item of the same material makes hasUniqueItem report true, "
			+ "even though the inventory does not contain the target item at all")
	void hasUniqueItem_differentUniqueItemSameMaterial_reportsTrue() {
		UniqueItem phone = UniqueItem.builder()
				.uniqueItem("phone").material(Material.IRON_INGOT).customModelData(0)
				.name("Phone")
				.addOnJoin(true).addOnRespawn(false).dropOnDeath(false).allowDuplicates(false).addToInventory(true)
				.build();

		// A different unique item ("lockpick"), same material, non-matching display name.
		ItemStack otherUniqueStack = mockUniqueStack(Material.IRON_INGOT, "Lockpick", "lockpick");

		Player player = playerWithInventory(otherUniqueStack);

		assertTrue(UniqueItemUtil.hasUniqueItem(player, phone),
		           "the predicate is inverted: it reports true for ANY other unique item of the same material");
	}

	@Test
	@DisplayName("hasUniqueItem ignores null slots, wrong-material items, and non-unique items of the right material")
	void hasUniqueItem_ignoresNonMatchingSlots() {
		UniqueItem phone = UniqueItem.builder()
				.uniqueItem("phone").material(Material.IRON_INGOT).customModelData(0)
				.name("Phone")
				.addOnJoin(true).addOnRespawn(false).dropOnDeath(false).allowDuplicates(false).addToInventory(true)
				.build();

		ItemStack wrongMaterial = mock(ItemStack.class);
		when(wrongMaterial.getType()).thenReturn(Material.DIRT);

		ItemStack plainIron = mock(ItemStack.class); // right material, but not tagged as any unique item
		when(plainIron.getType()).thenReturn(Material.IRON_INGOT);

		Player player = playerWithInventory(null, wrongMaterial, plainIron);

		assertFalse(UniqueItemUtil.hasUniqueItem(player, phone));
	}

	// -------------------------------------------------------------- fixtures

	private ItemStack mockUniqueStack(Material material, String rawDisplayName, String uniqueKey) {
		ItemStack stack = mock(ItemStack.class);
		ItemMeta  meta  = mock(ItemMeta.class);
		when(stack.getType()).thenReturn(material);
		when(stack.getItemMeta()).thenReturn(meta);
		when(meta.getDisplayName()).thenReturn(ChatUtil.color(rawDisplayName));
		// The shared accessor map is not per-stack; every "unique" stack in these tests reads the same tag value,
		// which is exactly why hasUniqueItem cannot tell two different unique items apart by NBT alone here — the
		// discriminator under test is compareTo's display-name comparison, not the tag.
		accessor.values.put(UniqueItemKeys.UNIQUE_ITEM_KEY, uniqueKey);
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
