package org.luckyraven.gangland.copsncrooks.config;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.bag.BagEntry;
import org.luckyraven.gangland.copsncrooks.bag.BagEnv;
import org.luckyraven.gangland.copsncrooks.bag.BagHud;
import org.luckyraven.gangland.copsncrooks.bag.BagService;
import org.luckyraven.gangland.copsncrooks.bag.BagSettings;
import org.luckyraven.gangland.copsncrooks.bag.BagSpill;
import org.luckyraven.gangland.copsncrooks.bag.HotCashBags;
import org.luckyraven.gangland.copsncrooks.database.HotCashBagRepository;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentService;
import org.luckyraven.gangland.copsncrooks.detainment.bribe.BribeService;
import org.luckyraven.gangland.copsncrooks.detainment.intake.JailIntakeService;
import org.luckyraven.gangland.copsncrooks.detainment.paperwork.PaperworkView;
import org.luckyraven.gangland.copsncrooks.npc.police.CopManager;
import org.luckyraven.gangland.copsncrooks.wanted.WantedMessages;
import org.luckyraven.gangland.copsncrooks.wanted.config.ChaseConfigLoader;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.core.wanted.WantedStars;
import org.luckyraven.gangland.data.economy.HotCash;
import org.luckyraven.gangland.data.economy.HotCashRouter;
import org.luckyraven.gangland.item.money.MoneyAddon;
import org.luckyraven.gangland.item.money.MoneyDepositService;
import org.luckyraven.keystone.bean.PostConstruct;
import org.luckyraven.keystone.bean.autowire.DependencyContainer;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Lane bag: {@link BagModuleConfig}. A missing router install, intake seizure, bribe funding, paperwork line, data
 * supplier, HUD start or shutdown hook would kill the feature with every service test green, so each is pinned here.
 */
@DisplayName("BagModuleConfig")
class BagModuleConfigTest {

	private HotCashRouter     router;
	private BagService        service;
	private BagHud            hud;
	private JailIntakeService intake;
	private BribeService      bribes;
	private PaperworkView     paperwork;
	private CopManager        copManager;
	private BagModuleConfig   config;

	@BeforeEach
	void setUp() {
		router     = new HotCashRouter();
		service    = mock(BagService.class);
		hud        = mock(BagHud.class);
		intake     = mock(JailIntakeService.class);
		bribes     = mock(BribeService.class);
		paperwork  = mock(PaperworkView.class);
		copManager = mock(CopManager.class);

		DependencyContainer container = new DependencyContainer();
		container.registerInstance(HotCashRouter.class, router);
		container.registerInstance(BagService.class, service);
		container.registerInstance(BagHud.class, hud);
		container.registerInstance(JailIntakeService.class, intake);
		container.registerInstance(BribeService.class, bribes);
		container.registerInstance(PaperworkView.class, paperwork);
		container.registerInstance(CopManager.class, copManager);
		config = new BagModuleConfig(mock(JavaPlugin.class), container);
	}

	@Test
	@DisplayName("the bean container runs it: installBag is a @PostConstruct")
	void installBag_isAPostConstruct() throws NoSuchMethodException {
		assertTrue(BagModuleConfig.class.getMethod("installBag").isAnnotationPresent(PostConstruct.class));
	}

	@Test
	@DisplayName("installBag installs the service into the HotCashRouter: a take now reaches the bag")
	void installBag_installsTheRouter() {
		Player player = mock(Player.class);
		when(service.take(player, BigDecimal.TEN, "pickup")).thenReturn(HotCash.Take.BAGGED);

		config.installBag();

		assertTrue(router.isInstalled());
		assertEquals(HotCash.Take.BAGGED, router.take(player, BigDecimal.TEN, "pickup"));
	}

	@Test
	@DisplayName("installBag gives the jail intake a seizure that calls BagService.seize")
	@SuppressWarnings("unchecked")
	void installBag_hooksTheIntakeSeizure() {
		Player player = mock(Player.class);
		when(service.seize(player)).thenReturn(42.5);

		config.installBag();

		ArgumentCaptor<ToDoubleFunction<Player>> seizure = ArgumentCaptor.forClass(ToDoubleFunction.class);
		verify(intake).setBagSeizure(seizure.capture());
		assertEquals(42.5, seizure.getValue().applyAsDouble(player));
	}

	@Test
	@DisplayName("installBag lets bribes draw from the bag")
	void installBag_hooksTheBribeFunding() {
		config.installBag();

		verify(bribes).setBagFunding(service);
	}

	@Test
	@DisplayName("installBag gives the paperwork view the seized-bag line")
	@SuppressWarnings("unchecked")
	void installBag_hooksThePaperworkLine() {
		UUID id = UUID.randomUUID();
		when(service.paperworkLine(id)).thenReturn("Bag seized: $800.00");

		config.installBag();

		ArgumentCaptor<Function<UUID, String>> line = ArgumentCaptor.forClass(Function.class);
		verify(paperwork).setBagLine(line.capture());
		assertEquals("Bag seized: $800.00", line.getValue().apply(id));
	}

	@Test
	@DisplayName("installBag starts the HUD once and registers one shutdown hook that stops the HUD and saves the service")
	void installBag_startsTheHudAndRegistersTheShutdownHook() {
		config.installBag();

		verify(hud, times(1)).start();
		ArgumentCaptor<Runnable> hook = ArgumentCaptor.forClass(Runnable.class);
		verify(copManager, times(1)).addShutdownHook(hook.capture());

		hook.getValue().run();

		verify(service).shutdown();
		verify(hud).stop();
	}

	@Test
	@DisplayName("hotCashBags wires the repository data supplier and loads the saved rows")
	void hotCashBags_wiresTheSupplierAndLoads() {
		HotCashBagRepository repository = mock(HotCashBagRepository.class);
		UUID                 id         = UUID.randomUUID();
		BagEntry saved = new BagEntry(id, new BigDecimal("500"), new BigDecimal("500"), 0L, BigDecimal.ZERO,
		                              BigDecimal.ZERO);
		when(repository.loadAll()).thenReturn(List.of(saved));

		HotCashBags bags = config.hotCashBags(repository);

		verify(repository).setDataSupplier(any());
		assertEquals(0, new BigDecimal("500").compareTo(bags.amount(id)));
	}

	@Test
	@DisplayName("settingsOf re-reads the loader on every get")
	void settingsOf_followsTheLoader() {
		ChaseConfigLoader loader = mock(ChaseConfigLoader.class);
		BagSettings       custom = new BagSettings(false, 0.5, 1, 2, 3, "small", false, 10);
		when(loader.getBag()).thenReturn(BagSettings.DEFAULT, custom);

		Supplier<BagSettings> settings = BagModuleConfig.settingsOf(loader);

		assertSame(BagSettings.DEFAULT, settings.get());
		assertSame(custom, settings.get());
	}

	@Test
	@DisplayName("every bean is built from its collaborators")
	@SuppressWarnings("unchecked")
	void beans_areBuilt() {
		JavaPlugin        plugin = mock(JavaPlugin.class);
		ChaseConfigLoader loader = mock(ChaseConfigLoader.class);
		when(loader.getBag()).thenReturn(BagSettings.DEFAULT);
		BagSpill spill = config.bagSpill(plugin, mock(MoneyAddon.class), mock(MoneyDepositService.class), loader);
		BagEnv env = config.bagEnv(plugin, mock(UserManager.class), mock(DetainmentService.class),
		                           mock(WantedStars.class), spill, loader, mock(WantedMessages.class));
		BagService built = config.bagService(new HotCashBags(null), loader, mock(WantedMessages.class), env);

		assertNotNull(spill);
		assertNotNull(env);
		assertNotNull(built);
		assertNotNull(config.bagHud(plugin, built, loader));
	}
}
