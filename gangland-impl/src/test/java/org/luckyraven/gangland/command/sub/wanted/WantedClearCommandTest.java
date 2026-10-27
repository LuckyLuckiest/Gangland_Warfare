package org.luckyraven.gangland.command.sub.wanted;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.core.events.wanted.WantedEndEvent;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.core.wanted.Wanted;
import org.luckyraven.gangland.core.wanted.WantedCause;
import org.luckyraven.gangland.core.wanted.WantedSettings;
import org.luckyraven.gangland.core.wanted.WantedStars;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.support.FakeMessageProvider;
import org.luckyraven.gangland.support.SettingsFixture;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.testkit.BukkitStatics;
import org.mockito.ArgumentCaptor;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code /glw wanted clear} (0.21.1): at zero stars the level does not change and {@link Wanted#setLevel} fires no end,
 * so a post-escape search kept hunting a cleared player and handed the chase off seconds later. The clear fires the
 * end itself then, once; a wanted player's end still comes from the level change alone.
 */
@DisplayName("/glw wanted clear - ends what still hunts a zero-star player")
class WantedClearCommandTest {

	@TempDir
	Path dir;

	private BukkitStatics       bukkit;
	private JavaPlugin          plugin;
	private UserManager<Player> userManager;
	private Player              player;
	private Wanted              wanted;
	private PluginManager       events;

	@SuppressWarnings("unchecked")
	@BeforeEach
	void setUp() {
		bukkit = BukkitStatics.install();
		SettingsFixture.initializeMinimal(dir);
		Messages.init(new FakeMessageProvider().withString("Wanted_Level.Cleared", "CLEARED"));
		events = mock(PluginManager.class);
		bukkit.statics().when(Bukkit::getPluginManager).thenReturn(events);
		bukkit.statics().when(Bukkit::isPrimaryThread).thenReturn(true);

		plugin      = mock(JavaPlugin.class);
		userManager = mock(UserManager.class);
		player      = mock(Player.class);
		wanted      = new Wanted(plugin, 1, 5);
		wanted.setOwner(player);

		User<Player> user = mock(User.class);
		when(user.getWanted()).thenReturn(wanted);
		when(userManager.getUser(player)).thenReturn(user);
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
	}

	@Test
	@DisplayName("at zero stars (the post-escape search) the clear fires one admin wanted end")
	void clearAtZeroStars_firesOneAdminEnd() {
		clear();

		List<WantedEndEvent> ends = ends();
		assertEquals(1, ends.size(), "one end, so the search and its cops stop");
		assertEquals(WantedCause.ADMIN, ends.get(0).getCause());
	}

	@Test
	@DisplayName("a wanted player's clear ends through the level change alone, never twice")
	void clearWhileWanted_endsOnce() {
		wanted.setLevel(3);

		clear();

		assertEquals(0, wanted.getLevel());
		assertEquals(1, ends().size());
	}

	private void clear() {
		Tree<Argument> tree   = new Tree<>();
		Argument       parent = new Argument(plugin, "wanted", tree);
		tree.add(parent.getNode());
		WantedStars stars = new WantedStars(plugin, mock(WantedSettings.class));

		new WantedClearCommand(plugin, tree, parent, userManager, stars).executeArgument(player,
		                                                                                 new String[]{"wanted", "clear"});
	}

	private List<WantedEndEvent> ends() {
		ArgumentCaptor<Event> fired = ArgumentCaptor.forClass(Event.class);
		verify(events, org.mockito.Mockito.atLeast(0)).callEvent(fired.capture());
		return fired.getAllValues().stream().filter(WantedEndEvent.class::isInstance).map(WantedEndEvent.class::cast)
		            .toList();
	}
}
