package org.luckyraven.gangland.lootchest;

import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.luckyraven.keystone.util.ChatUtil;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

/**
 * Pins the size normalisation of {@link SharedLootInventory}: the requested size is rounded up to a multiple of 9 and
 * capped at 54, and the same value is both exposed by {@code getSize()} and handed to {@code Bukkit.createInventory}.
 */
class SharedLootInventoryTest {

	private MockedStatic<Bukkit> bukkit;
	private Inventory            created;

	@BeforeEach
	void setUp() {
		created = mock(Inventory.class);
		bukkit  = mockStatic(Bukkit.class);
		bukkit.when(() -> Bukkit.createInventory(any(InventoryHolder.class), anyInt(), anyString()))
		      .thenReturn(created);
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
	}

	@ParameterizedTest(name = "requested {0} slots gives {1}")
	@DisplayName("the size is rounded up to a multiple of 9 and capped at 54, and getSize reports the normalised value")
	@CsvSource({
			"0,0", "1,9", "9,9", "10,18", "27,27", "28,36", "53,54", "54,54", "55,54", "60,54", "-3,0"
	})
	void getSize_isTheNormalisedSize(int requested, int expected) {
		SharedLootInventory inventory = new SharedLootInventory("Title", requested);

		assertEquals(expected, inventory.getSize());
	}

	@Test
	@DisplayName("the Bukkit inventory is created with this holder, the normalised size and the coloured title")
	void constructor_createsTheInventoryWithTheNormalisedSize() {
		SharedLootInventory inventory = new SharedLootInventory("&aLoot", 10);

		bukkit.verify(() -> Bukkit.createInventory(inventory, 18, ChatUtil.color("&aLoot")));
		assertSame(created, inventory.getInventory());
	}

	@Test
	@DisplayName("setItem writes through to the wrapped inventory")
	void setItem_writesThrough() {
		SharedLootInventory inventory = new SharedLootInventory("Title", 9);
		ItemStack item = mock(ItemStack.class);

		inventory.setItem(4, item);

		verify(created).setItem(4, item);
	}
}
