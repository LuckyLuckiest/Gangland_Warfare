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
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link UniqueItem} that do not require {@code buildItem(Player)} to run (that call needs a live
 * {@code ItemFactory}; {@code UniqueItemIdentityIntegrationTest} covers that path with a real server registry).
 *
 * <p>Regression net for IT-03 (Observation #3, items-unique.md). {@code compareTo} used to compare the raw,
 * unresolved config {@code name} (still carrying {@code &}-codes) against the built item's colour-translated
 * {@code meta.getDisplayName()}, so it never reported a match against a genuinely built stack — the root
 * cause behind IT-01 and IT-02. It also returned {@code 0} ("equal") for stacks with no meta and for stacks
 * that were not unique items at all, which made every caller treating {@code 0} as "this is my item" act on
 * unrelated inventory contents.
 *
 * <p>Identity is now {@link UniqueItem#matches(ItemStack)} — the {@code uniqueItem} NBT tag plus the
 * material — and {@code compareTo(stack) == 0} is exactly that predicate.
 *
 * <p>Also pins Observation #21 (IT-21, still open): {@code addItemToInventory} returns {@code !addItem(...)},
 * i.e. {@code false} on success and {@code true} on failure — inverted from what the name implies.
 */
@DisplayName("UniqueItem — identity comparison and inventory placement")
class UniqueItemTest {

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

	// -------------------------------------------------------------- matches

	@Test
	@DisplayName("matches is false for a null stack")
	void matches_nullStack_isFalse() {
		assertFalse(baseBuilder().build().matches(null));
	}

	@Test
	@DisplayName("matches is true for a stack carrying this item's key on this item's material")
	void matches_sameKeyAndMaterial_isTrue() {
		UniqueItem uniqueItem = baseBuilder().build();
		ItemStack  stack      = taggedStack(Material.IRON_INGOT, "phone");

		assertTrue(uniqueItem.matches(stack));
	}

	@Test
	@DisplayName("matches is false for another unique item's key on the same material")
	void matches_differentKey_isFalse() {
		UniqueItem uniqueItem = baseBuilder().build();
		ItemStack  stack      = taggedStack(Material.IRON_INGOT, "lockpick");

		assertFalse(uniqueItem.matches(stack));
	}

	@Test
	@DisplayName("matches is false for this item's key stamped on the wrong material")
	void matches_wrongMaterial_isFalse() {
		UniqueItem uniqueItem = baseBuilder().build();
		ItemStack  stack      = taggedStack(Material.GOLD_INGOT, "phone");

		assertFalse(uniqueItem.matches(stack));
	}

	@Test
	@DisplayName("matches is false for an untagged stack of the right material")
	void matches_untaggedStack_isFalse() {
		UniqueItem uniqueItem = baseBuilder().build();
		ItemStack  stack      = mock(ItemStack.class);
		when(stack.getType()).thenReturn(Material.IRON_INGOT);

		assertFalse(uniqueItem.matches(stack));
	}

	// -------------------------------------------------------------- compareTo

	@Test
	@DisplayName("IT-03: compareTo is non-zero for a stack that is not a unique item, so callers that treat 0 as "
			+ "a match never act on plain inventory contents")
	void compareTo_notUniqueItem_isNonZero() {
		UniqueItem uniqueItem = baseBuilder().build();
		ItemStack  stack      = mock(ItemStack.class);
		when(stack.getType()).thenReturn(Material.IRON_INGOT);

		assertNotEquals(0, uniqueItem.compareTo(stack));
	}

	@Test
	@DisplayName("IT-03: compareTo is non-zero for a stack with no ItemMeta — identity no longer reads meta at all")
	void compareTo_noMeta_isNonZero() {
		UniqueItem uniqueItem = baseBuilder().build();
		ItemStack  stack      = mock(ItemStack.class);
		when(stack.getItemMeta()).thenReturn(null);

		assertNotEquals(0, uniqueItem.compareTo(stack));
	}

	@Test
	@DisplayName("IT-03: compareTo matches a genuinely built stack whose display name is colour-translated, "
			+ "because identity is the uniqueItem NBT tag and not the display name")
	void compareTo_rawNameVsColourTranslatedName_matches() {
		UniqueItem uniqueItem = baseBuilder().name("&6Phone").build();

		// The stack a real buildItem() would produce: display name translated to §6Phone, tag "phone" stamped.
		ItemStack stack = taggedStack(Material.IRON_INGOT, "phone");

		assertEquals(0, uniqueItem.compareTo(stack),
		             "the raw '&6Phone' vs rendered '§6Phone' mismatch must no longer defeat identity");
	}

	@Test
	@DisplayName("compareTo is non-zero for a different unique item, and orders by registry key")
	void compareTo_differentKey_ordersByKey() {
		UniqueItem uniqueItem = baseBuilder().build(); // key "phone"
		ItemStack  lockpick   = taggedStack(Material.IRON_INGOT, "lockpick");

		assertTrue(uniqueItem.compareTo(lockpick) > 0, "'phone' sorts after 'lockpick'");
	}

	@Test
	@DisplayName("compareTo == 0 agrees with matches for every fixture")
	void compareTo_agreesWithMatches() {
		UniqueItem uniqueItem = baseBuilder().build();

		ItemStack match        = taggedStack(Material.IRON_INGOT, "phone");
		ItemStack otherKey     = taggedStack(Material.IRON_INGOT, "lockpick");
		ItemStack otherMateria = taggedStack(Material.GOLD_INGOT, "phone");

		for (ItemStack stack : new ItemStack[]{match, otherKey, otherMateria}) {
			assertEquals(uniqueItem.matches(stack), uniqueItem.compareTo(stack) == 0,
			             "compareTo(x) == 0 must be exactly matches(x)");
		}
	}

	// -------------------------------------------------------------- addItemToInventory (IT-21, still open)

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

	// -------------------------------------------------------------- fixtures

	private ItemStack taggedStack(Material material, String uniqueKey) {
		ItemStack stack = mock(ItemStack.class);
		when(stack.getType()).thenReturn(material);
		accessor.put(stack, UniqueItemKeys.UNIQUE_ITEM_KEY, uniqueKey);
		return stack;
	}

}
