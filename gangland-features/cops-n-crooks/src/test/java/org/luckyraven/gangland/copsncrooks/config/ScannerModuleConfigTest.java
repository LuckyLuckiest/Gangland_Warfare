package org.luckyraven.gangland.copsncrooks.config;

import org.bukkit.entity.Player;
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
import org.luckyraven.gangland.copsncrooks.npc.police.radio.CopRadio;
import org.luckyraven.gangland.copsncrooks.npc.police.radio.ScannerOverhearing;
import org.luckyraven.keystone.bean.PostConstruct;
import org.luckyraven.keystone.bean.autowire.DependencyContainer;
import org.luckyraven.keystone.npc.NpcSquad;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeast;
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
		CopLoader         loader   = mock(CopLoader.class);
		CopConfigProvider provider = mock(CopConfigProvider.class);
		when(provider.getScannerSettings()).thenReturn(new ScannerSettings(false, 160.0, "CLOCK", "x", List.of()));
		when(loader.getLoadedProvider()).thenReturn(null, provider);

		ScannerOverhearing bean = new ScannerModuleConfig(mock(JavaPlugin.class), new DependencyContainer())
				.scannerOverhearing(loader, mock(CopsItems.class));
		assertNotNull(bean);

		NpcSquad squad = mock(NpcSquad.class);
		when(squad.members()).thenReturn(List.of());
		Player player = mock(Player.class);
		assertFalse(bean.test(player, squad));
		assertFalse(bean.test(player, squad));
		verify(loader, atLeast(2)).getLoadedProvider();
	}

	@Test
	@DisplayName("the siren bean is built from the loader")
	void sirenBean_isBuilt() {
		assertNotNull(new ScannerModuleConfig(mock(JavaPlugin.class), new DependencyContainer())
				              .sirenService(mock(CopLoader.class)));
	}
}
