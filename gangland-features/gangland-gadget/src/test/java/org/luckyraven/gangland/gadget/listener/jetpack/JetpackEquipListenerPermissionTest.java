package org.luckyraven.gangland.gadget.listener.jetpack;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.testsupport.BukkitRegistryFixture;
import org.luckyraven.gangland.gadget.jetpack.Jetpack;
import org.luckyraven.gangland.gadget.jetpack.JetpackKey;
import org.luckyraven.gangland.gadget.jetpack.JetpackService;
import org.luckyraven.gangland.gadget.jetpack.config.JetpackAddon;
import org.luckyraven.gangland.gadget.jetpack.message.JetpackMessages;
import org.luckyraven.keystone.item.ItemBuilder;
import org.luckyraven.keystone.item.nbt.NbtBridge;
import org.luckyraven.keystone.testkit.RecordingNbtAccessor;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins WS7 G4 review I3: the per-item permission denial message fires from {@link JetpackEquipListener}'s
 * deliberate-interaction paths — {@code JetpackService.activate} (reached by join/dismount/undown too) stays
 * silent (see {@code JetpackServiceActivatePermissionTest}).
 */
@DisplayName("JetpackEquipListener — permission denial message (WS7 G4 review I3)")
class JetpackEquipListenerPermissionTest {

	@BeforeAll
	static void bootstrapBukkitRegistry() {
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

	private static ItemStack jetpackItem(String id) {
		ItemStack stack = new ItemStack(Material.IRON_CHESTPLATE);
		new ItemBuilder(stack).addTag(JetpackKey.JETPACK_ID.getKey(), id);
		return stack;
	}

	@Test
	@DisplayName("onInteract sends the denial message when the player lacks the jetpack's permission")
	void onInteract_missingPermission_sendsMessage() {
		JetpackService  jetpackService = mock(JetpackService.class);
		JetpackAddon    jetpackAddon   = mock(JetpackAddon.class);
		JetpackMessages messages       = mock(JetpackMessages.class);

		Jetpack jetpack = Jetpack.builder().jetpackId("jetpack").build();
		when(jetpackAddon.getJetpack("jetpack")).thenReturn(jetpack);
		when(messages.noPermission()).thenReturn("no-permission-message");

		JetpackEquipListener listener = new JetpackEquipListener(jetpackService, jetpackAddon, messages);

		Player player = mock(Player.class);
		when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
		when(player.hasPermission(jetpack.getPermission())).thenReturn(false);

		PlayerInteractEvent event = mock(PlayerInteractEvent.class);
		when(event.getHand()).thenReturn(EquipmentSlot.HAND);
		when(event.getAction()).thenReturn(Action.RIGHT_CLICK_AIR);
		when(event.getPlayer()).thenReturn(player);
		when(event.getItem()).thenReturn(jetpackItem("jetpack"));

		listener.onInteract(event);

		verify(player).sendMessage("no-permission-message");
		verify(jetpackService).scheduleChestplateCheck(player);
	}

	@Test
	@DisplayName("onInteract sends nothing when the player has permission")
	void onInteract_hasPermission_noMessage() {
		JetpackService  jetpackService = mock(JetpackService.class);
		JetpackAddon    jetpackAddon   = mock(JetpackAddon.class);
		JetpackMessages messages       = mock(JetpackMessages.class);

		Jetpack jetpack = Jetpack.builder().jetpackId("jetpack").build();
		when(jetpackAddon.getJetpack("jetpack")).thenReturn(jetpack);

		JetpackEquipListener listener = new JetpackEquipListener(jetpackService, jetpackAddon, messages);

		Player player = mock(Player.class);
		when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
		when(player.hasPermission(jetpack.getPermission())).thenReturn(true);

		PlayerInteractEvent event = mock(PlayerInteractEvent.class);
		when(event.getHand()).thenReturn(EquipmentSlot.HAND);
		when(event.getAction()).thenReturn(Action.RIGHT_CLICK_AIR);
		when(event.getPlayer()).thenReturn(player);
		when(event.getItem()).thenReturn(jetpackItem("jetpack"));

		listener.onInteract(event);

		verify(player, never()).sendMessage(anyString());
		verify(jetpackService).scheduleChestplateCheck(player);
	}

	@Test
	@DisplayName("onInteract with a non-jetpack chestplate sends nothing (nothing to warn about)")
	void onInteract_nonJetpackChestplate_noMessage() {
		JetpackService  jetpackService = mock(JetpackService.class);
		JetpackAddon    jetpackAddon   = mock(JetpackAddon.class);
		JetpackMessages messages       = mock(JetpackMessages.class);

		JetpackEquipListener listener = new JetpackEquipListener(jetpackService, jetpackAddon, messages);

		Player player = mock(Player.class);
		when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);

		PlayerInteractEvent event = mock(PlayerInteractEvent.class);
		when(event.getHand()).thenReturn(EquipmentSlot.HAND);
		when(event.getAction()).thenReturn(Action.RIGHT_CLICK_AIR);
		when(event.getPlayer()).thenReturn(player);
		when(event.getItem()).thenReturn(new ItemStack(Material.IRON_CHESTPLATE));

		listener.onInteract(event);

		verify(player, never()).sendMessage(anyString());
		verify(jetpackService).scheduleChestplateCheck(player);
	}
}
