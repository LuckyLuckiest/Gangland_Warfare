package org.luckyraven.gangland.copsncrooks.config;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.detainment.economy.DetainmentEconomyContract;
import org.luckyraven.gangland.copsncrooks.npc.police.CopGroup;
import org.luckyraven.gangland.copsncrooks.npc.police.CopManager;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopConfigProvider;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopLoader;
import org.luckyraven.gangland.copsncrooks.npc.police.config.PullOverSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.config.WarningSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.patrol.BrandishWarnings;
import org.luckyraven.gangland.copsncrooks.npc.police.patrol.PatrolManager;
import org.luckyraven.gangland.copsncrooks.npc.police.patrol.PatrolServices;
import org.luckyraven.gangland.copsncrooks.npc.police.patrol.PullOverTracker;
import org.luckyraven.gangland.copsncrooks.npc.police.perception.CopPerception;
import org.luckyraven.gangland.copsncrooks.npc.police.radio.CopRadio;
import org.luckyraven.gangland.copsncrooks.npc.police.spawn.CopSpawnManager;
import org.luckyraven.gangland.copsncrooks.npc.police.targeting.TargetingManager;
import org.luckyraven.gangland.copsncrooks.station.StationRegistry;
import org.luckyraven.gangland.copsncrooks.wanted.WantedMessages;
import org.luckyraven.gangland.crime.CrimeService;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.PostConstruct;
import org.luckyraven.keystone.bean.autowire.DependencyContainer;
import org.luckyraven.keystone.npc.entity.NpcMarkManager;
import org.luckyraven.bartizan.api.BartizanApi;
import org.luckyraven.bartizan.api.weapon.WeaponCatalog;
import org.luckyraven.gangland.copsncrooks.npc.police.patrol.PatrolFixture;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CopState;
import org.luckyraven.gangland.copsncrooks.station.Station;
import org.luckyraven.keystone.npc.NpcSupport;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicesManager;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 0.20.0 lane patrol: {@link PatrolModuleConfig} builds the four beans and installs the resolver and the services. */
@DisplayName("PatrolModuleConfig - patrol wiring")
class PatrolWiringTest {

	private CopLoader         loader;
	private CopConfigProvider provider;
	private PatrolModuleConfig config;

	@BeforeEach
	void setUp() {
		provider = mock(CopConfigProvider.class);
		when(provider.getPatrolSettings()).thenReturn(org.luckyraven.gangland.copsncrooks.npc.police.config.PatrolSettings.DEFAULT);
		when(provider.getWarningSettings()).thenReturn(new WarningSettings(false, 12.0, 5, 30));
		when(provider.getPullOverSettings()).thenReturn(new PullOverSettings(false, 24.0, 12.0, 6, 1.0, 2, 150.0, 120));
		loader = mock(CopLoader.class);
		when(loader.getLoadedProvider()).thenReturn(provider);
		config = new PatrolModuleConfig(mock(JavaPlugin.class), new DependencyContainer());
	}

	@Test
	@DisplayName("the four patrol beans are @Bean methods and installPatrol is a @PostConstruct")
	void annotations() throws NoSuchMethodException {
		for (String name : new String[] {"brandishWarnings", "pullOverTracker", "patrolServices", "patrolManager"}) {
			boolean found = false;
			for (var method : PatrolModuleConfig.class.getDeclaredMethods())
				if (method.getName().equals(name) && method.isAnnotationPresent(Bean.class)) found = true;
			assertTrue(found, name + " must be a @Bean");
		}
		assertTrue(PatrolModuleConfig.class.getMethod("installPatrol").isAnnotationPresent(PostConstruct.class));
	}

	@Test
	@DisplayName("brandishWarnings reads Cops.Warning from the loaded provider on every call")
	void brandishWarnings_readsProvider() {
		BrandishWarnings warnings = config.brandishWarnings(loader, mock(CrimeService.class),
		                                                    mock(WantedMessages.class));

		assertNotNull(warnings);
		assertFalse(warnings.canWarn(mock(Player.class)));
		when(provider.getWarningSettings()).thenReturn(WarningSettings.DEFAULT);
		Player player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(UUID.randomUUID());
		assertTrue(warnings.canWarn(player));
	}

	@Test
	@DisplayName("pullOverTracker reads Cops.Pull_Over from the loaded provider")
	void pullOverTracker_readsProvider() {
		PullOverTracker tracker = config.pullOverTracker(loader, mock(CrimeService.class),
		                                                 mock(DetainmentEconomyContract.class),
		                                                 mock(WantedMessages.class));

		assertNotNull(tracker);
		assertFalse(tracker.canOrder(UUID.randomUUID()));
	}

	@Test
	@DisplayName("patrolServices carries the shared perception, radio and warnings and delegates wanted and group lookups")
	void patrolServices_delegates() {
		CopPerception    perception = new CopPerception();
		CopRadio         radio      = mock(CopRadio.class);
		TargetingManager targeting  = mock(TargetingManager.class);
		CopManager       manager    = mock(CopManager.class);
		BrandishWarnings warnings   = mock(BrandishWarnings.class);
		UUID             id         = UUID.randomUUID();
		CopGroup         group      = mock(CopGroup.class);
		Player           player     = mock(Player.class);
		when(player.getUniqueId()).thenReturn(id);
		when(targeting.isWanted(id)).thenReturn(true);
		when(manager.groupOf(id)).thenReturn(group);

		PatrolServices services = config.patrolServices(loader, perception, targeting, manager, warnings, radio);

		assertSame(perception, services.perception());
		assertSame(radio, services.radio());
		assertSame(warnings, services.warnings());
		assertTrue(services.wanted().test(player));
		assertSame(group, services.groupOf().apply(id));
		assertNotNull(services.patrol().get());
		assertNotNull(services.warning().get());
		assertNotNull(services.players());
	}

	@Test
	@DisplayName("patrolManager is built")
	void patrolManager_built() {
		PatrolManager manager = config.patrolManager(loader, mock(StationRegistry.class), mock(CopSpawnManager.class),
		                                             mock(NpcMarkManager.class), new CopPerception());

		assertNotNull(manager);
	}

	@Test
	@DisplayName("installPatrol hands the services to the spawn manager and registers the beat cops as a cop resolver")
	@SuppressWarnings("unchecked")
	void installPatrol_wiresSpawnManagerAndResolver() {
		CopSpawnManager spawn    = mock(CopSpawnManager.class);
		CopManager      copManager = mock(CopManager.class);
		PatrolManager   patrol   = mock(PatrolManager.class);
		PatrolServices  services = mock(PatrolServices.class);
		DependencyContainer container = new DependencyContainer();
		container.registerInstance(CopSpawnManager.class, spawn);
		container.registerInstance(CopManager.class, copManager);
		container.registerInstance(PatrolManager.class, patrol);
		container.registerInstance(PatrolServices.class, services);

		new PatrolModuleConfig(mock(JavaPlugin.class), container).installPatrol();

		verify(spawn).setPatrolServices(services);
		ArgumentCaptor<Function<Entity, CopNpc>> resolver = ArgumentCaptor.forClass(Function.class);
		verify(copManager).addCopResolver(resolver.capture());
		Entity entity = mock(Entity.class);
		CopNpc beat   = mock(CopNpc.class);
		when(patrol.beatCopOf(entity)).thenReturn(beat);
		assertSame(beat, resolver.getValue().apply(entity));
		verify(patrol).beatCopOf(any());
	}

	// ---- the lambdas that make the feature work (a "p -> false" bean must fail these) ----

	@SuppressWarnings("unchecked")
	private void bartizan(PatrolFixture fx, Player holder, boolean pluginEnabled, boolean weapon) {
		PluginManager pm = mock(PluginManager.class);
		when(pm.isPluginEnabled("Bartizan")).thenReturn(pluginEnabled);
		fx.bukkit.statics().when(Bukkit::getPluginManager).thenReturn(pm);
		ServicesManager services = mock(ServicesManager.class);
		RegisteredServiceProvider<BartizanApi> registration = mock(RegisteredServiceProvider.class);
		BartizanApi     api       = mock(BartizanApi.class);
		WeaponCatalog   weapons   = mock(WeaponCatalog.class);
		ItemStack       held      = mock(ItemStack.class);
		PlayerInventory inventory = mock(PlayerInventory.class);
		when(inventory.getItemInMainHand()).thenReturn(held);
		when(holder.getInventory()).thenReturn(inventory);
		when(weapons.isWeapon(held)).thenReturn(weapon);
		when(api.weapons()).thenReturn(weapons);
		when(registration.getProvider()).thenReturn(api);
		when(services.getRegistration(BartizanApi.class)).thenReturn(registration);
		fx.bukkit.statics().when(Bukkit::getServicesManager).thenReturn(services);
	}

	@Test
	@DisplayName("brandishWarnings' holding predicate is Bartizan enabled AND a weapon in the main hand (P-PAT-2)")
	void brandishWarnings_holdingPredicate() {
		try (PatrolFixture fx = new PatrolFixture()) {
			Player player = fx.player(0, 0);
			bartizan(fx, player, true, true);
			BrandishWarnings warnings = config.brandishWarnings(loader, mock(CrimeService.class),
			                                                    PatrolFixture.messages());
			assertTrue(warnings.holding(player));

			bartizan(fx, player, false, true);
			assertFalse(warnings.holding(player), "plugin disabled means not holding");

			bartizan(fx, player, true, false);
			assertFalse(warnings.holding(player), "another item is not a weapon");
		}
	}

	private PatrolManager managerFor(PatrolFixture fx, CopSpawnManager spawner) {
		Station station = mock(Station.class);
		when(station.getId()).thenReturn(1);
		when(station.getName()).thenReturn("Central");
		when(station.getLocation()).thenReturn(new Location(fx.world, 0, 64, 0));
		StationRegistry stations = mock(StationRegistry.class);
		when(stations.all()).thenReturn(List.of(station));
		return config.patrolManager(loader, stations, spawner, mock(NpcMarkManager.class), new CopPerception());
	}

	private CopSpawnManager spawner(PatrolFixture fx) {
		CopSpawnManager spawner = mock(CopSpawnManager.class);
		when(spawner.spawnAtLocation(any(), anyInt(), anyBoolean()))
				.thenAnswer(inv -> fx.cop(CopState.IDLE, 0, 0, inv.getArgument(0)));
		return spawner;
	}

	@Test
	@DisplayName("patrolManager reads the online players, NpcSupport.isNpc and Settings.isCitizensAvailable")
	void patrolManager_lambdas() {
		try (PatrolFixture fx = new PatrolFixture(); MockedStatic<NpcSupport> npcs = mockStatic(NpcSupport.class)) {
			Player near = fx.player(10, 0);
			fx.bukkit.statics().when(Bukkit::getOnlinePlayers).thenAnswer(inv -> List.of(near));

			npcs.when(NpcSupport::available).thenReturn(true);
			npcs.when(() -> NpcSupport.isNpc(any(Entity.class))).thenReturn(false);
			CopSpawnManager spawner = spawner(fx);
			managerFor(fx, spawner).manage();
			verify(spawner, times(2)).spawnAtLocation(any(), anyInt(), anyBoolean());

			npcs.when(NpcSupport::available).thenReturn(false);
			CopSpawnManager noCitizens = spawner(fx);
			managerFor(fx, noCitizens).manage();
			verify(noCitizens, never()).spawnAtLocation(any(), anyInt(), anyBoolean());

			npcs.when(NpcSupport::available).thenReturn(true);
			npcs.when(() -> NpcSupport.isNpc(any(Entity.class))).thenReturn(true);
			CopSpawnManager npcOnly = spawner(fx);
			managerFor(fx, npcOnly).manage();
			verify(npcOnly, never()).spawnAtLocation(any(), anyInt(), anyBoolean());
		}
	}

	@Test
	@DisplayName("pullOverTracker looks the driver up online and charges the ticket through the economy contract")
	void pullOverTracker_lambdas() {
		try (PatrolFixture fx = new PatrolFixture()) {
			Player player = fx.player(0, 0);
			UUID   id     = player.getUniqueId();
			when(provider.getPullOverSettings()).thenReturn(new PullOverSettings(true, 24, 12, 6, 1.0, 0, 150, 120));
			DetainmentEconomyContract economy = mock(DetainmentEconomyContract.class);
			when(economy.getBalance(player)).thenReturn(1000.0);
			when(economy.tryCharge(player, 150.0)).thenReturn(DetainmentEconomyContract.ChargeResult.SUCCESS);
			PullOverTracker tracker = config.pullOverTracker(loader, mock(CrimeService.class), economy,
			                                                 PatrolFixture.messages());

			assertTrue(tracker.order(player, 15));
			tracker.onSpeed(id, 0);
			tracker.tick();

			verify(economy).tryCharge(player, 150.0);
			assertFalse(tracker.isOrdered(id), "the ticket closes the order");
		}
	}

	@Test
	@DisplayName("patrolServices' players and isNpc are the online players and NpcSupport.isNpc")
	void patrolServices_playersAndIsNpc() {
		try (PatrolFixture fx = new PatrolFixture(); MockedStatic<NpcSupport> npcs = mockStatic(NpcSupport.class)) {
			Player       online = fx.player(1, 1);
			List<Player> list   = List.of(online);
			fx.bukkit.statics().when(Bukkit::getOnlinePlayers).thenAnswer(inv -> list);
			Entity npc = mock(Entity.class);
			npcs.when(() -> NpcSupport.isNpc(npc)).thenReturn(true);

			PatrolServices services = config.patrolServices(loader, new CopPerception(), mock(TargetingManager.class),
			                                                mock(CopManager.class), mock(BrandishWarnings.class),
			                                                mock(CopRadio.class));

			assertSame(list, services.players().get());
			assertTrue(services.isNpc().test(npc));
			assertFalse(services.isNpc().test(online));
		}
	}
}
