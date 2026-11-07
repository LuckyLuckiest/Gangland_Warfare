package org.luckyraven.gangland.gadget.jetpack;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.testsupport.BukkitRegistryFixture;
import org.luckyraven.gangland.gadget.config.GadgetPhysicsConfig;
import org.luckyraven.gangland.item.fuel.FuelService;
import org.luckyraven.keystone.item.ItemBuilder;
import org.luckyraven.keystone.item.nbt.NbtBridge;
import org.luckyraven.keystone.testkit.BukkitStatics;
import org.luckyraven.keystone.testkit.RecordingNbtAccessor;
import org.mockito.InOrder;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Characterises {@code JetpackTask.checkGuards} before its two identical-bodied guard branches (offline player, not
 * wearing the jetpack) are merged into one condition: both deactivate the player and cancel the task, the wearing
 * check is never evaluated for an offline player, and a player who is online and wearing it passes untouched.
 */
@DisplayName("JetpackTask.checkGuards — offline / not wearing / wearing")
class JetpackTaskGuardsCharacterisationTest {

	@BeforeAll
	static void bootstrap() {
		BukkitRegistryFixture.install();
	}

	private BukkitStatics     bukkit;
	private JetpackService    service;
	private JetpackTask       task;
	private PlayerInventory   inventory;
	private Player            player;

	@BeforeEach
	void setUp() {
		NbtBridge.install(new RecordingNbtAccessor());
		bukkit = BukkitStatics.install();
		when(bukkit.scheduler().runTaskTimer(any(Plugin.class), any(Runnable.class), anyLong(), anyLong()))
				.thenReturn(mock(BukkitTask.class));

		service = mock(JetpackService.class);
		player  = mock(Player.class);
		inventory = mock(PlayerInventory.class);
		when(player.getInventory()).thenReturn(inventory);

		JetpackSession session = mock(JetpackSession.class);
		task = new JetpackTask(session, service, mock(FuelService.class), mock(GadgetPhysicsConfig.class));
		task.runTaskTimer(mock(JavaPlugin.class), 1L, 1L);
	}

	@AfterEach
	void tearDown() {
		NbtBridge.reset();
		if (bukkit != null) bukkit.close();
	}

	private boolean checkGuards(Jetpack jetpack) throws Exception {
		Method method = JetpackTask.class.getDeclaredMethod("checkGuards", Player.class, Jetpack.class);
		method.setAccessible(true);
		return (boolean) method.invoke(task, player, jetpack);
	}

	private static Jetpack jetpack() {
		return Jetpack.builder().jetpackId("jp").build();
	}

	@Test
	@DisplayName("an offline player: guard trips, deactivate then cancel, the inventory is never looked at")
	void offline_deactivatesAndCancels_withoutCheckingWearing() throws Exception {
		when(player.isOnline()).thenReturn(false);

		assertEquals(true, checkGuards(jetpack()));

		InOrder order = inOrder(service, bukkit.scheduler());
		order.verify(service).deactivate(player);
		order.verify(bukkit.scheduler()).cancelTask(anyInt());
		verify(player, never()).getInventory();
	}

	@Test
	@DisplayName("online with an empty chest slot: guard trips, deactivate then cancel")
	void online_noChestplate_deactivatesAndCancels() throws Exception {
		when(player.isOnline()).thenReturn(true);
		when(inventory.getChestplate()).thenReturn(null);

		assertEquals(true, checkGuards(jetpack()));

		InOrder order = inOrder(service, bukkit.scheduler());
		order.verify(service).deactivate(player);
		order.verify(bukkit.scheduler()).cancelTask(anyInt());
	}

	@Test
	@DisplayName("online wearing a different jetpack id: guard trips, deactivate then cancel")
	void online_otherJetpack_deactivatesAndCancels() throws Exception {
		when(player.isOnline()).thenReturn(true);
		ItemStack chest = new ItemStack(Material.IRON_CHESTPLATE);
		new ItemBuilder(chest).addTag(JetpackKey.JETPACK_ID.getKey(), "other");
		when(inventory.getChestplate()).thenReturn(chest);

		assertEquals(true, checkGuards(jetpack()));

		InOrder order = inOrder(service, bukkit.scheduler());
		order.verify(service).deactivate(player);
		order.verify(bukkit.scheduler()).cancelTask(anyInt());
	}

	@Test
	@DisplayName("online wearing this jetpack: guard passes, nothing is deactivated or cancelled")
	void online_wearing_passes() throws Exception {
		when(player.isOnline()).thenReturn(true);
		ItemStack chest = new ItemStack(Material.IRON_CHESTPLATE);
		new ItemBuilder(chest).addTag(JetpackKey.JETPACK_ID.getKey(), "jp");
		when(inventory.getChestplate()).thenReturn(chest);

		assertEquals(false, checkGuards(jetpack()));

		verify(service, never()).deactivate(any());
		verify(bukkit.scheduler(), never()).cancelTask(anyInt());
	}
}
