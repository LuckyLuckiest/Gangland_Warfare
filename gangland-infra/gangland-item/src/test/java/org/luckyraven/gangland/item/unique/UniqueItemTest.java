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
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link UniqueItem} that do not require {@code buildItem(Player)} to run (that call needs a live
 * {@code ItemFactory} — see {@code UniqueItemUtilTest} javadoc for why that path is out of unit-test reach here).
 *
 * <p>Pins Observation #3 (items-unique.md): {@code compareTo} compares the raw, unresolved config {@code name}
 * (still carrying {@code &}-codes) against the built item's colour-translated {@code meta.getDisplayName()} — so it
 * never reports a match against a genuinely built stack, which is the root cause behind Observations #1 and #2 in
 * {@link UniqueItemUtilTest} and {@code LoadUniqueItemTest}.
 *
 * <p>Also pins Observation #21: {@code addItemToInventory} returns {@code !addItem(...)}, i.e. {@code false} on
 * success and {@code true} on failure — inverted from what the name implies.
 */
@DisplayName("UniqueItem — identity comparison and inventory placement")
class UniqueItemTest {

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

	private static UniqueItem.UniqueItemBuilder baseBuilder() {
		return UniqueItem.builder()
				.uniqueItem("phone")
				.material(Material.IRON_INGOT)
				.customModelData(0)
				.name("&6Phone")
				.addOnJoin(true)
				.addOnRespawn(false)
				.dropOnDeath(false)
				.allowDuplicates(false)
				.addToInventory(true);
	}

	@Test
	@DisplayName("getPermission derives 'gangland.uniqueitem.<key>' from the registry key")
	void getPermission_derivesFromKey() {
		UniqueItem uniqueItem = baseBuilder().build();

		assertEquals("gangland.uniqueitem.phone", uniqueItem.getPermission());
	}

	@Test
	@DisplayName("compareTo returns 0 immediately when the stack has no ItemMeta")
	void compareTo_noMeta_returnsZero() {
		UniqueItem uniqueItem = baseBuilder().build();
		ItemStack  stack      = mock(ItemStack.class);
		when(stack.getItemMeta()).thenReturn(null);

		assertEquals(0, uniqueItem.compareTo(stack));
	}

	@Test
	@DisplayName("compareTo returns 0 when the stack is not tagged as a unique item at all (not just 'no match')")
	void compareTo_notUniqueItem_returnsZero() {
		UniqueItem uniqueItem = baseBuilder().build();
		ItemStack  stack      = mock(ItemStack.class);
		ItemMeta   meta       = mock(ItemMeta.class);
		when(stack.getItemMeta()).thenReturn(meta);
		when(stack.getType()).thenReturn(Material.IRON_INGOT);
		// RecordingNbtAccessor has no "uniqueItem" tag recorded, so isUniqueItem(stack) is false.

		assertEquals(0, uniqueItem.compareTo(stack),
		             "0 here means 'not comparable', but callers cannot distinguish this from a genuine match");
	}

	@Test
	@DisplayName("Observation #3: compareTo never matches a genuinely built stack, because it compares the raw "
			+ "&-coded config name against the colour-translated display name")
	void compareTo_rawNameVsColourTranslatedName_neverMatches() {
		UniqueItem uniqueItem = baseBuilder().name("&6Phone").build();

		ItemStack stack = mock(ItemStack.class);
		ItemMeta  meta  = mock(ItemMeta.class);
		when(stack.getItemMeta()).thenReturn(meta);
		when(stack.getType()).thenReturn(Material.IRON_INGOT);
		// Exactly what UniqueItem.buildItem would have produced via ItemBuilder.setDisplayName -> ChatUtil.color.
		when(meta.getDisplayName()).thenReturn(ChatUtil.color("&6Phone"));
		tagAsUniqueItem("phone");

		assertNotEquals(0, uniqueItem.compareTo(stack),
		                "raw '&6Phone' can never equal its own colour-translated '§6Phone'");
	}

	@Test
	@DisplayName("compareTo does return 0 for a stack whose display name is byte-for-byte identical to the raw "
			+ "config name and whose material matches — the one case where the identity check actually works")
	void compareTo_identicalRawNameAndMaterial_returnsZero() {
		UniqueItem uniqueItem = baseBuilder().name("Phone").build(); // no colour codes to translate

		ItemStack stack = mock(ItemStack.class);
		ItemMeta  meta  = mock(ItemMeta.class);
		when(stack.getItemMeta()).thenReturn(meta);
		when(stack.getType()).thenReturn(Material.IRON_INGOT);
		when(meta.getDisplayName()).thenReturn("Phone");
		tagAsUniqueItem("phone");

		assertEquals(0, uniqueItem.compareTo(stack));
	}

	@Test
	@DisplayName("Observation #21: addItemToInventory returns false immediately when Slot: -1 (addToInventory=false)")
	void addItemToInventory_addToInventoryFalse_returnsFalse() {
		UniqueItem uniqueItem = baseBuilder().addToInventory(false).build();

		assertFalse(uniqueItem.addItemToInventory(mock(Player.class)));
	}

	@Test
	@DisplayName("Observation #21: when every candidate slot is occupied and Overrides=false, addItem() fails to "
			+ "place the item, yet addItemToInventory() reports success (true) because it returns !addItem(...)")
	void addItemToInventory_everySlotOccupied_reportsSuccessOnFailure() {
		UniqueItem uniqueItem = baseBuilder().inventorySlot(30).overridesSlot(false).build();

		Player          player    = mock(Player.class);
		PlayerInventory inventory = mock(PlayerInventory.class);
		when(player.getInventory()).thenReturn(inventory);
		when(inventory.getSize()).thenReturn(36);
		// Every slot from 30 to 35 (the addItem loop's hard cap) is occupied, and overridesSlot is false, so the
		// recursive search runs off the end (slot 36 >= size) and addItem(...) returns false.
		for (int slot = 30; slot <= 35; slot++) {
			when(inventory.getItem(slot)).thenReturn(mock(ItemStack.class));
		}

		boolean reportedSuccess = uniqueItem.addItemToInventory(player);

		assertTrue(reportedSuccess, "addItemToInventory inverts addItem's result: a real failure to place reports "
				+ "as 'true' to any caller that reads it naively");
		verify(inventory, never()).setItem(anyInt(), any(ItemStack.class));
	}

	private void tagAsUniqueItem(String key) {
		// isUniqueItem/getStringTagData read through the installed RecordingNbtAccessor, keyed purely by tag name
		// (the accessor's map is not per-stack).
		accessor.values.put(UniqueItemKeys.UNIQUE_ITEM_KEY, key);
	}

}
