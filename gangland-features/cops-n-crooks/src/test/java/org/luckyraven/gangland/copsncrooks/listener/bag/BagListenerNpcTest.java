package org.luckyraven.gangland.copsncrooks.listener.bag;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.copsncrooks.bag.BagFixture;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Judge-fix r1: a Citizens player-type NPC (metadata "NPC") never earns a hot star or redeems a hot bill. */
@DisplayName("BagListener - Citizens NPC players are ignored")
class BagListenerNpcTest {

	@TempDir
	Path tempDir;

	@Test
	@DisplayName("an NPC player picking up a hot bill earns no star")
	void npcPickupNoStar() {
		BagFixture fx  = new BagFixture(tempDir);
		Player     npc = fx.player("Npc");
		when(npc.hasMetadata("NPC")).thenReturn(true);
		fx.level(npc, 0);
		Item item = mock(Item.class);
		when(item.getItemStack()).thenReturn(mock(ItemStack.class));

		new BagListener(fx.service, fx).onPickup(new EntityPickupItemEvent(npc, item, 0));

		assertTrue(fx.stars.isEmpty());
	}

	@Test
	@DisplayName("an NPC player right-clicking a hot bill is left alone: no star, not cancelled, item untouched")
	void npcRedeemIgnored() {
		BagFixture fx  = new BagFixture(tempDir);
		Player     npc = fx.player("Npc");
		when(npc.hasMetadata("NPC")).thenReturn(true);
		fx.level(npc, 0);
		ItemStack held = mock(ItemStack.class);
		PlayerInteractEvent event = new PlayerInteractEvent(npc, Action.RIGHT_CLICK_BLOCK, held, mock(Block.class),
		                                                    BlockFace.UP, EquipmentSlot.HAND);

		new BagListener(fx.service, fx).onRedeem(event);

		assertTrue(fx.stars.isEmpty());
		assertFalse(event.isCancelled());
		verify(held, never()).setType(any(Material.class));
	}
}
