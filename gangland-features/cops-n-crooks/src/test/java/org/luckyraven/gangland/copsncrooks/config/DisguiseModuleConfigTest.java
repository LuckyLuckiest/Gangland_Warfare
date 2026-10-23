package org.luckyraven.gangland.copsncrooks.config;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.npc.police.CopGroup;
import org.luckyraven.gangland.copsncrooks.npc.police.CopManager;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopConfigProvider;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopLoader;
import org.luckyraven.gangland.copsncrooks.npc.police.config.DisguiseSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.perception.CopPerception;
import org.luckyraven.gangland.copsncrooks.npc.police.radio.CopRadio;
import org.luckyraven.gangland.copsncrooks.wanted.disguise.DisguiseFilter;
import org.luckyraven.gangland.copsncrooks.wanted.disguise.DisguiseTick;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.core.wanted.Wanted;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.PostConstruct;
import org.luckyraven.keystone.bean.autowire.DependencyContainer;
import org.luckyraven.keystone.npc.NpcSquad;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Disguise wiring: the live settings supplier, and the three installs (lock-on filter, AI tick hook, radio extras). */
@DisplayName("DisguiseModuleConfig")
class DisguiseModuleConfigTest {

	private CopManager     manager;
	private CopRadio       radio;
	private CopPerception  perception;
	private DisguiseFilter filter;
	private DisguiseTick   tick;
	private DisguiseModuleConfig config;

	@BeforeEach
	void setUp() {
		manager    = mock(CopManager.class);
		radio      = mock(CopRadio.class);
		perception = new CopPerception();
		filter     = mock(DisguiseFilter.class);
		tick       = mock(DisguiseTick.class);

		DependencyContainer container = new DependencyContainer();
		container.registerInstance(CopManager.class, manager);
		container.registerInstance(CopRadio.class, radio);
		container.registerInstance(CopPerception.class, perception);
		container.registerInstance(DisguiseFilter.class, filter);
		container.registerInstance(DisguiseTick.class, tick);

		config = new DisguiseModuleConfig(mock(JavaPlugin.class), container);
	}

	@Test
	@DisplayName("settings: the shipped defaults until the cop config has loaded")
	void settingsDefaultBeforeLoad() {
		CopLoader loader = mock(CopLoader.class);
		when(loader.getLoadedProvider()).thenReturn(null);

		assertSame(DisguiseSettings.DEFAULT, DisguiseModuleConfig.settingsOf(loader).get());
	}

	@Test
	@DisplayName("settings: read live from the loaded cop config on every call")
	void settingsFollowTheLoadedConfig() {
		CopLoader loader = mock(CopLoader.class);
		CopConfigProvider provider = mock(CopConfigProvider.class);
		DisguiseSettings first  = new DisguiseSettings(true, List.of(), List.of(), List.of(), 1, 2);
		DisguiseSettings second = new DisguiseSettings(false, List.of(), List.of(), List.of(), 4, 3);
		when(loader.getLoadedProvider()).thenReturn(provider);
		when(provider.getDisguiseSettings()).thenReturn(first, second);

		var supplier = DisguiseModuleConfig.settingsOf(loader);

		assertEquals(first, supplier.get());
		assertEquals(second, supplier.get());
	}

	@Test
	@DisplayName("settings: a provider that answers null falls back to the defaults")
	void settingsNullFallsBack() {
		CopLoader loader = mock(CopLoader.class);
		CopConfigProvider provider = mock(CopConfigProvider.class);
		when(loader.getLoadedProvider()).thenReturn(provider);
		when(provider.getDisguiseSettings()).thenReturn(null);

		assertSame(DisguiseSettings.DEFAULT, DisguiseModuleConfig.settingsOf(loader).get());
	}

	@Test
	@DisplayName("levelOf: 0 for an unknown user or a missing wanted record, else the wanted level")
	@SuppressWarnings("unchecked")
	void levelOfReadsTheWantedLevel() {
		UserManager<Player> users = mock(UserManager.class);
		Player player = mock(Player.class);
		var level = DisguiseModuleConfig.levelOf(users);

		when(users.getUser(player)).thenReturn(null);
		assertEquals(0, level.applyAsInt(player));

		User<Player> user = mock(User.class);
		when(users.getUser(player)).thenReturn(user);
		when(user.getWanted()).thenReturn(null);
		assertEquals(0, level.applyAsInt(player));

		Wanted wanted = mock(Wanted.class);
		when(wanted.getLevel()).thenReturn(3);
		when(user.getWanted()).thenReturn(wanted);
		assertEquals(3, level.applyAsInt(player));
	}

	@Test
	@DisplayName("freshSightingOf: false without a squad, else whether the squad has a fresh sighting")
	void freshSightingFollowsTheSquad() {
		Player player = mock(Player.class);
		java.util.UUID id = java.util.UUID.randomUUID();
		when(player.getUniqueId()).thenReturn(id);
		var fresh = DisguiseModuleConfig.freshSightingOf(manager);

		when(manager.groupOf(id)).thenReturn(null);
		assertFalse(fresh.test(player));

		CopGroup group = mock(CopGroup.class);
		NpcSquad squad = mock(NpcSquad.class);
		when(group.getSquad()).thenReturn(squad);
		when(manager.groupOf(id)).thenReturn(group);
		when(squad.hasFreshSighting()).thenReturn(true);
		assertTrue(fresh.test(player));

		when(squad.hasFreshSighting()).thenReturn(false);
		assertFalse(fresh.test(player));
	}

	@Test
	@DisplayName("installDisguise is a @PostConstruct")
	void installIsAPostConstruct() throws NoSuchMethodException {
		assertTrue(DisguiseModuleConfig.class.getMethod("installDisguise").isAnnotationPresent(PostConstruct.class));
	}

	@Test
	@DisplayName("every disguise object is a @Bean")
	void beansDeclared() {
		long beans = java.util.Arrays.stream(DisguiseModuleConfig.class.getDeclaredMethods())
		                             .filter(m -> m.isAnnotationPresent(Bean.class)).count();
		assertEquals(5, beans, "wearableKeys, suspectDescriptions, policeOutfit, disguiseFilter, disguiseTick");
	}

	@Test
	@DisplayName("install registers the filter on CopPerception: a rejecting filter stops allowsLockOn")
	void installRegistersTheFilter() {
		when(filter.test(any(CopNpc.class), any(Player.class))).thenReturn(false);

		config.installDisguise();

		assertFalse(perception.allowsLockOn(mock(CopNpc.class), mock(Player.class)));
	}

	@Test
	@DisplayName("install registers the AI tick hook that ticks the disguise")
	@SuppressWarnings("unchecked")
	void installRegistersTheTickHook() {
		config.installDisguise();

		ArgumentCaptor<BiConsumer<Player, CopGroup>> captor = ArgumentCaptor.forClass(BiConsumer.class);
		verify(manager).addAiTickHook(captor.capture());
		Player player = mock(Player.class);
		CopGroup group = mock(CopGroup.class);

		captor.getValue().accept(player, group);

		verify(tick).tick(player, group);
	}

	@Test
	@DisplayName("install supplies %suspect% to the radio through the extras hook")
	@SuppressWarnings("unchecked")
	void installRegistersTheExtras() {
		NpcSquad squad = mock(NpcSquad.class);
		when(tick.extras(squad)).thenReturn(Map.of("suspect", "x"));

		config.installDisguise();

		ArgumentCaptor<Function<NpcSquad, Map<String, String>>> captor = ArgumentCaptor.forClass(Function.class);
		verify(radio).setExtraPlaceholders(captor.capture());
		assertEquals(Map.of("suspect", "x"), captor.getValue().apply(squad));
	}
}
