package org.luckyraven.gangland.copsncrooks.config;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.item.CopsItems;
import org.luckyraven.gangland.copsncrooks.npc.police.CopGroup;
import org.luckyraven.gangland.copsncrooks.npc.police.CopManager;
import org.luckyraven.gangland.copsncrooks.npc.police.SirenService;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopConfigProvider;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopLoader;
import org.luckyraven.gangland.copsncrooks.npc.police.config.ScannerSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.config.SirenSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.radio.CopRadio;
import org.luckyraven.gangland.copsncrooks.npc.police.radio.ScannerOverhearing;
import org.luckyraven.keystone.bean.PostConstruct;
import org.luckyraven.keystone.bean.autowire.DependencyContainer;
import org.luckyraven.keystone.npc.NpcSquad;
import org.luckyraven.keystone.testkit.BukkitStatics;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Scanner wiring: the overhearing predicate goes onto the radio, the siren service onto the cop AI tick. */
@DisplayName("ScannerModuleConfig")
class ScannerModuleConfigTest {

	@Test
	@DisplayName("installScanner hands the overhearing predicate to the radio and the siren tick to the cop manager")
	@SuppressWarnings("unchecked")
	void installScanner_wiresRadioAndTick() {
		ScannerOverhearing overhearing = mock(ScannerOverhearing.class);
		SirenService       siren       = mock(SirenService.class);
		CopRadio           radio       = mock(CopRadio.class);
		CopManager         manager     = mock(CopManager.class);

		DependencyContainer container = new DependencyContainer();
		container.registerInstance(ScannerOverhearing.class, overhearing);
		container.registerInstance(SirenService.class, siren);
		container.registerInstance(CopRadio.class, radio);
		container.registerInstance(CopManager.class, manager);

		new ScannerModuleConfig(mock(JavaPlugin.class), container).installScanner();

		verify(radio).setOverhearing(overhearing);
		ArgumentCaptor<BiConsumer<Player, CopGroup>> hook = ArgumentCaptor.forClass(BiConsumer.class);
		verify(manager).addAiTickHook(hook.capture());
		Player   player = mock(Player.class);
		CopGroup group  = mock(CopGroup.class);
		hook.getValue().accept(player, group);
		verify(siren).tick(player, group);
	}

	@Test
	@DisplayName("installScanner is a @PostConstruct")
	void installScanner_isAPostConstruct() throws NoSuchMethodException {
		assertTrue(ScannerModuleConfig.class.getMethod("installScanner").isAnnotationPresent(PostConstruct.class));
	}

	@Test
	@DisplayName("the overhearing bean reads Cops.Scanner from the loaded provider on every call; a missing provider falls back to the defaults")
	void overhearingBean_readsTheLoadedConfigPerCall() {
		try (BukkitStatics bukkit = BukkitStatics.install()) {
			CopLoader         loader   = mock(CopLoader.class);
			CopConfigProvider provider = mock(CopConfigProvider.class);
			when(provider.getScannerSettings()).thenReturn(new ScannerSettings(false, 160.0, "CLOCK", "x", List.of()));
			when(loader.getLoadedProvider()).thenReturn(null, provider);

			ItemStack stack = mock(ItemStack.class);
			CopsItems items = mock(CopsItems.class);
			when(items.isScanner(stack)).thenReturn(true);

			World           world     = mock(World.class);
			PlayerInventory inventory = mock(PlayerInventory.class);
			when(inventory.getContents()).thenReturn(new ItemStack[]{stack});
			when(inventory.getItemInOffHand()).thenReturn(null);
			Player player = mock(Player.class);
			when(player.getInventory()).thenReturn(inventory);
			when(player.getWorld()).thenReturn(world);
			when(player.getLocation()).thenReturn(new Location(world, 0, 64, 0));

			LivingEntity entity = mock(LivingEntity.class);
			when(entity.getLocation()).thenReturn(new Location(world, 120, 64, 0));
			when(entity.getWorld()).thenReturn(world);
			CopNpc cop = mock(CopNpc.class);
			when(cop.getEntity()).thenReturn(entity);
			NpcSquad squad = mock(NpcSquad.class);
			when(squad.members()).thenReturn(List.of(cop));

			ScannerOverhearing bean = new ScannerModuleConfig(mock(JavaPlugin.class), new DependencyContainer())
					.scannerOverhearing(loader, items);
			assertNotNull(bean);

			assertTrue(bean.test(player, squad), "no provider yet: the enabled defaults apply");
			assertFalse(bean.test(player, squad), "the loaded provider disables the scanner");
			verify(loader, atLeast(2)).getLoadedProvider();
			verify(items, atLeastOnce()).isScanner(stack);
		}
	}

	@Test
	@DisplayName("the siren bean reads Cops.Siren from the loaded provider on every call")
	void sirenBean_readsTheLoadedConfigPerCall() {
		CopLoader         loader   = mock(CopLoader.class);
		CopConfigProvider provider = mock(CopConfigProvider.class);
		when(provider.getSirenSettings()).thenReturn(new SirenSettings(false, 5, 24.0, "X", 3f, 1f));
		when(loader.getLoadedProvider()).thenReturn(null, provider);

		SirenService bean = new ScannerModuleConfig(mock(JavaPlugin.class), new DependencyContainer())
				.sirenService(loader);
		assertNotNull(bean);

		Player player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(UUID.randomUUID());
		when(player.getLocation()).thenReturn(new Location(mock(World.class), 0, 64, 0));
		CopGroup group = mock(CopGroup.class);
		when(group.getCops()).thenReturn(List.of());

		bean.tick(player, group);
		bean.tick(player, group);
		verify(loader, atLeast(2)).getLoadedProvider();
		verify(provider).getSirenSettings();
	}
}
