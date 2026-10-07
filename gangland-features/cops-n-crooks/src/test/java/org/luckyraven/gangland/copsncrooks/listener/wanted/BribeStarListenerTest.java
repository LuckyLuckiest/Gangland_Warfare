package org.luckyraven.gangland.copsncrooks.listener.wanted;

import org.bukkit.entity.Item;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.inventory.Inventory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.wanted.bribe.BribeStars;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("BribeStarListener: hoppers never collect a bribe star")
class BribeStarListenerTest {

	private final BribeStars        stars    = mock(BribeStars.class);
	private final BribeStarListener listener = new BribeStarListener(stars);

	@Test
	@DisplayName("a hopper (or hopper minecart) pulling the star is cancelled")
	void hopperPickingTheStar_isCancelled() {
		Item star = mock(Item.class);
		when(stars.isStar(star)).thenReturn(true);
		InventoryPickupItemEvent event = new InventoryPickupItemEvent(mock(Inventory.class), star);

		listener.onHopperPickup(event);

		assertTrue(event.isCancelled());
	}

	@Test
	@DisplayName("any other item is left to the hopper")
	void otherItem_isLeftAlone() {
		InventoryPickupItemEvent event = new InventoryPickupItemEvent(mock(Inventory.class), mock(Item.class));

		listener.onHopperPickup(event);

		assertFalse(event.isCancelled());
	}
}
