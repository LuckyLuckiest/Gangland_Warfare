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
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopTierConfig;
import org.luckyraven.gangland.copsncrooks.npc.police.config.DisguiseSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.config.WarningSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.patrol.PatrolManager;
import org.luckyraven.gangland.copsncrooks.npc.police.perception.CopPerception;
import org.luckyraven.gangland.copsncrooks.npc.police.radio.CopRadio;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CopState;
import org.luckyraven.gangland.copsncrooks.wanted.disguise.DisguiseFilter;
import org.luckyraven.gangland.copsncrooks.wanted.disguise.DisguiseTick;
import org.luckyraven.gangland.copsncrooks.wanted.disguise.SuspectDescriptions;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.core.wanted.Wanted;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.PostConstruct;
import org.luckyraven.keystone.bean.autowire.DependencyContainer;
import org.luckyraven.keystone.npc.NpcDifficulty;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
	@DisplayName("difficultyOf: the tier for his stars; level 0 asks tier 1; NORMAL for a null provider or tier")
	void difficultyFollowsTheTierForHisStars() {
		CopLoader loader = mock(CopLoader.class);
		CopConfigProvider provider = mock(CopConfigProvider.class);
		CopTierConfig deadly = mock(CopTierConfig.class);
		CopTierConfig easy   = mock(CopTierConfig.class);
		CopTierConfig bare   = mock(CopTierConfig.class);
		when(deadly.difficulty()).thenReturn(NpcDifficulty.DEADLY);
		when(easy.difficulty()).thenReturn(NpcDifficulty.EASY);
		when(provider.getTierConfig(5)).thenReturn(deadly);
		when(provider.getTierConfig(1)).thenReturn(easy);
		when(provider.getTierConfig(3)).thenReturn(bare);
		Player player = mock(Player.class);
		int[] level = {5};
		var difficulty = DisguiseModuleConfig.difficultyOf(loader, p -> level[0]);

		when(loader.getLoadedProvider()).thenReturn(null);
		assertEquals(NpcDifficulty.NORMAL, difficulty.apply(player), "config not loaded");

		when(loader.getLoadedProvider()).thenReturn(provider);
		assertEquals(NpcDifficulty.DEADLY, difficulty.apply(player), "5 stars");

		level[0] = 0;
		assertEquals(NpcDifficulty.EASY, difficulty.apply(player), "a post-escape search reads tier 1");
		verify(provider, never()).getTierConfig(0);

		level[0] = 3;
		assertEquals(NpcDifficulty.NORMAL, difficulty.apply(player), "a tier with no difficulty");

		level[0] = 2;
		assertEquals(NpcDifficulty.NORMAL, difficulty.apply(player), "no such tier");
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
		UUID id = UUID.randomUUID();
		when(tick.extras(id)).thenReturn(Map.of("suspect", "x"));

		config.installDisguise();

		ArgumentCaptor<Function<UUID, Map<String, String>>> captor = ArgumentCaptor.forClass(Function.class);
		verify(radio).setExtraPlaceholders(captor.capture());
		assertEquals(Map.of("suspect", "x"), captor.getValue().apply(id));
	}

	@Test
	@DisplayName("huntedOf: hunted while the player has stars or the cops still search for them")
	@SuppressWarnings("unchecked")
	void huntedFollowsStarsAndTheSearch() {
		UserManager<Player> users = mock(UserManager.class);
		Player player = mock(Player.class);
		UUID id = UUID.randomUUID();
		when(player.getUniqueId()).thenReturn(id);
		User<Player> user = mock(User.class);
		Wanted wanted = mock(Wanted.class);
		when(users.getUser(player)).thenReturn(user);
		when(user.getWanted()).thenReturn(wanted);
		var hunted = DisguiseModuleConfig.huntedOf(users, manager);

		when(wanted.getLevel()).thenReturn(0);
		when(manager.isSearching(id)).thenReturn(false);
		assertFalse(hunted.test(player), "0 stars, no search");

		when(manager.isSearching(id)).thenReturn(true);
		assertTrue(hunted.test(player), "0 stars, searched for");

		when(manager.isSearching(id)).thenReturn(false);
		when(wanted.getLevel()).thenReturn(2);
		assertTrue(hunted.test(player), "2 stars");
	}

	/** A live cop at {@code distance} from {@code player}, hunting {@code target} (may be null) in {@code state}. */
	private static CopNpc cop(Player player, UUID target, CopState state, double distance) {
		CopNpc cop = mock(CopNpc.class);
		when(cop.isValid()).thenReturn(true);
		when(cop.getTargetPlayerId()).thenReturn(target);
		when(cop.getCurrentState()).thenReturn(state);
		when(cop.distanceTo(player)).thenReturn(distance);
		return cop;
	}

	private static Player playerWithId() {
		Player player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(UUID.randomUUID());
		return player;
	}

	private static CopConfigProvider providerWithRanges(double alert, double warning) {
		CopConfigProvider provider = mock(CopConfigProvider.class);
		when(provider.getAlertRange()).thenReturn(alert);
		when(provider.getWarningSettings()).thenReturn(new WarningSettings(true, warning, 5, 30));
		return provider;
	}

	private static CopLoader loaderWithRanges(double alert, double warning) {
		CopConfigProvider provider = providerWithRanges(alert, warning);
		CopLoader         loader   = mock(CopLoader.class);
		when(loader.getLoadedProvider()).thenReturn(provider);
		return loader;
	}

	@Test
	@DisplayName("860 eyesOf: a cop hunting him with a clear ray counts, in any group; false before the config loads")
	void eyesCountAHuntingCop() {
		CopConfigProvider provider = providerWithRanges(40.0, 12.0);
		CopLoader loader = mock(CopLoader.class);
		Player player = playerWithId();
		UUID id = player.getUniqueId();
		CopNpc gone  = cop(player, id, CopState.PURSUING, 5.0);
		CopNpc valid = cop(player, id, CopState.PURSUING, 5.0);
		when(gone.isValid()).thenReturn(false);
		when(gone.canSee(player, 40.0)).thenReturn(true);
		var eyes = DisguiseModuleConfig.eyesOf(perception, loader, mock(SuspectDescriptions.class),
		                                       () -> List.of(gone, valid));

		when(loader.getLoadedProvider()).thenReturn(null);
		when(valid.canSee(player, 40.0)).thenReturn(true);
		assertFalse(eyes.test(player), "config not loaded");

		when(loader.getLoadedProvider()).thenReturn(provider);
		assertTrue(eyes.test(player), "the valid cop sees him, whatever the group");

		when(valid.canSee(player, 40.0)).thenReturn(false);
		assertFalse(eyes.test(player), "only an invalid cop sees him");
	}

	@Test
	@DisplayName("859 eyesOf: while recognised a hunting cop has the bare ray; the sight factors only stop a first pick-up")
	void eyesFollowTheBareRayWhileRecognised() {
		CopLoader loader = loaderWithRanges(40.0, 12.0);
		CopPerception dim = new CopPerception();
		dim.addSightFactor((cop, target) -> 0.25);
		Player player = playerWithId();
		UUID id = player.getUniqueId();
		CopNpc cop = cop(player, id, CopState.PURSUING, 20.0);
		when(cop.canSee(player, 40.0)).thenReturn(true);
		when(cop.canSee(player, 10.0)).thenReturn(false);
		SuspectDescriptions descriptions = mock(SuspectDescriptions.class);
		var eyes = DisguiseModuleConfig.eyesOf(dim, loader, descriptions, () -> List.of(cop));

		when(descriptions.isRecognised(id)).thenReturn(false);
		assertFalse(eyes.test(player), "not recognised: the factor shrinks 40 to 10, the ray stops short");

		when(descriptions.isRecognised(id)).thenReturn(true);
		assertTrue(eyes.test(player), "recognised: the bare ray to the alert range");
	}

	@Test
	@DisplayName("860 eyesOf: a cop not hunting him notices as a patrol does: warning range, sight factors on")
	void anotherCopNoticesAsAPatrolDoes() {
		CopLoader loader = loaderWithRanges(40.0, 12.0);
		double[] factor = {1.0};
		CopPerception patrol = new CopPerception();
		patrol.addSightFactor((cop, target) -> factor[0]);
		Player player = playerWithId();
		CopNpc beat = cop(player, null, CopState.PATROL, 8.0);
		when(beat.canSee(player, 12.0)).thenReturn(true);
		when(beat.canSee(player, 40.0)).thenReturn(true);
		when(beat.canSee(player, 3.0)).thenReturn(false);
		SuspectDescriptions descriptions = mock(SuspectDescriptions.class);
		when(descriptions.isRecognised(player.getUniqueId())).thenReturn(true);
		var eyes = DisguiseModuleConfig.eyesOf(patrol, loader, descriptions, () -> List.of(beat));

		assertTrue(eyes.test(player), "factor 1, a clear ray inside the warning range");

		factor[0] = 0.25;
		assertFalse(eyes.test(player), "recognised or not, a cop not hunting him has no bare ray");

		factor[0] = 1.0;
		CopNpc far = cop(player, null, CopState.PATROL, 30.0);
		when(far.canSee(player, 12.0)).thenReturn(true);
		var farEyes = DisguiseModuleConfig.eyesOf(patrol, loader, descriptions, () -> List.of(far));
		assertFalse(farEyes.test(player), "30 blocks is past the warning range");
		verify(far, never()).canSee(any(), anyDouble());
	}

	@Test
	@DisplayName("860 eyesOf: a cop walking home never counts")
	void aCopWalkingHomeNeverCounts() {
		CopLoader loader = loaderWithRanges(40.0, 12.0);
		Player player = playerWithId();
		UUID id = player.getUniqueId();
		CopNpc cop = cop(player, id, CopState.PURSUING, 5.0);
		when(cop.canSee(player, 40.0)).thenReturn(true);
		var eyes = DisguiseModuleConfig.eyesOf(perception, loader, mock(SuspectDescriptions.class), () -> List.of(cop));

		assertTrue(eyes.test(player), "pursuing: counts");

		when(cop.getCurrentState()).thenReturn(CopState.RETURNING);
		assertFalse(eyes.test(player), "returning: does not");
	}

	@Test
	@DisplayName("eyesOf: a cop out of range costs no sight lookup (the factors run only for cops in range)")
	void aCopOutOfRangeCostsNoSightLookup() {
		CopLoader loader = loaderWithRanges(40.0, 12.0);
		CopPerception counting = new CopPerception();
		CopPerception.SightFactor factor = mock(CopPerception.SightFactor.class);
		counting.addSightFactor(factor);
		Player player = playerWithId();
		CopNpc cop = cop(player, player.getUniqueId(), CopState.PURSUING, 41.0);
		var eyes = DisguiseModuleConfig.eyesOf(counting, loader, mock(SuspectDescriptions.class), () -> List.of(cop));

		assertFalse(eyes.test(player));
		verifyNoInteractions(factor);
	}

	@Test
	@DisplayName("860 witnessesOf: squad cops and beat cops")
	void witnessesAreSquadAndBeatCops() {
		CopNpc squadCop = mock(CopNpc.class);
		CopNpc beatCop  = mock(CopNpc.class);
		when(manager.liveCops()).thenReturn(List.of(squadCop));
		PatrolManager patrol = mock(PatrolManager.class);
		when(patrol.beatCops()).thenReturn(List.of(beatCop));

		assertEquals(List.of(squadCop, beatCop), DisguiseModuleConfig.witnessesOf(manager, patrol).get());
	}
}
