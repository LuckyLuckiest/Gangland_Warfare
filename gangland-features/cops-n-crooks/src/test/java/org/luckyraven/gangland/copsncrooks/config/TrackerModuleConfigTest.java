package org.luckyraven.gangland.copsncrooks.config;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopConfigProvider;
import org.luckyraven.gangland.copsncrooks.npc.police.config.TrackerSettings;
import org.luckyraven.gangland.core.wanted.Wanted;
import org.luckyraven.gangland.copsncrooks.npc.police.CopGroup;
import org.luckyraven.gangland.copsncrooks.npc.police.CopManager;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopLoader;
import org.luckyraven.gangland.copsncrooks.npc.police.radio.CopRadio;
import org.luckyraven.gangland.copsncrooks.wanted.WantedMessages;
import org.luckyraven.gangland.copsncrooks.wanted.evasion.TrackerTags;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.keystone.bean.PostConstruct;
import org.luckyraven.keystone.bean.autowire.DependencyContainer;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** {@link TrackerModuleConfig}: the tag book bean and its install into the cop AI tick. */
@DisplayName("TrackerModuleConfig")
class TrackerModuleConfigTest {

	@Test
	@DisplayName("trackerTags builds from the loader, the user manager, the radio and the plugin")
	@SuppressWarnings("unchecked")
	void trackerTags_isBuilt() {
		JavaPlugin plugin = mock(JavaPlugin.class);
		when(plugin.getName()).thenReturn("Gangland_Warfare");
		TrackerModuleConfig config = new TrackerModuleConfig(plugin, new DependencyContainer());

		assertNotNull(config.trackerTags(mock(CopLoader.class), mock(UserManager.class), mock(CopRadio.class),
		                                 mock(WantedMessages.class), plugin));
	}

	@Test
	@DisplayName("installTracker registers exactly one AI tick hook, and it feeds TrackerTags.tick")
	@SuppressWarnings("unchecked")
	void installTracker_registersTickHook() {
		DependencyContainer container = new DependencyContainer();
		TrackerTags         tags      = mock(TrackerTags.class);
		CopManager          manager   = mock(CopManager.class);
		container.registerInstance(TrackerTags.class, tags);
		container.registerInstance(CopManager.class, manager);

		new TrackerModuleConfig(mock(JavaPlugin.class), container).installTracker();

		ArgumentCaptor<BiConsumer<Player, CopGroup>> hook = ArgumentCaptor.forClass(BiConsumer.class);
		verify(manager, times(1)).addAiTickHook(hook.capture());
		Player   player = mock(Player.class);
		CopGroup group  = mock(CopGroup.class);
		hook.getValue().accept(player, group);
		verify(tags).tick(player, group);
	}

	@Test
	@DisplayName("the bean container runs it: installTracker is a @PostConstruct")
	void installTracker_isAPostConstruct() throws NoSuchMethodException {
		assertTrue(TrackerModuleConfig.class.getMethod("installTracker").isAnnotationPresent(PostConstruct.class));
	}

	@Test
	@DisplayName("settingsOf reads the loaded provider on every get: DEFAULT while none is loaded, then the live tracker settings")
	void settingsOf_followsTheProvider() {
		CopLoader         loader   = mock(CopLoader.class);
		CopConfigProvider provider = mock(CopConfigProvider.class);
		TrackerSettings   custom   = new TrackerSettings(true, 3, 5, 40.0, 7, 99);
		when(loader.getLoadedProvider()).thenReturn(null, provider);
		when(provider.getTrackerSettings()).thenReturn(custom);

		Supplier<TrackerSettings> settings = TrackerModuleConfig.settingsOf(loader);

		assertSame(TrackerSettings.DEFAULT, settings.get());
		assertSame(custom, settings.get());
		verify(loader, times(2)).getLoadedProvider();
	}

	@Test
	@DisplayName("settingsOf: a provider without tracker settings gives DEFAULT")
	void settingsOf_nullSettingsGiveDefault() {
		CopLoader         loader   = mock(CopLoader.class);
		CopConfigProvider provider = mock(CopConfigProvider.class);
		when(loader.getLoadedProvider()).thenReturn(provider);
		when(provider.getTrackerSettings()).thenReturn(null);

		assertSame(TrackerSettings.DEFAULT, TrackerModuleConfig.settingsOf(loader).get());
	}

	@Test
	@DisplayName("levelOf: 0 for a player with no user, else the Wanted level")
	@SuppressWarnings("unchecked")
	void levelOf_readsTheWantedLevel() {
		UserManager<Player> users  = mock(UserManager.class);
		Player              known  = mock(Player.class);
		Player              absent = mock(Player.class);
		User<Player>        user   = mock(User.class);
		Wanted              wanted = mock(Wanted.class);
		when(users.getUser(known)).thenReturn(user);
		when(user.getWanted()).thenReturn(wanted);
		when(wanted.getLevel()).thenReturn(3);

		ToIntFunction<Player> level = TrackerModuleConfig.levelOf(users);

		assertEquals(0, level.applyAsInt(absent));
		assertEquals(3, level.applyAsInt(known));
	}

	@Test
	@DisplayName("the PDC key is copsncrooks:tracker")
	void key_isCopsOwned() {
		assertEquals("copsncrooks:tracker", TrackerModuleConfig.KEY.toString());
	}

	@Test
	@DisplayName("the notifier tells the tagged player with the Tracker.Tagged line")
	void notifier_sendsTheTaggedLine() {
		JavaPlugin     plugin   = mock(JavaPlugin.class);
		WantedMessages messages = mock(WantedMessages.class);
		Player         player   = mock(Player.class);
		when(messages.format(eq(WantedMessages.Key.TRACKER_TAGGED), anyMap())).thenReturn("tagged-line");

		TrackerModuleConfig.notifier(plugin, messages).tagged(player);

		verify(player).sendMessage("tagged-line");
	}
}
