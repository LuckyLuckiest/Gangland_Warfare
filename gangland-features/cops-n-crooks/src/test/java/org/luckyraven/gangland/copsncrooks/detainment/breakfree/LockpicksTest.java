package org.luckyraven.gangland.copsncrooks.detainment.breakfree;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.item.unique.UniqueItemUtil;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 0.21.0 lockpick lane: finding and consuming the {@code lockpick} unique item. The NBT read is mocked at
 * {@link UniqueItemUtil#getUniqueItemKey(ItemStack)}.
 */
@DisplayName("Lockpicks")
class LockpicksTest {

	private MockedStatic<UniqueItemUtil> util;
	private Player                       player;
	private PlayerInventory              inventory;

	@BeforeEach
	void setUp() {
		util      = mockStatic(UniqueItemUtil.class);
		player    = mock(Player.class);
		inventory = mock(PlayerInventory.class);
		when(player.getInventory()).thenReturn(inventory);
	}

	@AfterEach
	void tearDown() {
		util.close();
	}

	private ItemStack stack(String key, int amount) {
		ItemStack item = mock(ItemStack.class);
		when(item.getAmount()).thenReturn(amount);
		when(item.getType()).thenReturn(Material.TRIPWIRE_HOOK);
		util.when(() -> UniqueItemUtil.getUniqueItemKey(item)).thenReturn(key);
		return item;
	}

	@Test
	@DisplayName("the item key is lockpick")
	void itemKey() {
		assertEquals("lockpick", Lockpicks.ITEM_KEY);
	}

	@Test
	@DisplayName("a lockpick-tagged stack is found; a plain tripwire hook is not")
	void findsByUniqueKey() {
		ItemStack plain = stack(null, 1);
		ItemStack pick  = stack("lockpick", 1);

		when(inventory.getContents()).thenReturn(new ItemStack[]{plain, null, pick});
		assertTrue(Lockpicks.has(player));

		when(inventory.getContents()).thenReturn(new ItemStack[]{plain, null});
		assertFalse(Lockpicks.has(player));
	}

	@Test
	@DisplayName("the key match ignores case")
	void keyIgnoresCase() {
		ItemStack pick = stack("LockPick", 1);
		when(inventory.getContents()).thenReturn(new ItemStack[]{pick});
		assertTrue(Lockpicks.has(player));
	}

	@Test
	@DisplayName("another unique item is not a lockpick")
	void otherUniqueItem_isNotAPick() {
		ItemStack other = stack("crowbar", 1);
		when(inventory.getContents()).thenReturn(new ItemStack[]{other});
		assertFalse(Lockpicks.has(player));
	}

	@Test
	@DisplayName("null player, null inventory contents and an empty inventory are safe")
	void nullSafe() {
		assertFalse(Lockpicks.has(null));
		assertFalse(Lockpicks.consumeOne(null));

		when(inventory.getContents()).thenReturn(null);
		assertFalse(Lockpicks.has(player));
		assertFalse(Lockpicks.consumeOne(player));

		when(inventory.getContents()).thenReturn(new ItemStack[0]);
		assertFalse(Lockpicks.has(player));
		assertFalse(Lockpicks.consumeOne(player));
	}

	@Test
	@DisplayName("consuming from a stack of 2 leaves 1")
	void consumeDecrements() {
		ItemStack pick = stack("lockpick", 2);
		when(inventory.getContents()).thenReturn(new ItemStack[]{null, pick});

		assertTrue(Lockpicks.consumeOne(player));

		verify(pick).setAmount(1);
		verify(inventory).setItem(1, pick);
	}

	@Test
	@DisplayName("consuming the last pick clears its slot")
	void consumeLast_clearsSlot() {
		ItemStack pick = stack("lockpick", 1);
		when(inventory.getContents()).thenReturn(new ItemStack[]{null, null, pick});

		assertTrue(Lockpicks.consumeOne(player));

		verify(inventory).setItem(2, null);
		verify(pick, never()).setAmount(0);
	}

	@Test
	@DisplayName("only the first pick stack is touched")
	void consume_takesFromFirstStackOnly() {
		ItemStack first  = stack("lockpick", 1);
		ItemStack second = stack("lockpick", 5);
		when(inventory.getContents()).thenReturn(new ItemStack[]{first, second});

		assertTrue(Lockpicks.consumeOne(player));

		verify(inventory).setItem(0, null);
		verify(second, never()).setAmount(4);
	}

	@Test
	@DisplayName("consuming with no pick returns false and changes nothing")
	void consume_noPick_false() {
		ItemStack plain = stack(null, 3);
		when(inventory.getContents()).thenReturn(new ItemStack[]{plain});

		assertFalse(Lockpicks.consumeOne(player));

		verify(inventory, never()).setItem(0, null);
		verify(plain, never()).setAmount(2);
	}
}
